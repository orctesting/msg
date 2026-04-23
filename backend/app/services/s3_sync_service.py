import io
from typing import Any

import boto3
from botocore.config import Config as BotoConfig
import structlog

from app.config import settings

logger = structlog.get_logger()

_client = None


def _get_client():
    global _client
    if _client is None:
        _client = boto3.client(
            "s3",
            endpoint_url=settings.s3_endpoint_url,
            aws_access_key_id=settings.s3_access_key,
            aws_secret_access_key=settings.s3_secret_key,
            region_name=settings.s3_region,
            config=BotoConfig(signature_version="s3v4", s3={"addressing_style": "path"}),
        )
    return _client


def download_object_bytes(storage_key: str) -> bytes | None:
    try:
        obj = _get_client().get_object(Bucket=settings.s3_bucket, Key=storage_key)
        return obj["Body"].read()
    except Exception as e:
        logger.error("s3_sync_download_error", key=storage_key, error=str(e))
        return None


def upload_bytes(storage_key: str, data: bytes, content_type: str) -> bool:
    try:
        _get_client().put_object(
            Bucket=settings.s3_bucket,
            Key=storage_key,
            Body=data,
            ContentType=content_type,
        )
        return True
    except Exception as e:
        logger.error("s3_sync_upload_error", key=storage_key, error=str(e))
        return False


def head_object(storage_key: str) -> dict | None:
    try:
        return _get_client().head_object(Bucket=settings.s3_bucket, Key=storage_key)
    except Exception:
        return None