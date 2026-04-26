import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Query
from sqlalchemy import select, desc, delete
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import joinedload
from app.db.models.push_log import PushLog
from app.db.models.attachment import Attachment
from app.services import s3_service
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message, MessageRead
from app.db.models.deleted_message import DeletedMessage
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ForbiddenException, ConflictException
from app.api.schemas.message import (
    MessageSendIn,
    MessageEditIn,
    MessageOut,
    MessageListOut,
    MessageReadIn,
    ReplyPreview,
    ForwardedInfo,
    BulkDeleteIn,
    ForwardMessageIn,
)
from app.api.schemas.base import OkResponse
from app.services.ws_manager import ws_manager
from app.utils.idempotency import acquire_message_idempotency
from fastapi import APIRouter, Depends, Query, Request
from app.utils.public_url import derive_s3_public_base
from app.db.models.message_attachment_link import MessageAttachmentLink
from app.services.attachment_cleanup import cleanup_attachments_for_messages, soft_delete_attachment_if_orphan

logger = structlog.get_logger()
router = APIRouter(prefix="/chats", tags=["messages"])


async def _ensure_personal_chat_visible(session: AsyncSession, chat_id: uuid.UUID):
    """Make personal chat visible to all members after first message activity."""
    chat_res = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = chat_res.scalar_one_or_none()
    if chat is None or chat.type != "personal":
        return
    hidden_members = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.is_visible == False,
        )
    )
    changed = False
    for m in hidden_members.scalars().all():
        m.is_visible = True
        changed = True
    if changed:
        await session.commit()


def _reply_preview_from(msg: Message | None) -> ReplyPreview | None:
    if msg is None:
        return None
    sender = msg.sender
    return ReplyPreview(
        id=msg.id,
        sender_id=msg.sender_id,
        sender_name=sender.display_name if sender else "Admin",
        content=msg.content,
        message_type=msg.message_type,
        is_deleted=False,
    )


def _forwarded_info_from(msg: Message) -> ForwardedInfo | None:
    if msg.forwarded_from_message_id is None and msg.forwarded_from_sender_name is None:
        return None
    original = msg.forwarded_from
    if original is not None:
        sender = original.sender
        return ForwardedInfo(
            original_message_id=original.id,
            sender_name=sender.display_name if sender else (msg.forwarded_from_sender_name or "Admin"),
            is_deleted=False,
        )
    return ForwardedInfo(
        original_message_id=None,
        sender_name=msg.forwarded_from_sender_name,
        is_deleted=True,
    )


def _message_to_out(message: Message, attachments_out: list | None = None) -> MessageOut:
    sender = message.sender
    reply_preview = None
    if message.reply_to_message_id is not None:
        if message.reply_to is not None:
            reply_preview = _reply_preview_from(message.reply_to)
        else:
            reply_preview = ReplyPreview(
                id=message.reply_to_message_id,
                sender_name=None,
                content="Сообщение удалено",
                message_type="text",
                is_deleted=True,
            )
    return MessageOut(
        id=message.id,
        chat_id=message.chat_id,
        sender_id=message.sender_id,
        sender_name=sender.display_name if sender else "Admin",
        sender_role=sender.role if sender else "admin",
        content=message.content,
        message_type=message.message_type,
        created_at=message.created_at,
        edited_at=message.edited_at,
        reply_to=reply_preview,
        forwarded_from=_forwarded_info_from(message),
        attachments=attachments_out or [],
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


async def _load_message_full(session: AsyncSession, message_id: uuid.UUID) -> Message | None:
    result = await session.execute(
        select(Message)
        .options(
            joinedload(Message.sender),
            joinedload(Message.reply_to).joinedload(Message.sender),
            joinedload(Message.forwarded_from).joinedload(Message.sender),
            joinedload(Message.attachments),
        )
        .where(Message.id == message_id)
    )
    return result.scalars().unique().one_or_none()
    
    
async def _build_attachments_out(message: Message, public_base: str | None = None) -> list:
    from app.api.schemas.attachment import AttachmentOut
    result = []
    atts = list(message.attachments or [])
    for att in atts:
        download_url = None
        thumb_url = None
        if att.status in ("uploaded", "ready"):
            try:
                download_url = await s3_service.generate_presigned_download_url(
                    att.storage_key,
                    filename=att.original_filename,
                    public_base_override=public_base,
                )
            except Exception as e:
                logger.error("msg_att_presign_error", error=str(e))
            if att.thumbnail_key:
                try:
                    thumb_url = await s3_service.generate_presigned_download_url(
                        att.thumbnail_key,
                        public_base_override=public_base,
                    )
                except Exception:
                    pass
        result.append(
            AttachmentOut(
                id=att.id, original_filename=att.original_filename, mime_type=att.mime_type,
                size_bytes=att.size_bytes, file_kind=att.file_kind, width=att.width,
                height=att.height, duration_ms=att.duration_ms,
                has_thumbnail=att.thumbnail_key is not None, status=att.status,
                created_at=att.created_at, download_url=download_url, thumbnail_url=thumb_url,
            )
        )
    return result


@router.get("/{chat_id}/messages", response_model=MessageListOut)
async def get_messages(
    chat_id: uuid.UUID,
    request: Request,
    before: uuid.UUID | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    query = (
        select(Message)
        .options(
            joinedload(Message.sender),
            joinedload(Message.reply_to).joinedload(Message.sender),
            joinedload(Message.forwarded_from).joinedload(Message.sender),
            joinedload(Message.attachments),
        )
        .where(Message.chat_id == chat_id)
    )

    if before:
        cursor_result = await session.execute(select(Message.created_at).where(Message.id == before))
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

    messages_out = []
    for m in messages:
        atts_out = await _build_attachments_out(m, public_base=derive_s3_public_base(request))
        messages_out.append(_message_to_out(m, atts_out))

    return MessageListOut(
        messages=messages_out,
        has_more=has_more,
        read_by_others_up_to=read_by_others_up_to,
    )


@router.post("/{chat_id}/messages", response_model=MessageOut, status_code=201)
async def send_message(
    chat_id: uuid.UUID,
    request: Request,
    body: MessageSendIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    chat_result = await session.execute(select(Chat).where(Chat.id == chat_id))
    if chat_result.scalar_one_or_none() is None:
        raise NotFoundException("Chat not found")

    # Валидация reply
    if body.reply_to_message_id is not None:
        reply_res = await session.execute(
            select(Message).where(
                Message.id == body.reply_to_message_id,
                Message.chat_id == chat_id,
            )
        )
        if reply_res.scalar_one_or_none() is None:
            raise NotFoundException("Reply target not found in this chat")

    # Валидация forward: source — где пользователь является членом
    forwarded_sender_name: str | None = None
    if body.forwarded_from_message_id is not None:
        src_res = await session.execute(
            select(Message)
            .options(joinedload(Message.sender))
            .where(Message.id == body.forwarded_from_message_id)
        )
        src = src_res.scalars().unique().one_or_none()
        if src is None:
            raise NotFoundException("Forwarded source message not found")
        # Проверяем членство в source чате
        await _check_membership(session, src.chat_id, current_user.id)
        forwarded_sender_name = src.sender.display_name if src.sender else "Admin"

    if body.idempotency_key:
        existing_result = await session.execute(
            select(Message).where(Message.idempotency_key == body.idempotency_key)
        )
        existing = existing_result.scalar_one_or_none()
        if existing is not None:
            full = await _load_message_full(session, existing.id)
            return _message_to_out(full)

        acquired = await acquire_message_idempotency(str(body.idempotency_key))
        if not acquired:
            existing_result = await session.execute(
                select(Message).where(Message.idempotency_key == body.idempotency_key)
            )
            existing = existing_result.scalar_one_or_none()
            if existing is not None:
                full = await _load_message_full(session, existing.id)
                return _message_to_out(full)
                
    # Валидация attachments
    validated_attachments: list[Attachment] = []
    if body.attachment_ids:
        att_res = await session.execute(
            select(Attachment).where(Attachment.id.in_(body.attachment_ids))
        )
        atts = list(att_res.scalars().all())
        if len(atts) != len(body.attachment_ids):
            raise NotFoundException("Some attachments not found")
        for a in atts:
            if a.uploader_user_id != current_user.id:
                raise ForbiddenException("Attachment belongs to another user")
            # Проверяем нет ли уже привязки к другому сообщению через links
            existing_link = await session.execute(
                select(MessageAttachmentLink).where(
                    MessageAttachmentLink.attachment_id == a.id
                ).limit(1)
            )
            if existing_link.scalar_one_or_none() is not None:
                raise ConflictException("Attachment already linked to a message")
            if a.status not in ("uploaded", "ready"):
                raise ConflictException(f"Attachment {a.id} is not ready")
        validated_attachments = atts

    # Требование: либо content, либо вложения
    if not body.content.strip() and not validated_attachments:
        raise ConflictException("Message must have content or attachments")

    message = Message(
        chat_id=chat_id,
        sender_id=current_user.id,
        content=body.content,
        message_type=body.message_type,
        idempotency_key=body.idempotency_key,
        reply_to_message_id=body.reply_to_message_id,
        forwarded_from_message_id=body.forwarded_from_message_id,
        forwarded_from_sender_name=forwarded_sender_name,
    )
    session.add(message)
    await session.flush()

    need_thumb_ids: list[uuid.UUID] = []
    for a in validated_attachments:
        # Создаём связь через links вместо прямой записи message_id
        session.add(MessageAttachmentLink(message_id=message.id, attachment_id=a.id))
        a.message_id = message.id  # primary owner для backward-compat
        a.chat_id = chat_id
        if a.status == "uploaded":
            a.status = "ready"
        if a.file_kind == "image" and a.thumbnail_key is None:
            need_thumb_ids.append(a.id)

    await session.commit()

    # Enqueue thumbnail generation для тех, у кого его ещё нет
    for aid in need_thumb_ids:
        try:
            from app.workers.tasks.attachments import generate_attachment_thumbnail
            generate_attachment_thumbnail.delay(str(aid))
        except Exception as e:
            logger.error("enqueue_thumbnail_send_error", error=str(e))
    
    await _ensure_personal_chat_visible(session, chat_id)

    full = await _load_message_full(session, message.id)
    atts_out = await _build_attachments_out(full, public_base=derive_s3_public_base(request))
    message_out = _message_to_out(full, atts_out)

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


@router.patch("/{chat_id}/messages/{message_id}", response_model=MessageOut)
async def edit_message(
    chat_id: uuid.UUID,
    request: Request,
    message_id: uuid.UUID,
    body: MessageEditIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    msg = await _load_message_full(session, message_id)
    atts_out = await _build_attachments_out(msg, public_base=derive_s3_public_base(request))
    out = _message_to_out(msg, atts_out)
    if msg is None or msg.chat_id != chat_id:
        raise NotFoundException("Message not found")

    is_owner = msg.sender_id == current_user.id
    is_admin = current_user.role == "admin"
    if not is_owner and not is_admin:
        raise ForbiddenException("Cannot edit foreign message")

    if msg.message_type not in ("text", "notification"):
        raise ConflictException("This message type cannot be edited")

    if not body.content.strip():
        raise ConflictException("Content cannot be empty")

    msg.content = body.content
    msg.edited_at = datetime.now(timezone.utc)
    await session.commit()

    msg = await _load_message_full(session, message_id)
    out = _message_to_out(msg)

    try:
        await ws_manager.publish_event(
            chat_id=chat_id,
            event={
                "type": "message_edited",
                "data": {
                    "chat_id": str(chat_id),
                    "message": out.model_dump(mode="json"),
                },
            },
        )
    except Exception as e:
        logger.error("ws_edit_publish_error", error=str(e))

    return out


@router.delete("/{chat_id}/messages/{message_id}", response_model=OkResponse)
async def delete_message(
    chat_id: uuid.UUID,
    message_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    msg_res = await session.execute(
        select(Message).where(Message.id == message_id, Message.chat_id == chat_id)
    )
    msg = msg_res.scalar_one_or_none()
    if msg is None:
        raise NotFoundException("Message not found")

    is_owner = msg.sender_id == current_user.id
    is_admin = current_user.role == "admin"
    if not is_owner and not is_admin:
        raise ForbiddenException("Cannot delete foreign message")

    # Архив
    session.add(
        DeletedMessage(
            original_message_id=msg.id,
            chat_id=msg.chat_id,
            sender_id=msg.sender_id,
            content=msg.content,
            message_type=msg.message_type,
            original_created_at=msg.created_at,
            edited_at=msg.edited_at,
            reply_to_message_id=msg.reply_to_message_id,
            forwarded_from_message_id=msg.forwarded_from_message_id,
            forwarded_from_sender_name=msg.forwarded_from_sender_name,
            deleted_by_user_id=current_user.id,
        )
    )

    # Снять pin, если это было закреплённое сообщение
    chat_res = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = chat_res.scalar_one_or_none()
    if chat is not None and chat.pinned_message_id == msg.id:
        chat.pinned_message_id = None
        chat.pinned_by_user_id = None
        chat.pinned_at = None

    # Cleanup attachments через links + soft-delete осиротевших
    await cleanup_attachments_for_messages(session, [msg.id])

    # Вручную удаляем зависимости
    await session.execute(delete(MessageRead).where(MessageRead.message_id == msg.id))
    await session.execute(delete(PushLog).where(PushLog.message_id == msg.id))
    await session.flush()

    await session.delete(msg)
    await session.commit()

    try:
        await ws_manager.publish_event(
            chat_id=chat_id,
            event={
                "type": "message_deleted",
                "data": {
                    "chat_id": str(chat_id),
                    "message_ids": [str(message_id)],
                },
            },
        )
    except Exception as e:
        logger.error("ws_delete_publish_error", error=str(e))

    return OkResponse(detail="Deleted")


@router.post("/{chat_id}/messages/bulk-delete", response_model=OkResponse)
async def bulk_delete_messages(
    chat_id: uuid.UUID,
    body: BulkDeleteIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, chat_id, current_user.id)

    if not body.message_ids:
        return OkResponse(detail="Nothing to delete")

    msgs_res = await session.execute(
        select(Message).where(
            Message.id.in_(body.message_ids),
            Message.chat_id == chat_id,
        )
    )
    messages = list(msgs_res.scalars().all())

    is_admin = current_user.role == "admin"
    deleted_ids: list[uuid.UUID] = []

    chat_res = await session.execute(select(Chat).where(Chat.id == chat_id))
    chat = chat_res.scalar_one_or_none()

    for msg in messages:
        if not is_admin and msg.sender_id != current_user.id:
            continue
        session.add(
            DeletedMessage(
                original_message_id=msg.id,
                chat_id=msg.chat_id,
                sender_id=msg.sender_id,
                content=msg.content,
                message_type=msg.message_type,
                original_created_at=msg.created_at,
                edited_at=msg.edited_at,
                reply_to_message_id=msg.reply_to_message_id,
                forwarded_from_message_id=msg.forwarded_from_message_id,
                forwarded_from_sender_name=msg.forwarded_from_sender_name,
                deleted_by_user_id=current_user.id,
            )
        )
        if chat is not None and chat.pinned_message_id == msg.id:
            chat.pinned_message_id = None
            chat.pinned_by_user_id = None
            chat.pinned_at = None
        deleted_ids.append(msg.id)

    if not deleted_ids:
        raise ForbiddenException("Nothing you can delete")

    # Cleanup attachments через links + soft-delete осиротевших
    await cleanup_attachments_for_messages(session, deleted_ids)

    await session.execute(delete(MessageRead).where(MessageRead.message_id.in_(deleted_ids)))
    await session.execute(delete(PushLog).where(PushLog.message_id.in_(deleted_ids)))
    await session.flush()

    await session.execute(delete(Message).where(Message.id.in_(deleted_ids)))
    await session.commit()

    try:
        await ws_manager.publish_event(
            chat_id=chat_id,
            event={
                "type": "message_deleted",
                "data": {
                    "chat_id": str(chat_id),
                    "message_ids": [str(i) for i in deleted_ids],
                },
            },
        )
    except Exception as e:
        logger.error("ws_bulk_delete_publish_error", error=str(e))

    return OkResponse(detail=f"Deleted {len(deleted_ids)}")


@router.post("/forward", response_model=MessageOut, status_code=201)
async def forward_message(
    body: ForwardMessageIn,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    await _check_membership(session, body.source_chat_id, current_user.id)
    await _check_membership(session, body.target_chat_id, current_user.id)

    src_res = await session.execute(
        select(Message)
        .options(joinedload(Message.sender), joinedload(Message.attachments))
        .where(Message.id == body.message_id, Message.chat_id == body.source_chat_id)
    )
    src = src_res.scalars().unique().one_or_none()
    if src is None:
        raise NotFoundException("Source message not found")

    if body.idempotency_key:
        existing_res = await session.execute(
            select(Message).where(Message.idempotency_key == body.idempotency_key)
        )
        existing = existing_res.scalar_one_or_none()
        if existing is not None:
            full = await _load_message_full(session, existing.id)
            return _message_to_out(full)
        acquired = await acquire_message_idempotency(str(body.idempotency_key))
        if not acquired:
            existing_res = await session.execute(
                select(Message).where(Message.idempotency_key == body.idempotency_key)
            )
            existing = existing_res.scalar_one_or_none()
            if existing is not None:
                full = await _load_message_full(session, existing.id)
                return _message_to_out(full)

    new_msg = Message(
        chat_id=body.target_chat_id,
        sender_id=current_user.id,
        content=src.content,
        message_type=src.message_type,
        idempotency_key=body.idempotency_key,
        forwarded_from_message_id=src.id,
        forwarded_from_sender_name=src.sender.display_name if src.sender else "Admin",
    )
    session.add(new_msg)
    await session.flush()

    # Копируем links на те же attachments — без копирования файлов в S3
    for att in (src.attachments or []):
        session.add(MessageAttachmentLink(message_id=new_msg.id, attachment_id=att.id))

    await session.commit()

    await _ensure_personal_chat_visible(session, body.target_chat_id)

    full = await _load_message_full(session, new_msg.id)
    atts_out = await _build_attachments_out(full, public_base=derive_s3_public_base(request))
    out = _message_to_out(full, atts_out)

    try:
        await ws_manager.publish_new_message(
            chat_id=body.target_chat_id,
            message_data=out.model_dump(mode="json"),
            sender_id=current_user.id,
        )
    except Exception as e:
        logger.error("ws_forward_publish_error", error=str(e))

    try:
        members_result = await session.execute(
            select(ChatMember.user_id).where(
                ChatMember.chat_id == body.target_chat_id,
                ChatMember.user_id != current_user.id,
            )
        )
        recipient_ids = [str(row[0]) for row in members_result.all()]
        if recipient_ids:
            from app.workers.tasks.push import send_push_notification
            send_push_notification.delay(str(new_msg.id), recipient_ids)
    except Exception as e:
        logger.error("push_enqueue_error", error=str(e))

    return out


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