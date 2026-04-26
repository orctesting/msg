import uuid

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.core.dependencies import get_current_admin
from app.core.exceptions import NotFoundException, ConflictException
from app.api.schemas.user import UserOut, UserUpdateIn
from app.api.schemas.chat import ChatCreateIn, ChatOut
from app.api.schemas.base import OkResponse

logger = structlog.get_logger()
router = APIRouter(prefix="/admin", tags=["admin"])


# ── Users ──────────────────────────────────────────────────────────────

@router.post("/users", response_model=UserOut, status_code=201)
async def create_user(
    phone: str,
    display_name: str,
    role: str = "user",
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    from app.utils.username import generate_unique_username

    existing = await session.execute(select(User).where(User.phone == phone))
    if existing.scalar_one_or_none():
        raise ConflictException("User with this phone already exists")

    parts = display_name.strip().split(maxsplit=1)
    first = parts[0] if parts else None
    last = parts[1] if len(parts) > 1 else None

    username = await generate_unique_username(session, first, last, fallback_id=str(uuid.uuid4()))

    user = User(
        phone=phone,
        display_name=display_name,
        username=username,
        first_name=first,
        last_name=last,
        role=role,
    )
    session.add(user)
    await session.commit()
    await session.refresh(user)
    logger.info("admin_created_user", admin_id=str(admin.id), user_id=str(user.id))
    return UserOut.model_validate(user)


@router.get("/users", response_model=list[UserOut])
async def list_users(
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    is_active: bool | None = None,
    search: str | None = Query(None, description="Search by phone or display_name"),
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    query = select(User)
    if is_active is not None:
        query = query.where(User.is_active == is_active)
    if search:
        pattern = f"%{search.strip()}%"
        query = query.where(
            (User.phone.ilike(pattern)) | (User.display_name.ilike(pattern))
        )
    query = query.order_by(User.created_at.desc()).offset(offset).limit(limit)
    result = await session.execute(query)
    return [UserOut.model_validate(u) for u in result.scalars().all()]


@router.get("/users/{user_id}", response_model=UserOut)
async def get_user(
    user_id: uuid.UUID,
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if not user:
        raise NotFoundException("User not found")
    return UserOut.model_validate(user)


@router.patch("/users/{user_id}", response_model=UserOut)
async def update_user(
    user_id: uuid.UUID,
    body: UserUpdateIn,
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if not user:
        raise NotFoundException("User not found")

    if body.display_name is not None:
        user.display_name = body.display_name

    await session.commit()
    await session.refresh(user)
    return UserOut.model_validate(user)


@router.delete("/users/{user_id}", response_model=OkResponse)
async def deactivate_user(
    user_id: uuid.UUID,
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(select(User).where(User.id == user_id))
    user = result.scalar_one_or_none()
    if not user:
        raise NotFoundException("User not found")

    user.is_active = False
    await session.commit()
    logger.info("admin_deactivated_user", admin_id=str(admin.id), user_id=str(user_id))
    return OkResponse(detail="User deactivated")


# ── Chats ──────────────────────────────────────────────────────────────

@router.post("/chats", response_model=ChatOut, status_code=201)
async def create_chat(
    body: ChatCreateIn,
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    if body.type not in ("group", "personal"):
        raise ConflictException("Invalid chat type")
    if body.type == "personal":
        raise ConflictException("Use /chats/personal endpoint for personal chats")

    chat = Chat(name=body.name, type=body.type)
    session.add(chat)
    await session.flush()

    # админа тоже добавляем в чат (чтобы он мог отправить системку и был участником)
    session.add(ChatMember(chat_id=chat.id, user_id=admin.id, role="admin"))

    for uid in body.member_ids:
        if uid == admin.id:
            continue
        user_result = await session.execute(select(User).where(User.id == uid))
        if user_result.scalar_one_or_none() is None:
            raise NotFoundException(f"User {uid} not found")
        session.add(ChatMember(chat_id=chat.id, user_id=uid, role="member"))

    # Системное уведомление от имени админа
    from app.db.models.message import Message
    notification = Message(
        chat_id=chat.id,
        sender_id=admin.id,
        content="Вас добавили в чат, нужно больше чатов",
        message_type="notification",
    )
    session.add(notification)

    await session.commit()
    await session.refresh(chat)
    await session.refresh(notification)

    logger.info("admin_created_chat", admin_id=str(admin.id), chat_id=str(chat.id))

    # WS broadcast о новом сообщении-уведомлении
    try:
        from app.services.ws_manager import ws_manager
        await ws_manager.publish_event(
            chat_id=chat.id,
            event={
                "type": "new_message",
                "data": {
                    "chat_id": str(chat.id),
                    "message": {
                        "id": str(notification.id),
                        "chat_id": str(chat.id),
                        "sender_id": str(admin.id),
                        "sender_name": admin.display_name,
                        "sender_role": "admin",
                        "content": notification.content,
                        "message_type": notification.message_type,
                        "created_at": notification.created_at.isoformat(),
                    },
                },
            },
        )
    except Exception as e:
        logger.error("ws_publish_chat_created_error", error=str(e))

    return ChatOut(
        id=chat.id,
        name=chat.name,
        type=chat.type,
        created_at=chat.created_at,
        unread_count=0,
        last_message=None,
    )


@router.get("/chats", response_model=list[ChatOut])
async def list_chats(
    offset: int = Query(0, ge=0),
    limit: int = Query(50, ge=1, le=100),
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    query = select(Chat).order_by(Chat.created_at.desc()).offset(offset).limit(limit)
    result = await session.execute(query)
    chats = result.scalars().all()
    return [
        ChatOut(
            id=chat.id,
            name=chat.name,
            type=chat.type,
            created_at=chat.created_at,
            unread_count=0,
            last_message=None,
        )
        for chat in chats
    ]


@router.post("/chats/{chat_id}/members", response_model=OkResponse, status_code=201)
async def add_chat_member(
    chat_id: uuid.UUID,
    user_id: uuid.UUID,
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    chat_res = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = chat_res.scalar_one_or_none()
    if chat is None:
        raise NotFoundException("Chat not found")
    if chat.type == "personal":
        raise ConflictException("Cannot add members to personal chat")

    user_res = await session.execute(select(User).where(User.id == user_id))
    if user_res.scalar_one_or_none() is None:
        raise NotFoundException("User not found")

    existing = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == user_id,
        )
    )
    if existing.scalar_one_or_none():
        raise ConflictException("User is already a member of this chat")

    session.add(ChatMember(chat_id=chat_id, user_id=user_id, role="member"))
    await session.commit()
    return OkResponse(detail="Member added")


@router.delete("/chats/{chat_id}/members/{user_id}", response_model=OkResponse)
async def remove_chat_member(
    chat_id: uuid.UUID,
    user_id: uuid.UUID,
    admin: User = Depends(get_current_admin),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == user_id,
        )
    )
    member = result.scalar_one_or_none()
    if member is None:
        raise NotFoundException("Member not found in this chat")

    await session.delete(member)
    await session.commit()
    return OkResponse(detail="Member removed")