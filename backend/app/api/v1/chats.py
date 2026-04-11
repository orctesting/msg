import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select, func, and_, desc, case
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message, MessageRead
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ForbiddenException
from app.api.schemas.chat import ChatOut, ChatListOut

logger = structlog.get_logger()
router = APIRouter(prefix="/chats", tags=["chats"])


@router.get("", response_model=ChatListOut)
async def get_chats(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    # Get all chats the user is a member of
    result = await session.execute(
        select(Chat)
        .join(ChatMember, ChatMember.chat_id == Chat.id)
        .where(ChatMember.user_id == current_user.id)
        .order_by(desc(Chat.created_at))
    )
    chats = result.scalars().all()

    return ChatListOut(chats=[ChatOut.model_validate(c) for c in chats])


@router.get("/{chat_id}", response_model=ChatOut)
async def get_chat(
    chat_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    # Check membership
    result = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == current_user.id,
        )
    )
    if result.scalar_one_or_none() is None:
        raise ForbiddenException("You are not a member of this chat")

    result = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = result.scalar_one_or_none()
    if chat is None:
        raise NotFoundException("Chat not found")

    return ChatOut.model_validate(chat)