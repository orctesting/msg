import io
import uuid
from typing import Tuple

from PIL import Image
import structlog
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.models.attachment import Attachment
from app.services import s3_service
from app.config import settings

logger = structlog.get_logger()

CROP_OUTPUT_SIZE = 512  # финальный размер квадратной обрезки
CROP_QUALITY = 88


async def fetch_object_bytes(storage_key: str) -> bytes | None:
    """Скачивает объект из S3 (через async клиент)."""
    import aioboto3
    from botocore.config import Config as BotoConfig

    session = aioboto3.Session(
        aws_access_key_id=settings.s3_access_key,
        aws_secret_access_key=settings.s3_secret_key,
        region_name=settings.s3_region,
    )
    try:
        async with session.client(
            "s3",
            endpoint_url=settings.s3_endpoint_url,
            config=BotoConfig(signature_version="s3v4", s3={"addressing_style": "path"}),
        ) as s3:
            obj = await s3.get_object(Bucket=settings.s3_bucket, Key=storage_key)
            body = obj["Body"]
            data = await body.read()
            return data
    except Exception as e:
        logger.error("avatar_fetch_object_error", key=storage_key, error=str(e))
        return None


async def upload_bytes_to_s3(storage_key: str, data: bytes, content_type: str) -> bool:
    import aioboto3
    from botocore.config import Config as BotoConfig

    session = aioboto3.Session(
        aws_access_key_id=settings.s3_access_key,
        aws_secret_access_key=settings.s3_secret_key,
        region_name=settings.s3_region,
    )
    try:
        async with session.client(
            "s3",
            endpoint_url=settings.s3_endpoint_url,
            config=BotoConfig(signature_version="s3v4", s3={"addressing_style": "path"}),
        ) as s3:
            await s3.put_object(
                Bucket=settings.s3_bucket,
                Key=storage_key,
                Body=data,
                ContentType=content_type,
            )
        return True
    except Exception as e:
        logger.error("avatar_upload_error", key=storage_key, error=str(e))
        return False


def crop_image_square(
    image_bytes: bytes,
    x: int,
    y: int,
    size: int,
    output_size: int = CROP_OUTPUT_SIZE,
) -> Tuple[bytes, int, int]:
    """Берёт квадратную область (x,y,size) из исходного изображения и ресайзит до output_size."""
    img = Image.open(io.BytesIO(image_bytes))
    img = img.convert("RGB")
    w, h = img.size

    # Ограничиваем рамки
    x = max(0, min(x, w - 1))
    y = max(0, min(y, h - 1))
    right = min(x + size, w)
    bottom = min(y + size, h)
    actual_size = min(right - x, bottom - y)
    if actual_size <= 0:
        raise ValueError("Invalid crop region")

    box = (x, y, x + actual_size, y + actual_size)
    cropped = img.crop(box)
    cropped = cropped.resize((output_size, output_size), Image.Resampling.LANCZOS)

    buf = io.BytesIO()
    cropped.save(buf, format="JPEG", quality=CROP_QUALITY, optimize=True)
    return buf.getvalue(), output_size, output_size


async def create_crop_attachment(
    session: AsyncSession,
    user_id: uuid.UUID,
    source_att: Attachment,
    crop_x: int,
    crop_y: int,
    crop_size: int,
) -> Attachment:
    """
    Скачивает source_att, делает квадратный crop, заливает в S3, создаёт новый Attachment.
    """
    src_bytes = await fetch_object_bytes(source_att.storage_key)
    if src_bytes is None:
        raise ValueError("Cannot read source attachment")

    crop_bytes, w, h = crop_image_square(src_bytes, crop_x, crop_y, crop_size)

    new_att_id = uuid.uuid4()
    storage_key = f"avatars/{user_id}/{new_att_id}_crop.jpg"
    ok = await upload_bytes_to_s3(storage_key, crop_bytes, "image/jpeg")
    if not ok:
        raise ValueError("Failed to upload crop to S3")

    crop_att = Attachment(
        id=new_att_id,
        uploader_user_id=user_id,
        chat_id=None,
        message_id=None,
        storage_key=storage_key,
        original_filename="avatar_crop.jpg",
        mime_type="image/jpeg",
        size_bytes=len(crop_bytes),
        file_kind="image",
        width=w,
        height=h,
        status="ready",
    )
    session.add(crop_att)
    await session.flush()
    return crop_att