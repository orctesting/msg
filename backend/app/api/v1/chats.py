import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import select, desc, func, delete, and_
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import joinedload
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message, MessageRead
from app.db.models.user_pinned_dismissal import UserPinnedDismissal
from app.db.models.user_contact_dismissal import UserContactDismissal
from app.db.models.contact import Contact
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ForbiddenException, ConflictException
from app.api.schemas.chat import (
    ChatOut,
    ChatListOut,
    LastMessageOut,
    PinnedMessageOut,
    PinMessageIn,
    PeerUserOut,
    PersonalChatCreateIn,
)
from app.api.schemas.base import OkResponse
from app.services.ws_manager import ws_manager

logger = structlog.get_logger()
router = APIRouter(prefix="/chats", tags=["chats"])


async def _get_pinned_for_user(
    session: AsyncSession,
    chat: Chat,
    user_id: uuid.UUID,
) -> PinnedMessageOut | None:
    if chat.pinned_message_id is None:
        return None

    dismissal_res = await session.execute(
        select(UserPinnedDismissal).where(
            UserPinnedDismissal.user_id == user_id,
            UserPinnedDismissal.chat_id == chat.id,
        )
    )
    dismissal = dismissal_res.scalar_one_or_none()
    if dismissal is not None and dismissal.pinned_message_id == chat.pinned_message_id:
        return None

    msg_res = await session.execute(
        select(Message)
        .options(joinedload(Message.sender))
        .where(Message.id == chat.pinned_message_id)
    )
    msg = msg_res.scalars().unique().one_or_none()
    if msg is None:
        return None

    return PinnedMessageOut(
        id=msg.id,
        chat_id=msg.chat_id,
        sender_id=msg.sender_id,
        sender_name=msg.sender.display_name if msg.sender else "Admin",
        content=msg.content,
        message_type=msg.message_type,
        created_at=msg.created_at,
        pinned_by_user_id=chat.pinned_by_user_id,
        pinned_at=chat.pinned_at,
    )


async def _resolve_personal_chat_view(
    session: AsyncSession,
    chat: Chat,
    current_user: User,
) -> tuple[str, PeerUserOut | None, bool | None, bool | None]:
    """
    For personal chat, returns (display_name, peer_user, peer_is_in_contacts, peer_dismissed).
    For non-personal, returns (chat.name, None, None, None).
    """
    if chat.type != "personal":
        return chat.name, None, None, None

    members_res = await session.execute(
        select(ChatMember, User)
        .join(User, User.id == ChatMember.user_id)
        .where(ChatMember.chat_id == chat.id)
    )
    rows = members_res.all()
    other_user: User | None = None
    for _, user in rows:
        if user.id != current_user.id:
            other_user = user
            break

    if other_user is None:
        return chat.name, None, None, None

    contact_res = await session.execute(
        select(Contact).where(
            Contact.owner_user_id == current_user.id,
            Contact.contact_user_id == other_user.id,
        )
    )
    contact = contact_res.scalar_one_or_none()

    display_name = contact.display_name if contact else other_user.display_name

    dismissal_res = await session.execute(
        select(UserContactDismissal).where(
            UserContactDismissal.user_id == current_user.id,
            UserContactDismissal.peer_user_id == other_user.id,
        )
    )
    dismissed = dismissal_res.scalar_one_or_none() is not None

    peer = PeerUserOut(
        id=other_user.id,
        phone=other_user.phone,
        display_name=other_user.display_name,
    )

    return display_name, peer, contact is not None, dismissed


async def _build_chat_out(
    session: AsyncSession,
    chat: Chat,
    current_user: User,
) -> ChatOut:
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

    pinned = await _get_pinned_for_user(session, chat, current_user.id)
    display_name, peer, in_contacts, dismissed = await _resolve_personal_chat_view(
        session, chat, current_user
    )

    return ChatOut(
        id=chat.id,
        name=display_name,
        type=chat.type,
        created_at=chat.created_at,
        unread_count=unread_count,
        last_message=LastMessageOut.model_validate(last_message) if last_message else None,
        pinned_message=pinned,
        peer_user=peer,
        peer_is_in_contacts=in_contacts,
        peer_dismissed=dismissed,
    )


@router.get("", response_model=ChatListOut)
async def get_chats(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(
        select(Chat, ChatMember.is_visible)
        .join(ChatMember, ChatMember.chat_id == Chat.id)
        .where(ChatMember.user_id == current_user.id)
        .order_by(desc(Chat.created_at))
    )
    rows = result.all()

    response_items: list[ChatOut] = []
    for chat, is_visible in rows:
        if chat.type == "personal" and not is_visible:
            continue
        response_items.append(await _build_chat_out(session, chat, current_user))

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

    return await _build_chat_out(session, chat, current_user)


@router.post("/{chat_id}/pin", response_model=OkResponse)
async def pin_message(
    chat_id: uuid.UUID,
    body: PinMessageIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    membership_result = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == current_user.id,
        )
    )
    if membership_result.scalar_one_or_none() is None:
        raise ForbiddenException("You are not a member of this chat")

    chat_res = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = chat_res.scalar_one_or_none()
    if chat is None:
        raise NotFoundException("Chat not found")

    msg_res = await session.execute(
        select(Message).where(
            Message.id == body.message_id,
            Message.chat_id == chat_id,
        )
    )
    if msg_res.scalar_one_or_none() is None:
        raise NotFoundException("Message not found in this chat")

    chat.pinned_message_id = body.message_id
    chat.pinned_by_user_id = current_user.id
    chat.pinned_at = datetime.now(timezone.utc)

    await session.execute(
        delete(UserPinnedDismissal).where(UserPinnedDismissal.chat_id == chat_id)
    )

    await session.commit()

    try:
        await ws_manager.publish_event(
            chat_id=chat_id,
            event={
                "type": "message_pinned",
                "data": {
                    "chat_id": str(chat_id),
                    "message_id": str(body.message_id),
                    "pinned_by_user_id": str(current_user.id),
                    "pinned_at": chat.pinned_at.isoformat(),
                },
            },
        )
    except Exception as e:
        logger.error("ws_pin_publish_error", error=str(e))

    return OkResponse(detail="Pinned")


@router.delete("/{chat_id}/pin", response_model=OkResponse)
async def unpin_message(
    chat_id: uuid.UUID,
    scope: str = "local",
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    membership_result = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == current_user.id,
        )
    )
    if membership_result.scalar_one_or_none() is None:
        raise ForbiddenException("You are not a member of this chat")

    chat_res = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = chat_res.scalar_one_or_none()
    if chat is None:
        raise NotFoundException("Chat not found")
    if chat.pinned_message_id is None:
        return OkResponse(detail="Nothing to unpin")

    if scope == "global":
        if current_user.role != "admin":
            raise ForbiddenException("Only admin can unpin globally")

        chat.pinned_message_id = None
        chat.pinned_by_user_id = None
        chat.pinned_at = None
        await session.execute(
            delete(UserPinnedDismissal).where(UserPinnedDismissal.chat_id == chat_id)
        )
        await session.commit()

        try:
            await ws_manager.publish_event(
                chat_id=chat_id,
                event={
                    "type": "message_unpinned",
                    "data": {"chat_id": str(chat_id), "scope": "global"},
                },
            )
        except Exception as e:
            logger.error("ws_unpin_publish_error", error=str(e))

        return OkResponse(detail="Unpinned globally")

    pinned_id = chat.pinned_message_id
    existing_res = await session.execute(
        select(UserPinnedDismissal).where(
            UserPinnedDismissal.user_id == current_user.id,
            UserPinnedDismissal.chat_id == chat_id,
        )
    )
    existing = existing_res.scalar_one_or_none()
    if existing is not None:
        existing.pinned_message_id = pinned_id
        existing.dismissed_at = datetime.now(timezone.utc)
    else:
        session.add(
            UserPinnedDismissal(
                user_id=current_user.id,
                chat_id=chat_id,
                pinned_message_id=pinned_id,
            )
        )
    await session.commit()

    return OkResponse(detail="Unpinned locally")


@router.post("/personal", response_model=ChatOut, status_code=201)
async def create_personal_chat(
    body: PersonalChatCreateIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    if body.contact_id is None and body.phone is None:
        raise NotFoundException("contact_id or phone is required")

    target_user: User | None = None
    target_phone: str | None = None

    if body.contact_id is not None:
        contact_res = await session.execute(
            select(Contact).where(
                Contact.id == body.contact_id,
                Contact.owner_user_id == current_user.id,
            )
        )
        contact = contact_res.scalar_one_or_none()
        if contact is None:
            raise NotFoundException("Contact not found")
        if contact.contact_user_id is None:
            raise ConflictException("Contact user is not registered")
        target_phone = contact.phone
        user_res = await session.execute(select(User).where(User.id == contact.contact_user_id))
        target_user = user_res.scalar_one_or_none()
    else:
        target_phone = body.phone.strip()
        user_res = await session.execute(select(User).where(User.phone == target_phone))
        target_user = user_res.scalar_one_or_none()
        if target_user is None:
            raise ConflictException("User with this phone is not registered")

    if target_user is None:
        raise NotFoundException("Target user not found")

    if target_user.id == current_user.id:
        raise ConflictException("Cannot create personal chat with yourself")

    existing_res = await session.execute(
        select(Chat)
        .join(ChatMember, ChatMember.chat_id == Chat.id)
        .where(
            Chat.type == "personal",
            ChatMember.user_id == current_user.id,
        )
    )
    candidate_chats = list(existing_res.scalars().all())
    for chat in candidate_chats:
        other_res = await session.execute(
            select(ChatMember).where(
                ChatMember.chat_id == chat.id,
                ChatMember.user_id == target_user.id,
            )
        )
        if other_res.scalar_one_or_none() is not None:
            # Ensure initiator visibility is true (in case was hidden earlier)
            await session.execute(
                select(ChatMember).where(
                    ChatMember.chat_id == chat.id,
                    ChatMember.user_id == current_user.id,
                )
            )
            my_member_res = await session.execute(
                select(ChatMember).where(
                    ChatMember.chat_id == chat.id,
                    ChatMember.user_id == current_user.id,
                )
            )
            my_member = my_member_res.scalar_one_or_none()
            if my_member is not None and not my_member.is_visible:
                my_member.is_visible = True
                await session.commit()
            return await _build_chat_out(session, chat, current_user)

    chat = Chat(name="", type="personal")
    session.add(chat)
    await session.flush()

    session.add(
        ChatMember(
            chat_id=chat.id,
            user_id=current_user.id,
            role="member",
            is_visible=True,
        )
    )
    session.add(
        ChatMember(
            chat_id=chat.id,
            user_id=target_user.id,
            role="member",
            is_visible=False,
        )
    )

    await session.commit()
    await session.refresh(chat)

    logger.info(
        "personal_chat_created",
        user_id=str(current_user.id),
        target_id=str(target_user.id),
        chat_id=str(chat.id),
    )

    return await _build_chat_out(session, chat, current_user)