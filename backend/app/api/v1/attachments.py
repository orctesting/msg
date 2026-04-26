import uuid

from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.attachment import Attachment
from app.db.models.chat_member import ChatMember
from app.db.models.message import Message
from app.core.dependencies import get_current_user
from app.core.exceptions import (
    NotFoundException,
    ForbiddenException,
    ConflictException,
)
from app.config import settings
from app.services import s3_service
from app.api.schemas.attachment import (
    PresignUploadIn,
    PresignUploadOut,
    AttachmentOut,
)
from app.api.schemas.base import OkResponse
from fastapi import APIRouter, Depends, Request
from app.utils.public_url import derive_s3_public_base

logger = structlog.get_logger()
router = APIRouter(prefix="/attachments", tags=["attachments"])

ALLOWED_MIME_PREFIXES = ("image/", "video/", "audio/", "application/", "text/")


def _detect_file_kind(mime: str) -> str:
    m = mime.lower()
    if m.startswith("image/"):
        return "image"
    if m.startswith("video/"):
        return "video"
    if m.startswith("audio/"):
        return "audio"
    return "file"


async def _assert_chat_membership(
    session: AsyncSession, chat_id: uuid.UUID, user_id: uuid.UUID
) -> None:
    res = await session.execute(
        select(ChatMember).where(
            ChatMember.chat_id == chat_id,
            ChatMember.user_id == user_id,
        )
    )
    if res.scalar_one_or_none() is None:
        raise ForbiddenException("You are not a member of this chat")


async def _build_attachment_out(
    att: Attachment,
    include_urls: bool = True,
    public_base: str | None = None,
) -> AttachmentOut:
    download_url = None
    thumbnail_url = None
    if include_urls and att.status in ("uploaded", "ready"):
        try:
            download_url = await s3_service.generate_presigned_download_url(
                att.storage_key,
                filename=att.original_filename,
                public_base_override=public_base,
            )
        except Exception as e:
            logger.error("presign_download_error", error=str(e), attachment_id=str(att.id))
        if att.thumbnail_key:
            try:
                thumbnail_url = await s3_service.generate_presigned_download_url(
                    att.thumbnail_key,
                    public_base_override=public_base,
                )
            except Exception:
                pass

    return AttachmentOut(
        id=att.id,
        original_filename=att.original_filename,
        mime_type=att.mime_type,
        size_bytes=att.size_bytes,
        file_kind=att.file_kind,
        width=att.width,
        height=att.height,
        duration_ms=att.duration_ms,
        has_thumbnail=att.thumbnail_key is not None,
        status=att.status,
        created_at=att.created_at,
        download_url=download_url,
        thumbnail_url=thumbnail_url,
    )


@router.post("/presign-upload", response_model=PresignUploadOut)
async def presign_upload(
    body: PresignUploadIn,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    if body.size_bytes > settings.attachment_max_size_bytes:
        raise ConflictException(
            f"File too large (max {settings.attachment_max_size_bytes} bytes)"
        )

    if not any(body.mime_type.lower().startswith(p) for p in ALLOWED_MIME_PREFIXES):
        raise ConflictException("Mime type not allowed")

    if body.chat_id is not None:
        await _assert_chat_membership(session, body.chat_id, current_user.id)

    attachment_id = uuid.uuid4()
    storage_key = s3_service.build_storage_key(body.chat_id, attachment_id, body.filename)

    att = Attachment(
        id=attachment_id,
        uploader_user_id=current_user.id,
        chat_id=body.chat_id,
        storage_key=storage_key,
        original_filename=body.filename,
        mime_type=body.mime_type,
        size_bytes=body.size_bytes,
        file_kind=_detect_file_kind(body.mime_type),
        status="pending",
    )
    session.add(att)
    await session.commit()

    try:
        upload_url = await s3_service.generate_presigned_upload_url(
            storage_key=storage_key,
            content_type=body.mime_type,
            size_bytes=body.size_bytes,
            public_base_override=derive_s3_public_base(request),
        )
    except Exception as e:
        logger.error("presign_upload_error", error=str(e))
        raise ConflictException("Failed to generate upload URL")

    return PresignUploadOut(
        attachment_id=attachment_id,
        upload_url=upload_url,
        storage_key=storage_key,
        expires_in=settings.attachment_upload_url_ttl_seconds,
    )


@router.post("/{attachment_id}/complete", response_model=AttachmentOut)
async def complete_upload(
    attachment_id: uuid.UUID,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Attachment).where(Attachment.id == attachment_id)
    )
    att = res.scalar_one_or_none()
    if att is None:
        raise NotFoundException("Attachment not found")
    if att.uploader_user_id != current_user.id:
        raise ForbiddenException("Not your attachment")

    if att.status in ("uploaded", "ready"):
        return await _build_attachment_out(att, public_base=derive_s3_public_base(request))

    head = await s3_service.head_object(att.storage_key)
    if head is None:
        raise ConflictException("Object not found in storage; upload failed or not finished")

    actual_size = int(head.get("ContentLength", 0) or 0)
    if actual_size > 0:
        att.size_bytes = actual_size
    att.status = "uploaded"
    await session.commit()
    await session.refresh(att)

    # Для изображений — ставим задачу на генерацию превью
    if att.file_kind == "image":
        try:
            from app.workers.tasks.attachments import generate_attachment_thumbnail
            generate_attachment_thumbnail.delay(str(att.id))
        except Exception as e:
            logger.error("enqueue_thumbnail_error", error=str(e))
            # Fallback: считаем ready без превью
            att.status = "ready"
            await session.commit()
            await session.refresh(att)
    else:
        att.status = "ready"
        await session.commit()
        await session.refresh(att)

    return await _build_attachment_out(att, public_base=derive_s3_public_base(request))


@router.get("/{attachment_id}", response_model=AttachmentOut)
async def get_attachment(
    attachment_id: uuid.UUID,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Attachment).where(Attachment.id == attachment_id)
    )
    att = res.scalar_one_or_none()
    if att is None:
        raise NotFoundException("Attachment not found")

    # Доступ: uploader или член чата
    if att.uploader_user_id != current_user.id:
        if att.chat_id is None:
            raise ForbiddenException("No access")
        await _assert_chat_membership(session, att.chat_id, current_user.id)

    return await _build_attachment_out(att, public_base=derive_s3_public_base(request))


@router.get("/{attachment_id}/download-url")
async def get_download_url(
    attachment_id: uuid.UUID,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Attachment).where(Attachment.id == attachment_id)
    )
    att = res.scalar_one_or_none()
    if att is None:
        raise NotFoundException("Attachment not found")

    if att.uploader_user_id != current_user.id:
        if att.chat_id is None:
            raise ForbiddenException("No access")
        await _assert_chat_membership(session, att.chat_id, current_user.id)

    if att.status not in ("uploaded", "ready"):
        raise ConflictException("Attachment is not ready")

    url = await s3_service.generate_presigned_download_url(
        att.storage_key, filename=att.original_filename, public_base_override=derive_s3_public_base(request)
    )
    thumb = None
    if att.thumbnail_key:
        try:
            thumb = await s3_service.generate_presigned_download_url(att.thumbnail_key, public_base_override=derive_s3_public_base(request))
        except Exception:
            pass
    return {
        "download_url": url,
        "thumbnail_url": thumb,
        "expires_in": settings.attachment_download_url_ttl_seconds,
    }


@router.delete("/{attachment_id}", response_model=OkResponse)
async def delete_attachment(
    attachment_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(Attachment).where(Attachment.id == attachment_id)
    )
    att = res.scalar_one_or_none()
    if att is None:
        raise NotFoundException("Attachment not found")
    if att.uploader_user_id != current_user.id and current_user.role != "admin":
        raise ForbiddenException("Not your attachment")

    # Проверяем, есть ли links на это вложение
    from app.db.models.message_attachment_link import MessageAttachmentLink
    from sqlalchemy import func
    count_res = await session.execute(
        select(func.count(MessageAttachmentLink.id)).where(
            MessageAttachmentLink.attachment_id == attachment_id
        )
    )
    if (count_res.scalar() or 0) > 0:
        raise ConflictException("Attachment is linked to message(s); delete the message instead")

    # Не привязано — soft-delete файл в S3
    try:
        from app.services import s3_service
        new_key = await s3_service.move_to_deleted(att.storage_key)
        if new_key:
            att.storage_key = new_key
        if att.thumbnail_key:
            new_thumb = await s3_service.move_to_deleted(att.thumbnail_key)
            if new_thumb:
                att.thumbnail_key = new_thumb
    except Exception as e:
        logger.error("s3_soft_delete_error", error=str(e))

    att.status = "deleted"
    await session.commit()
    return OkResponse(detail="Deleted")