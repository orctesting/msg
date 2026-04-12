import uuid

from fastapi import APIRouter, Depends
from sqlalchemy import select, desc, func
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message, MessageRead
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ForbiddenException
from app.api.schemas.chat import ChatOut, ChatListOut, LastMessageOut

logger = structlog.get_logger()
router = APIRouter(prefix="/chats", tags=["chats"])


@router.get("", response_model=ChatListOut)
async def get_chats(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(
        select(Chat)
        .join(ChatMember, ChatMember.chat_id == Chat.id)
        .where(ChatMember.user_id == current_user.id)
        .order_by(desc(Chat.created_at))
    )
    chats = result.scalars().all()

    response_items: list[ChatOut] = []

    for chat in chats:
        last_message_result = await session.execute(
            select(Message)
            .where(Message.chat_id == chat.id)
            .order_by(desc(Message.created_at))
            .limit(1)
        )
        last_message = last_message_result.scalar_one_or_none()

        last_read_result = await session.execute(
            select(func.max(MessageRead.read_at))
            .join(Message, Message.id == MessageRead.message_id)
            .where(
                MessageRead.user_id == current_user.id,
                Message.chat_id == chat.id,
            )
        )
        last_read_at = last_read_result.scalar_one_or_none()

        unread_query = select(func.count(Message.id)).where(
            Message.chat_id == chat.id,
            Message.sender_id != current_user.id,
        )
        if last_read_at is not None:
            unread_query = unread_query.where(Message.created_at > last_read_at)

        unread_count = (await session.execute(unread_query)).scalar() or 0

        response_items.append(
            ChatOut(
                id=chat.id,
                name=chat.name,
                type=chat.type,
                created_at=chat.created_at,
                unread_count=unread_count,
                last_message=LastMessageOut.model_validate(last_message) if last_message else None,
            )
        )

    return ChatListOut(chats=response_items)


@router.get("/{chat_id}", response_model=ChatOut)
async def get_chat(
    chat_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    membership_result = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == current_user.id,
        )
    )
    membership = membership_result.scalar_one_or_none()
    if membership is None:
        raise ForbiddenException("You are not a member of this chat")

    result = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = result.scalar_one_or_none()
    if chat is None:
        raise NotFoundException("Chat not found")

    last_message_result = await session.execute(
        select(Message)
        .where(Message.chat_id == chat.id)
        .order_by(desc(Message.created_at))
        .limit(1)
    )
    last_message = last_message_result.scalar_one_or_none()

    last_read_result = await session.execute(
        select(func.max(MessageRead.read_at))
        .join(Message, Message.id == MessageRead.message_id)
        .where(
            MessageRead.user_id == current_user.id,
            Message.chat_id == chat.id,
        )
    )
    last_read_at = last_read_result.scalar_one_or_none()

    unread_query = select(func.count(Message.id)).where(
        Message.chat_id == chat.id,
        Message.sender_id != current_user.id,
    )
    if last_read_at is not None:
        unread_query = unread_query.where(Message.created_at > last_read_at)

    unread_count = (await session.execute(unread_query)).scalar() or 0

    return ChatOut(
        id=chat.id,
        name=chat.name,
        type=chat.type,
        created_at=chat.created_at,
        unread_count=unread_count,
        last_message=LastMessageOut.model_validate(last_message) if last_message else None,
    )