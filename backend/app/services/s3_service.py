import uuid
from typing import Any

import aioboto3
from botocore.config import Config as BotoConfig
import structlog

from app.config import settings

logger = structlog.get_logger()

_session: aioboto3.Session | None = None


def _get_session() -> aioboto3.Session:
    global _session
    if _session is None:
        _session = aioboto3.Session(
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            region_name=settings.s3_region,
        )
    return _session


def _client_kwargs(public: bool = False) -> dict[str, Any]:
    endpoint = settings.s3_public_endpoint_url if public else settings.s3_endpoint_url
    return {
        "service_name": "s3",
        "endpoint_url": endpoint,
        "config": BotoConfig(signature_version="s3v4", s3={"addressing_style": "path"}),
    }


def build_storage_key(chat_id: uuid.UUID | str | None, attachment_id: uuid.UUID, filename: str) -> str:
    safe = filename.replace("/", "_").replace("\\", "_")[:200]
    prefix = f"chat_{chat_id}" if chat_id else "unassigned"
    return f"{prefix}/{attachment_id}_{safe}"


async def generate_presigned_upload_url(
    storage_key: str,
    content_type: str,
    size_bytes: int,
    ttl_seconds: int | None = None,
) -> str:
    ttl = ttl_seconds or settings.attachment_upload_url_ttl_seconds
    session = _get_session()
    async with session.client(**_client_kwargs(public=True)) as s3:
        url = await s3.generate_presigned_url(
            ClientMethod="put_object",
            Params={
                "Bucket": settings.s3_bucket,
                "Key": storage_key,
                "ContentType": content_type,
            },
            ExpiresIn=ttl,
            HttpMethod="PUT",
        )
    return url


async def generate_presigned_download_url(
    storage_key: str,
    filename: str | None = None,
    ttl_seconds: int | None = None,
) -> str:
    ttl = ttl_seconds or settings.attachment_download_url_ttl_seconds
    session = _get_session()
    params: dict[str, Any] = {"Bucket": settings.s3_bucket, "Key": storage_key}
    if filename:
        params["ResponseContentDisposition"] = f'attachment; filename="{filename}"'
    async with session.client(**_client_kwargs(public=True)) as s3:
        url = await s3.generate_presigned_url(
            ClientMethod="get_object",
            Params=params,
            ExpiresIn=ttl,
        )
    return url


async def head_object(storage_key: str) -> dict | None:
    session = _get_session()
    try:
        async with session.client(**_client_kwargs(public=False)) as s3:
            return await s3.head_object(Bucket=settings.s3_bucket, Key=storage_key)
    except Exception as e:
        logger.info("s3_head_object_miss", key=storage_key, error=str(e))
        return None


async def delete_object(storage_key: str) -> None:
    session = _get_session()
    try:
        async with session.client(**_client_kwargs(public=False)) as s3:
            await s3.delete_object(Bucket=settings.s3_bucket, Key=storage_key)
    except Exception as e:
        logger.error("s3_delete_object_error", key=storage_key, error=str(e))