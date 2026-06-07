import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Header, HTTPException
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.dependencies import get_db
from app.api.schemas.release import ReleaseCreateIn, ReleaseOut
from app.config import settings
from app.db.models.app_release import AppRelease

router = APIRouter(prefix="/admin/releases", tags=["admin-releases"])


def _require_admin_key(x_api_key: str | None = Header(None, alias="X-API-Key")) -> None:
    expected = settings.releases_admin_api_key
    if not expected or x_api_key != expected:
        raise HTTPException(status_code=403, detail="Forbidden")


@router.post("", response_model=ReleaseOut, dependencies=[Depends(_require_admin_key)])
async def create_release(
    payload: ReleaseCreateIn,
    db: AsyncSession = Depends(get_db),
):
    existing = await db.execute(
        select(AppRelease).where(
            AppRelease.platform == payload.platform,
            AppRelease.channel == payload.channel,
            AppRelease.version_code == payload.version_code,
        )
    )
    if existing.scalar_one_or_none() is not None:
        raise HTTPException(status_code=409, detail="Release already exists")

    rel = AppRelease(
        id=uuid.uuid4(),
        platform=payload.platform,
        channel=payload.channel,
        version_name=payload.version_name,
        version_code=payload.version_code,
        min_supported_version_code=payload.min_supported_version_code,
        storage_key=payload.storage_key,
        file_size_bytes=payload.file_size_bytes,
        sha256=payload.sha256,
        release_notes=payload.release_notes,
        is_published=payload.is_published,
        published_at=datetime.now(timezone.utc) if payload.is_published else None,
    )
    db.add(rel)
    await db.commit()
    await db.refresh(rel)
    return ReleaseOut.model_validate(rel)


@router.post("/{release_id}/publish", response_model=ReleaseOut, dependencies=[Depends(_require_admin_key)])
async def publish_release(
    release_id: uuid.UUID,
    db: AsyncSession = Depends(get_db),
):
    rel = await db.get(AppRelease, release_id)
    if rel is None:
        raise HTTPException(status_code=404, detail="Not found")
    rel.is_published = True
    if rel.published_at is None:
        rel.published_at = datetime.now(timezone.utc)
    await db.commit()
    await db.refresh(rel)
    return ReleaseOut.model_validate(rel)