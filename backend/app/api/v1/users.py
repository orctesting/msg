import uuid
from fastapi import APIRouter, Depends, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.attachment import Attachment
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException
from app.api.schemas.profile import PublicUserOut
from app.services import s3_service
from app.utils.public_url import derive_s3_public_base

router = APIRouter(prefix="/users", tags=["users"])


@router.get("/{user_id}", response_model=PublicUserOut)
async def get_public_user(
    user_id: uuid.UUID,
    request: Request,
    _: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(User).where(User.id == user_id, User.is_active == True)
    )
    user = res.scalar_one_or_none()
    if user is None:
        raise NotFoundException("User not found")

    public_base = derive_s3_public_base(request)
    primary_url = None
    primary_thumb_url = None

    if user.primary_avatar_attachment_id:
        att_res = await session.execute(
            select(Attachment).where(Attachment.id == user.primary_avatar_attachment_id)
        )
        att = att_res.scalar_one_or_none()
        if att and att.status in ("uploaded", "ready"):
            try:
                primary_url = await s3_service.generate_presigned_download_url(
                    att.storage_key, public_base_override=public_base
                )
            except Exception:
                pass

    if user.primary_avatar_thumb_attachment_id:
        att_res = await session.execute(
            select(Attachment).where(Attachment.id == user.primary_avatar_thumb_attachment_id)
        )
        att = att_res.scalar_one_or_none()
        if att and att.status in ("uploaded", "ready"):
            try:
                primary_thumb_url = await s3_service.generate_presigned_download_url(
                    att.storage_key, public_base_override=public_base
                )
            except Exception:
                pass

    return PublicUserOut(
        id=user.id,
        username=user.username,
        display_name=user.display_name,
        first_name=user.first_name,
        last_name=user.last_name,
        bio=user.bio,
        primary_avatar_url=primary_url,
        primary_avatar_thumb_url=primary_thumb_url,
        created_at=user.created_at,
    )