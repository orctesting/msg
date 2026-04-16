import uuid

from fastapi import APIRouter, Depends, Header
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.config import settings
from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message
from app.db.models.device import Device
from app.db.models.push_log import PushLog
from app.core.dependencies import get_current_user_optional, require_admin
from app.core.exceptions import NotFoundException, ForbiddenException
from app.api.schemas.push import (
    PushBroadcastIn,
    PushBroadcastOut,
    PushStatusOut,
    PushStatusItemOut,
)
from app.services.ws_manager import ws_manager

logger = structlog.get_logger()
router = APIRouter(prefix="/push", tags=["push"])


@router.post("/broadcast", response_model=PushBroadcastOut, status_code=202)
async def broadcast_push(
    body: PushBroadcastIn,
    x_api_key: str | None = Header(default=None, alias="X-API-Key"),
    current_user: User | None = Depends(get_current_user_optional),
    session: AsyncSession = Depends(get_async_session),
):
    internal_api_key = getattr(settings, "internal_api_key", None)

    is_internal_call = bool(
        x_api_key and internal_api_key and x_api_key == internal_api_key
    )
    is_admin_call = current_user is not None and current_user.role == "admin"

    if not is_internal_call and not is_admin_call:
        raise ForbiddenException("Admin or internal API access required")

    chat_result = await session.execute(select(Chat).where(Chat.id == body.chat_id))
    chat = chat_result.scalar_one_or_none()
    if chat is None:
        raise NotFoundException("Chat not found")

    existing_result = await session.execute(
        select(Message).where(Message.idempotency_key == str(body.idempotency_key))
    )
    existing_message = existing_result.scalar_one_or_none()
    if existing_message is not None:
        members_count_result = await session.execute(
            select(func.count(ChatMember.id)).where(ChatMember.chat_id == body.chat_id)
        )
        return PushBroadcastOut(
            message_id=existing_message.id,
            push_task_id=None,
            recipients_count=members_count_result.scalar() or 0,
        )

    message = Message(
        chat_id=body.chat_id,
        sender_id=None,
        content=body.content,
        message_type=body.message_type,
        idempotency_key=str(body.idempotency_key),
    )
    session.add(message)
    await session.flush()

    members_result = await session.execute(
        select(ChatMember.user_id).where(ChatMember.chat_id == body.chat_id)
    )
    recipient_ids = [row[0] for row in members_result.all()]

    await session.commit()
    await session.refresh(message)

    # message.sender_id = None, значит это системное
    try:
        await ws_manager.publish_event(
            chat_id=body.chat_id,
            event={
                "type": "new_message",
                "data": {
                    "chat_id": str(body.chat_id),
                    "message": {
                        "id": str(message.id),
                        "chat_id": str(message.chat_id),
                        "sender_id": None,
                        "sender_name": "Admin",
                        "sender_role": "admin",
                        "content": message.content,
                        "message_type": message.message_type,
                        "created_at": message.created_at.isoformat(),
                    },
                },
            },
        )
    except Exception as e:
        logger.error("push_broadcast_ws_publish_error", error=str(e))

    task_id = None
    if body.send_push and recipient_ids:
        try:
            from app.workers.tasks.push import send_push_notification

            task = send_push_notification.delay(
                str(message.id),
                [str(user_id) for user_id in recipient_ids],
            )
            task_id = task.id
        except Exception as e:
            logger.error("push_broadcast_enqueue_error", error=str(e))

    return PushBroadcastOut(
        message_id=message.id,
        push_task_id=task_id,
        recipients_count=len(recipient_ids),
    )


@router.get("/status/{message_id}", response_model=PushStatusOut)
async def get_push_status(
    message_id: uuid.UUID,
    _: User = Depends(require_admin),
    session: AsyncSession = Depends(get_async_session),
):
    message_result = await session.execute(
        select(Message).where(Message.id == message_id)
    )
    message = message_result.scalar_one_or_none()
    if message is None:
        raise NotFoundException("Message not found")

    recipients_count_result = await session.execute(
        select(func.count(ChatMember.id)).where(ChatMember.chat_id == message.chat_id)
    )
    total_recipients = recipients_count_result.scalar() or 0

    logs_result = await session.execute(
        select(PushLog, Device, User)
        .join(Device, Device.id == PushLog.device_id)
        .join(User, User.id == Device.user_id)
        .where(PushLog.message_id == message_id)
        .order_by(PushLog.created_at.desc())
    )
    rows = logs_result.all()

    stats = {
        "delivered_ws": 0,
        "sent_to_provider": 0,
        "pending": 0,
        "failed": 0,
        "skipped_inactive": 0,
    }

    details: list[PushStatusItemOut] = []

    for push_log, device, user in rows:
        status = push_log.status

        if status == "delivered_ws":
            stats["delivered_ws"] += 1
        elif status == "sent_to_provider":
            stats["sent_to_provider"] += 1
        elif status == "pending":
            stats["pending"] += 1
        elif status == "skipped_inactive":
            stats["skipped_inactive"] += 1
        elif status.startswith("failed"):
            stats["failed"] += 1

        details.append(
            PushStatusItemOut(
                user_id=user.id,
                device_id=device.id,
                platform=device.platform,
                status=push_log.status,
                provider_message_id=push_log.provider_message_id,
                attempt_number=push_log.attempt_number,
                created_at=push_log.created_at,
            )
        )

    return PushStatusOut(
        message_id=message_id,
        total_recipients=total_recipients,
        delivery_stats=stats,
        details=details,
    )