import uuid
import json
import asyncio
from collections import defaultdict

from fastapi import WebSocket
import redis.asyncio as aioredis
import structlog

from app.config import settings

logger = structlog.get_logger()

CHANNEL_PREFIX = "chat:"
USER_CHANNEL_PREFIX = "user:"


class WSManager:
    def __init__(self):
        self._connections: dict[uuid.UUID, set[WebSocket]] = defaultdict(set)
        self._chat_users: dict[uuid.UUID, set[uuid.UUID]] = defaultdict(set)
        self._user_chats: dict[uuid.UUID, set[uuid.UUID]] = defaultdict(set)
        self._sub_redis: aioredis.Redis | None = None
        self._pub_redis: aioredis.Redis | None = None
        self._pubsub = None
        self._pubsub_task: asyncio.Task | None = None
        self._subscribed_channels: set[str] = set()

    async def _get_sub_redis(self) -> aioredis.Redis:
        if self._sub_redis is None:
            self._sub_redis = aioredis.from_url(settings.redis_url, decode_responses=True)
        return self._sub_redis

    async def _get_pub_redis(self) -> aioredis.Redis:
        if self._pub_redis is None:
            self._pub_redis = aioredis.from_url(settings.redis_url, decode_responses=True)
        return self._pub_redis

    async def connect(
        self,
        user_id: uuid.UUID,
        websocket: WebSocket,
        chat_ids: list[uuid.UUID],
    ):
        self._connections[user_id].add(websocket)
        self._user_chats[user_id] = set(chat_ids)

        for chat_id in chat_ids:
            self._chat_users[chat_id].add(user_id)

        await self._ensure_subscriptions(chat_ids)
        await self._ensure_user_subscription(user_id)

        logger.info(
            "ws_manager_connect",
            user_id=str(user_id),
            chat_count=len(chat_ids),
            total_connections=sum(len(v) for v in self._connections.values()),
        )

    async def disconnect(self, user_id: uuid.UUID, websocket: WebSocket):
        if user_id in self._connections:
            self._connections[user_id].discard(websocket)
            if not self._connections[user_id]:
                del self._connections[user_id]
                for chat_id in self._user_chats.get(user_id, set()):
                    self._chat_users[chat_id].discard(user_id)
                    if not self._chat_users[chat_id]:
                        del self._chat_users[chat_id]
                self._user_chats.pop(user_id, None)

    async def _ensure_subscriptions(self, chat_ids: list[uuid.UUID]):
        redis = await self._get_sub_redis()

        if self._pubsub is None:
            self._pubsub = redis.pubsub()

        # Subscribe to new channels FIRST, before starting listener
        new_channels = []
        for chat_id in chat_ids:
            ch = f"{CHANNEL_PREFIX}{chat_id}"
            if ch not in self._subscribed_channels:
                new_channels.append(ch)
                self._subscribed_channels.add(ch)

        if new_channels:
            await self._pubsub.subscribe(*new_channels)
            print(f"=== SUBSCRIBED to {new_channels} ===", flush=True)

        # Start listener AFTER first subscribe
        if self._pubsub_task is None or self._pubsub_task.done():
            self._pubsub_task = asyncio.create_task(self._listen())
            print("=== PUBSUB LISTENER TASK STARTED ===", flush=True)
                        
    async def _ensure_user_subscription(self, user_id: uuid.UUID):
        redis = await self._get_sub_redis()

        if self._pubsub is None:
            self._pubsub = redis.pubsub()

        ch = f"{USER_CHANNEL_PREFIX}{user_id}"
        if ch not in self._subscribed_channels:
            self._subscribed_channels.add(ch)
            await self._pubsub.subscribe(ch)
            print(f"=== SUBSCRIBED to {ch} ===", flush=True)

        if self._pubsub_task is None or self._pubsub_task.done():
            self._pubsub_task = asyncio.create_task(self._listen())
            print("=== PUBSUB LISTENER TASK STARTED ===", flush=True)
            
    async def _listen(self):
        print("=== WS PUBSUB LISTENER RUNNING ===", flush=True)
        logger.info("ws_pubsub_listener_started")
        try:
            while True:
                if self._pubsub is None:
                    await asyncio.sleep(0.1)
                    continue

                try:
                    message = await self._pubsub.get_message(
                        ignore_subscribe_messages=True, timeout=0.1
                    )
                except Exception as e:
                    logger.error("ws_pubsub_get_message_error", error=str(e))
                    await asyncio.sleep(1)
                    continue

                if message is None:
                    await asyncio.sleep(0.05)
                    continue

                if message["type"] == "message":
                    print(f"=== PUBSUB GOT: {message['channel']} ===", flush=True)
                    try:
                        data = json.loads(message["data"])
                        channel = message["channel"]

                        if channel.startswith(USER_CHANNEL_PREFIX):
                            user_id_str = channel[len(USER_CHANNEL_PREFIX):]
                            target_user_id = uuid.UUID(user_id_str)
                            await self._send_to_user(target_user_id, data)
                        elif channel.startswith(CHANNEL_PREFIX):
                            chat_id_str = channel[len(CHANNEL_PREFIX):]
                            chat_id = uuid.UUID(chat_id_str)
                            exclude_user_id = None
                            if data.get("_exclude_user_id"):
                                exclude_user_id = uuid.UUID(data.pop("_exclude_user_id"))
                            await self._broadcast_to_chat(chat_id, data, exclude_user_id)
                    except Exception as e:
                        logger.error("ws_pubsub_dispatch_error", error=str(e))

        except asyncio.CancelledError:
            logger.info("ws_pubsub_listener_cancelled")
        except Exception as e:
            logger.error("ws_pubsub_listener_error", error=str(e))

    async def _broadcast_to_chat(
        self,
        chat_id: uuid.UUID,
        data: dict,
        exclude_user_id: uuid.UUID | None = None,
    ):
        user_ids = self._chat_users.get(chat_id, set())
        dead_connections = []

        for user_id in user_ids:
            if exclude_user_id and user_id == exclude_user_id:
                continue

            for ws in self._connections.get(user_id, set()):
                try:
                    await ws.send_json(data)
                except Exception:
                    dead_connections.append((user_id, ws))

        for user_id, ws in dead_connections:
            await self.disconnect(user_id, ws)
            
    async def _send_to_user(self, user_id: uuid.UUID, data: dict):
        dead_connections = []
        for ws in self._connections.get(user_id, set()):
            try:
                await ws.send_json(data)
            except Exception:
                dead_connections.append(ws)
        for ws in dead_connections:
            await self.disconnect(user_id, ws)

    async def publish_event(
        self,
        chat_id: str | uuid.UUID,
        event: dict,
        exclude_user_id: uuid.UUID | None = None,
    ):
        redis = await self._get_pub_redis()
        if exclude_user_id:
            event["_exclude_user_id"] = str(exclude_user_id)
        channel = f"{CHANNEL_PREFIX}{chat_id}"
        await redis.publish(channel, json.dumps(event, default=str))
        
    async def publish_to_user(
        self,
        user_id: str | uuid.UUID,
        event: dict,
    ):
        redis = await self._get_pub_redis()
        channel = f"{USER_CHANNEL_PREFIX}{user_id}"
        await redis.publish(channel, json.dumps(event, default=str))

    async def publish_new_message(
        self,
        chat_id: uuid.UUID,
        message_data: dict,
        sender_id: uuid.UUID,
    ):
        event = {
            "type": "new_message",
            "data": {
                "chat_id": str(chat_id),
                "message": message_data,
            },
        }
        await self.publish_event(
            chat_id=chat_id,
            event=event,
            exclude_user_id=sender_id,
        )

    async def shutdown(self):
        if self._pubsub_task:
            self._pubsub_task.cancel()
        if self._pubsub:
            await self._pubsub.unsubscribe()
            await self._pubsub.close()
        if self._sub_redis:
            await self._sub_redis.close()
        if self._pub_redis:
            await self._pub_redis.close()


ws_manager = WSManager()