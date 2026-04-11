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


class WSManager:
    """
    Manages WebSocket connections and Redis Pub/Sub for real-time messaging.

    Each backend instance holds its own set of connections.
    Redis Pub/Sub ensures messages are delivered across all instances.
    """

    def __init__(self):
        # user_id -> set of WebSocket connections (one user can have multiple devices)
        self._connections: dict[uuid.UUID, set[WebSocket]] = defaultdict(set)
        # chat_id -> set of user_ids currently connected
        self._chat_users: dict[uuid.UUID, set[uuid.UUID]] = defaultdict(set)
        # user_id -> set of chat_ids
        self._user_chats: dict[uuid.UUID, set[uuid.UUID]] = defaultdict(set)
        # Redis pubsub listener task
        self._redis: aioredis.Redis | None = None
        self._pubsub_task: asyncio.Task | None = None
        self._pubsub = None

    async def _get_redis(self) -> aioredis.Redis:
        if self._redis is None:
            self._redis = aioredis.from_url(settings.redis_url, decode_responses=True)
        return self._redis

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

        # Subscribe to new channels if needed
        await self._ensure_subscriptions(chat_ids)

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
                # Remove user from chat_users
                for chat_id in self._user_chats.get(user_id, set()):
                    self._chat_users[chat_id].discard(user_id)
                    if not self._chat_users[chat_id]:
                        del self._chat_users[chat_id]
                self._user_chats.pop(user_id, None)

    async def _ensure_subscriptions(self, chat_ids: list[uuid.UUID]):
        """Ensure Redis pubsub is listening on all needed channels."""
        redis = await self._get_redis()

        if self._pubsub is None:
            self._pubsub = redis.pubsub()
            self._pubsub_task = asyncio.create_task(self._listen())

        channels = [f"{CHANNEL_PREFIX}{chat_id}" for chat_id in chat_ids]
        if channels:
            await self._pubsub.subscribe(*channels)

    async def _listen(self):
        """Background task that listens to Redis pubsub and dispatches to local WS connections."""
        try:
            while True:
                if self._pubsub is None:
                    await asyncio.sleep(0.1)
                    continue

                message = await self._pubsub.get_message(
                    ignore_subscribe_messages=True, timeout=0.1
                )
                if message is None:
                    await asyncio.sleep(0.05)
                    continue

                if message["type"] == "message":
                    try:
                        data = json.loads(message["data"])
                        channel = message["channel"]
                        # Extract chat_id from channel name "chat:<uuid>"
                        chat_id_str = channel.replace(CHANNEL_PREFIX, "")
                        chat_id = uuid.UUID(chat_id_str)
                        exclude_user_id = None
                        if data.get("_exclude_user_id"):
                            exclude_user_id = uuid.UUID(data.pop("_exclude_user_id"))

                        await self._broadcast_to_chat(chat_id, data, exclude_user_id)
                    except Exception as e:
                        logger.error("ws_pubsub_dispatch_error", error=str(e))

        except asyncio.CancelledError:
            pass
        except Exception as e:
            logger.error("ws_pubsub_listener_error", error=str(e))

    async def _broadcast_to_chat(
        self,
        chat_id: uuid.UUID,
        data: dict,
        exclude_user_id: uuid.UUID | None = None,
    ):
        """Send data to all local WS connections in a chat."""
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

        # Clean up dead connections
        for user_id, ws in dead_connections:
            await self.disconnect(user_id, ws)

    async def publish_event(
        self,
        chat_id: str | uuid.UUID,
        event: dict,
        exclude_user_id: uuid.UUID | None = None,
    ):
        """Publish an event to Redis so all instances receive it."""
        redis = await self._get_redis()
        if exclude_user_id:
            event["_exclude_user_id"] = str(exclude_user_id)
        channel = f"{CHANNEL_PREFIX}{chat_id}"
        await redis.publish(channel, json.dumps(event, default=str))

    async def publish_new_message(
        self,
        chat_id: uuid.UUID,
        message_data: dict,
        sender_id: uuid.UUID,
    ):
        """Publish a new_message event."""
        event = {
            "type": "new_message",
            **message_data,
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
        if self._redis:
            await self._redis.close()


# Singleton
ws_manager = WSManager()