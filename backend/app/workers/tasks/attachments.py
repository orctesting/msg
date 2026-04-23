import io
import uuid

from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker
from PIL import Image
import structlog

from app.config import settings
from app.workers.celery_app import celery_app
from app.db.models.attachment import Attachment
from app.services import s3_sync_service

logger = structlog.get_logger()

_sync_engine = create_engine(settings.database_url.replace("+asyncpg", ""), future=True)
SyncSessionLocal = sessionmaker(bind=_sync_engine, autoflush=False, autocommit=False)


THUMBNAIL_MAX_SIZE = (512, 512)
THUMBNAIL_QUALITY = 80


def _thumbnail_key_for(storage_key: str) -> str:
    return f"thumbs/{storage_key}.jpg"


def _generate_image_thumbnail(data: bytes) -> tuple[bytes, int, int] | None:
    try:
        img = Image.open(io.BytesIO(data))
        img = img.convert("RGB")
        original_w, original_h = img.size
        img.thumbnail(THUMBNAIL_MAX_SIZE, Image.Resampling.LANCZOS)
        buf = io.BytesIO()
        img.save(buf, format="JPEG", quality=THUMBNAIL_QUALITY, optimize=True)
        return buf.getvalue(), original_w, original_h
    except Exception as e:
        logger.error("thumbnail_generate_error", error=str(e))
        return None


@celery_app.task(bind=True, max_retries=2, default_retry_delay=30)
def generate_attachment_thumbnail(self, attachment_id: str):
    session: Session = SyncSessionLocal()
    try:
        att = session.execute(
            select(Attachment).where(Attachment.id == uuid.UUID(attachment_id))
        ).scalar_one_or_none()
        if att is None:
            logger.info("attachment_thumb_skip_not_found", attachment_id=attachment_id)
            return

        if att.file_kind != "image":
            att.status = "ready"
            session.commit()
            return

        data = s3_sync_service.download_object_bytes(att.storage_key)
        if data is None:
            logger.warning("attachment_thumb_no_data", attachment_id=attachment_id)
            att.status = "ready"
            session.commit()
            return

        result = _generate_image_thumbnail(data)
        if result is None:
            att.status = "ready"
            session.commit()
            return

        thumb_bytes, orig_w, orig_h = result
        thumb_key = _thumbnail_key_for(att.storage_key)

        uploaded = s3_sync_service.upload_bytes(
            thumb_key, thumb_bytes, content_type="image/jpeg"
        )
        if uploaded:
            att.thumbnail_key = thumb_key
        att.width = orig_w
        att.height = orig_h
        att.status = "ready"
        session.commit()

        logger.info(
            "attachment_thumb_generated",
            attachment_id=attachment_id,
            thumb_size=len(thumb_bytes),
        )

        # WS broadcast о готовности превью
        try:
            _publish_attachment_ready(att)
        except Exception as e:
            logger.error("attachment_ws_publish_error", error=str(e))

    except Exception as exc:
        logger.error("attachment_thumb_task_error", error=str(exc))
        session.rollback()
        raise self.retry(exc=exc)
    finally:
        session.close()


def _publish_attachment_ready(att: Attachment):
    """Publish via Redis pubsub (sync) to notify all WS subscribers of the chat."""
    if att.chat_id is None or att.message_id is None:
        return

    import json
    from app.services.redis_service import get_sync_redis

    redis = get_sync_redis()
    event = {
        "type": "attachment_ready",
        "data": {
            "chat_id": str(att.chat_id),
            "message_id": str(att.message_id),
            "attachment_id": str(att.id),
            "has_thumbnail": att.thumbnail_key is not None,
            "width": att.width,
            "height": att.height,
        },
    }
    channel = f"chat:{att.chat_id}"
    redis.publish(channel, json.dumps(event, default=str))