import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message, MessageRead
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ForbiddenException
from app.api.schemas.message import (
    MessageSendIn,
    MessageOut,
    MessageListOut,
    MessageReadIn,
)
from app.services.ws_manager import ws_manager
from app.utils.idempotency import acquire_message_idempotency

logger = structlog.get_logger()
router = APIRouter(prefix="/chats", tags=["messages"])


async def _check_membership(
    session: AsyncSession,
    chat_id: uuid.UUID,
    user_id: uuid.UUID,
) -> ChatMember:
    result = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == user_id,
        )
    )
    member = result.scalar_one_or_none()
    if member is None:
        raise ForbiddenException("You are not a member of this chat")
    return member


@router.get("/{chat_id}/messages", response_model=MessageListOut)
async def get_messages(
    chat_id: uuid.UUID,
    before: uuid.UUID | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    query = select(Message).where(Message.chat_id == chat_id)

    if before:
        cursor_result = await session.execute(
            select(Message.created_at).where(Message.id == before)
        )
        cursor_ts = cursor_result.scalar_one_or_none()
        if cursor_ts:
            query = query.where(Message.created_at < cursor_ts)

    query = query.order_by(desc(Message.created_at)).limit(limit + 1)
    result = await session.execute(query)
    messages = list(result.scalars().all())

    has_more = len(messages) > limit
    if has_more:
        messages = messages[:limit]

    messages.reverse()

    return MessageListOut(
        messages=[MessageOut.model_validate(m) for m in messages],
        has_more=has_more,
    )


@router.post("/{chat_id}/messages", response_model=MessageOut, status_code=201)
async def send_message(
    chat_id: uuid.UUID,
    body: MessageSendIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    chat_result = await session.execute(select(Chat).where(Chat.id == chat_id))
    if chat_result.scalar_one_or_none() is None:
        raise NotFoundException("Chat not found")

    if body.idempotency_key:
        existing_result = await session.execute(
            select(Message).where(Message.idempotency_key == body.idempotency_key)
        )
        existing_message = existing_result.scalar_one_or_none()
        if existing_message is not None:
            return MessageOut.model_validate(existing_message)

        acquired = await acquire_message_idempotency(str(body.idempotency_key))
        if not acquired:
            existing_result = await session.execute(
                select(Message).where(Message.idempotency_key == body.idempotency_key)
            )
            existing_message = existing_result.scalar_one_or_none()
            if existing_message is not None:
                return MessageOut.model_validate(existing_message)

    message = Message(
        chat_id=chat_id,
        sender_id=current_user.id,
        content=body.content,
        message_type=body.message_type,
        idempotency_key=body.idempotency_key,
    )
    session.add(message)
    await session.commit()
    await session.refresh(message)

    message_out = MessageOut.model_validate(message)

    logger.info(
        "message_sent",
        message_id=str(message.id),
        chat_id=str(chat_id),
        sender_id=str(current_user.id),
    )

    try:
        await ws_manager.publish_new_message(
            chat_id=chat_id,
            message_data=message_out.model_dump(mode="json"),
            sender_id=current_user.id,
        )
    except Exception as e:
        logger.error("ws_publish_error", error=str(e))

    try:
        members_result = await session.execute(
            select(ChatMember.user_id).where(
                ChatMember.chat_id == chat_id,
                ChatMember.user_id != current_user.id,
            )
        )
        recipient_ids = [str(row[0]) for row in members_result.all()]

        if recipient_ids:
            from app.workers.tasks.push import send_push_notification

            send_push_notification.delay(str(message.id), recipient_ids)
    except Exception as e:
        logger.error("push_enqueue_error", error=str(e))

    return message_out


@router.post("/{chat_id}/read", status_code=204)
async def mark_messages_read(
    chat_id: uuid.UUID,
    body: MessageReadIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    # Verify target message exists in this chat
    target_result = await session.execute(
        select(Message).where(
            Message.id == body.last_read_message_id,
            Message.chat_id == chat_id,
        )
    )
    target_message = target_result.scalar_one_or_none()
    if target_message is None:
        raise NotFoundException("Message not found in this chat")

    # Get all unread message IDs in one query (messages up to target that have no read record)
    from sqlalchemy import and_, exists as sa_exists

    already_read_subq = (
        select(MessageRead.message_id)
        .where(
            MessageRead.user_id == current_user.id,
        )
        .correlate(Message)
    )

    unread_messages_result = await session.execute(
        select(Message.id).where(
            Message.chat_id == chat_id,
            Message.created_at <= target_message.created_at,
            ~Message.id.in_(
                select(MessageRead.message_id).where(
                    MessageRead.user_id == current_user.id,
                )
            ),
        )
    )
    unread_message_ids = [row[0] for row in unread_messages_result.all()]

    # Bulk insert read records
    for msg_id in unread_message_ids:
        session.add(
            MessageRead(
                message_id=msg_id,
                user_id=current_user.id,
            )
        )

    await session.commit()

    try:
        await ws_manager.publish_event(
            chat_id=chat_id,
            event={
                "type": "message_read",
                "data": {
                    "chat_id": str(chat_id),
                    "user_id": str(current_user.id),
                    "last_read_message_id": str(body.last_read_message_id),
                },
            },
            exclude_user_id=current_user.id,
        )
    except Exception as e:
        logger.error("ws_read_receipt_error", error=str(e))