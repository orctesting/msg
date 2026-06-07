import uuid

from fastapi import APIRouter, Depends, HTTPException, Query, Response, status
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.dependencies import get_db
from app.api.schemas.release import ReleaseLatestOut
from app.db.models.app_release import AppRelease
from app.services import s3_service

router = APIRouter(prefix="/releases", tags=["releases"])

_ALLOWED_PLATFORMS = {"android", "desktop_win", "desktop_mac", "desktop_linux"}


@router.get("/latest", response_model=ReleaseLatestOut, responses={204: {}})
async def get_latest_release(
    platform: str = Query(...),
    channel: str = Query("stable"),
    current_version_code: int = Query(0, ge=0),
    db: AsyncSession = Depends(get_db),
):
    if platform not in _ALLOWED_PLATFORMS:
        raise HTTPException(status_code=400, detail="Invalid platform")

    stmt = (
        select(AppRelease)
        .where(
            AppRelease.platform == platform,
            AppRelease.channel == channel,
            AppRelease.is_published.is_(True),
        )
        .order_by(AppRelease.version_code.desc())
        .limit(1)
    )
    res = await db.execute(stmt)
    rel = res.scalar_one_or_none()

    if rel is None or rel.version_code <= current_version_code:
        return Response(status_code=status.HTTP_204_NO_CONTENT)

    download_url = await s3_service.generate_presigned_download_url(
        rel.storage_key,
        filename=rel.storage_key.split("/")[-1],
        ttl_seconds=86400,
    )

    is_mandatory = (
        rel.min_supported_version_code is not None
        and current_version_code < rel.min_supported_version_code
    )

    return ReleaseLatestOut(
        version_name=rel.version_name,
        version_code=rel.version_code,
        download_url=download_url,
        sha256=rel.sha256,
        file_size_bytes=rel.file_size_bytes,
        release_notes=rel.release_notes,
        is_mandatory=is_mandatory,
    )