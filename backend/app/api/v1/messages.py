import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select, desc, func
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import joinedload
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


def _message_to_out(message: Message) -> MessageOut:
    sender = message.sender
    return MessageOut(
        id=message.id,
        chat_id=message.chat_id,
        sender_id=message.sender_id,
        sender_name=sender.display_name if sender else "Admin",
        sender_role=sender.role if sender else "admin",
        content=message.content,
        message_type=message.message_type,
        created_at=message.created_at,
    )


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

    query = (
        select(Message)
        .options(joinedload(Message.sender))
        .where(Message.chat_id == chat_id)
    )

    if before:
        cursor_result = await session.execute(
            select(Message.created_at).where(Message.id == before)
        )
        cursor_ts = cursor_result.scalar_one_or_none()
        if cursor_ts:
            query = query.where(Message.created_at < cursor_ts)

    query = query.order_by(desc(Message.created_at)).limit(limit + 1)
    result = await session.execute(query)
    messages = list(result.scalars().unique().all())

    has_more = len(messages) > limit
    if has_more:
        messages = messages[:limit]

    messages.reverse()

    read_by_others_up_to = None
    latest_read_result = await session.execute(
        select(MessageRead.message_id)
        .join(Message, Message.id == MessageRead.message_id)
        .where(
            Message.chat_id == chat_id,
            Message.sender_id == current_user.id,
            MessageRead.user_id != current_user.id,
        )
        .order_by(desc(Message.created_at))
        .limit(1)
    )
    row = latest_read_result.first()
    if row:
        read_by_others_up_to = row[0]

    return MessageListOut(
        messages=[_message_to_out(m) for m in messages],
        has_more=has_more,
        read_by_others_up_to=read_by_others_up_to,
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
            select(Message)
            .options(joinedload(Message.sender))
            .where(Message.idempotency_key == body.idempotency_key)
        )
        existing_message = existing_result.scalars().unique().first()
        if existing_message is not None:
            return _message_to_out(existing_message)

        acquired = await acquire_message_idempotency(str(body.idempotency_key))
        if not acquired:
            existing_result = await session.execute(
                select(Message)
                .options(joinedload(Message.sender))
                .where(Message.idempotency_key == body.idempotency_key)
            )
            existing_message = existing_result.scalars().unique().first()
            if existing_message is not None:
                return _message_to_out(existing_message)

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

    # Eagerly load sender
    msg_result = await session.execute(
        select(Message)
        .options(joinedload(Message.sender))
        .where(Message.id == message.id)
    )
    message = msg_result.scalars().unique().one()

    message_out = _message_to_out(message)

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

    target_result = await session.execute(
        select(Message).where(
            Message.id == body.last_read_message_id,
            Message.chat_id == chat_id,
        )
    )
    target_message = target_result.scalar_one_or_none()
    if target_message is None:
        raise NotFoundException("Message not found in this chat")

    unread_messages_result = await session.execute(
        select(Message.id).where(
            Message.chat_id == chat_id,
            Message.sender_id != current_user.id,
            Message.created_at <= target_message.created_at,
            ~Message.id.in_(
                select(MessageRead.message_id).where(
                    MessageRead.user_id == current_user.id,
                )
            ),
        )
    )
    unread_message_ids = [row[0] for row in unread_messages_result.all()]

    if not unread_message_ids:
        return

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