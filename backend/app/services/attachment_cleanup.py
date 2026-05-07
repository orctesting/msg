import uuid
import structlog
from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.attachment import Attachment
from app.db.models.message_attachment_link import MessageAttachmentLink
from app.services import s3_service

logger = structlog.get_logger()


async def soft_delete_attachment_if_orphan(
    session: AsyncSession,
    attachment_id: uuid.UUID,
) -> bool:
    """
    Если на attachment больше нет ни одной ссылки в message_attachment_links —
    переносит файлы в S3 в папку deleted/ и помечает attachment как 'deleted'.
    Возвращает True, если файл был перемещён.
    """
    count_res = await session.execute(
        select(func.count(MessageAttachmentLink.id)).where(
            MessageAttachmentLink.attachment_id == attachment_id
        )
    )
    if (count_res.scalar() or 0) > 0:
        return False

    att_res = await session.execute(
        select(Attachment).where(Attachment.id == attachment_id)
    )
    att = att_res.scalar_one_or_none()
    if att is None:
        return False
    if att.status == "deleted":
        return False

    new_key = await s3_service.move_to_deleted(att.storage_key)
    if new_key:
        att.storage_key = new_key
    if att.thumbnail_key:
        new_thumb = await s3_service.move_to_deleted(att.thumbnail_key)
        if new_thumb:
            att.thumbnail_key = new_thumb

    att.status = "deleted"
    att.message_id = None
    logger.info("attachment_soft_deleted", attachment_id=str(attachment_id))
    return True


async def cleanup_attachments_for_messages(
    session: AsyncSession,
    message_ids: list[uuid.UUID],
) -> None:
    """
    Удаляет links для указанных сообщений и soft-delete'ит attachments,
    которые остались без ссылок.
    """
    if not message_ids:
        return

    affected_res = await session.execute(
        select(MessageAttachmentLink.attachment_id).where(
            MessageAttachmentLink.message_id.in_(message_ids)
        ).distinct()
    )
    attachment_ids = [row[0] for row in affected_res.all()]

    # links удалятся каскадом при delete сообщения, но мы делаем явно
    from sqlalchemy import delete as sa_delete
    await session.execute(
        sa_delete(MessageAttachmentLink).where(
            MessageAttachmentLink.message_id.in_(message_ids)
        )
    )
    await session.flush()

    for aid in attachment_ids:
        await soft_delete_attachment_if_orphan(session, aid)