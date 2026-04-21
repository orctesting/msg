import uuid
import json
import asyncio

from fastapi import APIRouter, WebSocket, WebSocketDisconnect, Query
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

import structlog

from app.core.security import decode_token
from app.db.session import async_session_factory
from app.db.models.user import User
from app.db.models.chat_member import ChatMember
from app.services.ws_manager import ws_manager

logger = structlog.get_logger()
router = APIRouter()


async def _authenticate_ws(token: str) -> User | None:
    """Validate JWT and return User or None."""
    payload = decode_token(token)
    if payload is None or payload.get("type") != "access":
        return None

    user_id = payload.get("sub")
    if user_id is None:
        return None

    async with async_session_factory() as session:
        result = await session.execute(
            select(User).where(User.id == uuid.UUID(user_id), User.is_active == True)
        )
        return result.scalar_one_or_none()


async def _get_user_chat_ids(user_id: uuid.UUID) -> list[uuid.UUID]:
    """Get all chat IDs the user is a member of."""
    async with async_session_factory() as session:
        result = await session.execute(
            select(ChatMember.chat_id).where(ChatMember.user_id == user_id)
        )
        return [row[0] for row in result.all()]


@router.websocket("/ws")
async def websocket_endpoint(
    websocket: WebSocket,
    token: str = Query(...),
):
    user = await _authenticate_ws(token)
    if user is None:
        await websocket.close(code=4001, reason="Unauthorized")
        return

    await websocket.accept()
    logger.info("ws_connected", user_id=str(user.id))

    chat_ids = await _get_user_chat_ids(user.id)

    # Register connection
    await ws_manager.connect(user.id, websocket, chat_ids)

    try:
        while True:
            data = await websocket.receive_text()
            # Client can send ping/pong or typing indicators
            try:
                msg = json.loads(data)
                msg_type = msg.get("type")

                if msg_type == "ping":
                    await websocket.send_json({"type": "pong"})

                elif msg_type == "typing":
                    chat_id = msg.get("chat_id")
                    if chat_id:
                        await ws_manager.publish_event(
                            chat_id=chat_id,
                            event={
                                "type": "typing",
                                "chat_id": chat_id,
                                "user_id": str(user.id),
                                "display_name": user.display_name,
                            },
                            exclude_user_id=user.id,
                        )

            except json.JSONDecodeError:
                pass

    except WebSocketDisconnect:
        logger.info("ws_disconnected", user_id=str(user.id))
    except Exception as e:
        logger.error("ws_error", user_id=str(user.id), error=str(e))
    finally:
        await ws_manager.disconnect(user.id, websocket)