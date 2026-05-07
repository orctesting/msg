import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Request
from sqlalchemy import select, desc
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.user_avatar import UserAvatar
from app.db.models.attachment import Attachment
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ConflictException, ForbiddenException
from app.api.schemas.profile import (
    MeOut,
    MeUpdateIn,
    AvatarOut,
    AvatarListOut,
    AvatarCreateIn,
    SetPrimaryAvatarIn,
)
from app.api.schemas.base import OkResponse
from app.services import s3_service, avatar_service
from app.utils.public_url import derive_s3_public_base

logger = structlog.get_logger()
router = APIRouter(prefix="/me", tags=["me"])


async def _build_me_out(
    user: User,
    session: AsyncSession,
    public_base: str | None = None,
) -> MeOut:
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

    return MeOut(
        id=user.id,
        phone=user.phone,
        username=user.username,
        display_name=user.display_name,
        first_name=user.first_name,
        last_name=user.last_name,
        birth_date=user.birth_date,
        bio=user.bio,
        email=user.email,
        role=user.role,
        primary_avatar_attachment_id=user.primary_avatar_attachment_id,
        primary_avatar_thumb_attachment_id=user.primary_avatar_thumb_attachment_id,
        primary_avatar_url=primary_url,
        primary_avatar_thumb_url=primary_thumb_url,
        created_at=user.created_at,
    )


async def _build_avatar_out(
    avatar: UserAvatar,
    session: AsyncSession,
    public_base: str | None = None,
) -> AvatarOut:
    full_url = None
    crop_url = None
    full_res = await session.execute(
        select(Attachment).where(Attachment.id == avatar.full_attachment_id)
    )
    full_att = full_res.scalar_one_or_none()
    if full_att and full_att.status in ("uploaded", "ready"):
        try:
            full_url = await s3_service.generate_presigned_download_url(
                full_att.storage_key, public_base_override=public_base
            )
        except Exception:
            pass

    crop_res = await session.execute(
        select(Attachment).where(Attachment.id == avatar.crop_attachment_id)
    )
    crop_att = crop_res.scalar_one_or_none()
    if crop_att and crop_att.status in ("uploaded", "ready"):
        try:
            crop_url = await s3_service.generate_presigned_download_url(
                crop_att.storage_key, public_base_override=public_base
            )
        except Exception:
            pass

    return AvatarOut(
        id=avatar.id,
        full_attachment_id=avatar.full_attachment_id,
        crop_attachment_id=avatar.crop_attachment_id,
        full_url=full_url,
        crop_url=crop_url,
        created_at=avatar.created_at,
    )


@router.get("", response_model=MeOut)
async def get_me(
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    return await _build_me_out(current_user, session, public_base=derive_s3_public_base(request))


@router.patch("", response_model=MeOut)
async def update_me(
    body: MeUpdateIn,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    if body.username is not None and body.username != current_user.username:
        existing = await session.execute(
            select(User).where(User.username == body.username, User.id != current_user.id)
        )
        if existing.scalar_one_or_none() is not None:
            raise ConflictException("Username already taken")
        current_user.username = body.username

    if body.display_name is not None:
        current_user.display_name = body.display_name.strip()
    if body.first_name is not None:
        current_user.first_name = body.first_name.strip() or None
    if body.last_name is not None:
        current_user.last_name = body.last_name.strip() or None
    if body.birth_date is not None:
        current_user.birth_date = body.birth_date
    if body.bio is not None:
        current_user.bio = body.bio.strip() or None
    if body.email is not None:
        current_user.email = str(body.email).strip() or None

    current_user.updated_at = datetime.now(timezone.utc)
    await session.commit()
    await session.refresh(current_user)
    return await _build_me_out(current_user, session, public_base=derive_s3_public_base(request))


@router.get("/avatars", response_model=AvatarListOut)
async def list_my_avatars(
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(UserAvatar)
        .where(UserAvatar.user_id == current_user.id)
        .order_by(desc(UserAvatar.created_at))
    )
    avatars = list(res.scalars().all())
    public_base = derive_s3_public_base(request)
    out_items = [await _build_avatar_out(a, session, public_base=public_base) for a in avatars]

    primary_id: uuid.UUID | None = None
    if current_user.primary_avatar_attachment_id:
        for a in avatars:
            if a.crop_attachment_id == current_user.primary_avatar_thumb_attachment_id:
                primary_id = a.id
                break

    return AvatarListOut(avatars=out_items, primary_avatar_id=primary_id)


@router.post("/avatars", response_model=AvatarOut, status_code=201)
async def create_avatar(
    body: AvatarCreateIn,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    src_res = await session.execute(
        select(Attachment).where(Attachment.id == body.source_attachment_id)
    )
    source_att = src_res.scalar_one_or_none()
    if source_att is None:
        raise NotFoundException("Source attachment not found")
    if source_att.uploader_user_id != current_user.id:
        raise ForbiddenException("Not your attachment")
    if source_att.file_kind != "image":
        raise ConflictException("Source must be an image")
    if source_att.status not in ("uploaded", "ready"):
        raise ConflictException("Source attachment is not ready")

    # Создаём crop attachment
    try:
        crop_att = await avatar_service.create_crop_attachment(
            session=session,
            user_id=current_user.id,
            source_att=source_att,
            crop_x=body.crop_x,
            crop_y=body.crop_y,
            crop_size=body.crop_size,
        )
    except ValueError as e:
        raise ConflictException(str(e))

    # source становится "ready" (если был uploaded)
    if source_att.status == "uploaded":
        source_att.status = "ready"

    avatar = UserAvatar(
        user_id=current_user.id,
        full_attachment_id=source_att.id,
        crop_attachment_id=crop_att.id,
    )
    session.add(avatar)
    await session.flush()

    # Этот аватар становится primary (последний загруженный)
    current_user.primary_avatar_attachment_id = source_att.id
    current_user.primary_avatar_thumb_attachment_id = crop_att.id
    current_user.updated_at = datetime.now(timezone.utc)

    await session.commit()
    await session.refresh(avatar)

    return await _build_avatar_out(avatar, session, public_base=derive_s3_public_base(request))


@router.post("/avatars/set-primary", response_model=MeOut)
async def set_primary_avatar(
    body: SetPrimaryAvatarIn,
    request: Request,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(UserAvatar).where(
            UserAvatar.id == body.avatar_id,
            UserAvatar.user_id == current_user.id,
        )
    )
    avatar = res.scalar_one_or_none()
    if avatar is None:
        raise NotFoundException("Avatar not found")

    current_user.primary_avatar_attachment_id = avatar.full_attachment_id
    current_user.primary_avatar_thumb_attachment_id = avatar.crop_attachment_id
    current_user.updated_at = datetime.now(timezone.utc)
    await session.commit()
    await session.refresh(current_user)
    return await _build_me_out(current_user, session, public_base=derive_s3_public_base(request))


@router.delete("/avatars/{avatar_id}", response_model=OkResponse)
async def delete_avatar(
    avatar_id: uuid.UUID,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    from app.services.attachment_cleanup import soft_delete_attachment_if_orphan
    from app.db.models.message_attachment_link import MessageAttachmentLink
    from sqlalchemy import func, delete as sa_delete

    res = await session.execute(
        select(UserAvatar).where(
            UserAvatar.id == avatar_id,
            UserAvatar.user_id == current_user.id,
        )
    )
    avatar = res.scalar_one_or_none()
    if avatar is None:
        raise NotFoundException("Avatar not found")

    full_id = avatar.full_attachment_id
    crop_id = avatar.crop_attachment_id
    was_primary = (
        current_user.primary_avatar_attachment_id == full_id
        or current_user.primary_avatar_thumb_attachment_id == crop_id
    )

    # Снимаем primary, если этот аватар был primary
    if was_primary:
        current_user.primary_avatar_attachment_id = None
        current_user.primary_avatar_thumb_attachment_id = None

    # Удаляем UserAvatar
    await session.delete(avatar)
    await session.flush()

    # Для каждого attachment проверяем: используется ли где-то ещё (в других UserAvatar или в сообщениях)
    for att_id in (full_id, crop_id):
        # Другой UserAvatar ссылается?
        ua_res = await session.execute(
            select(func.count(UserAvatar.id)).where(
                (UserAvatar.full_attachment_id == att_id)
                | (UserAvatar.crop_attachment_id == att_id)
            )
        )
        ua_count = ua_res.scalar() or 0
        if ua_count > 0:
            continue

        # Ссылка из сообщения?
        link_res = await session.execute(
            select(func.count(MessageAttachmentLink.id)).where(
                MessageAttachmentLink.attachment_id == att_id
            )
        )
        link_count = link_res.scalar() or 0
        if link_count > 0:
            continue

        # Используется как primary у другого пользователя? (на всякий)
        u_res = await session.execute(
            select(func.count(User.id)).where(
                (User.primary_avatar_attachment_id == att_id)
                | (User.primary_avatar_thumb_attachment_id == att_id)
            )
        )
        u_count = u_res.scalar() or 0
        if u_count > 0:
            continue

        # Сирота — soft-delete в S3 и пометить attachment
        att_obj = await session.get(Attachment, att_id)
        if att_obj is not None and att_obj.status != "deleted":
            new_key = await s3_service.move_to_deleted(att_obj.storage_key)
            if new_key:
                att_obj.storage_key = new_key
            if att_obj.thumbnail_key:
                new_thumb = await s3_service.move_to_deleted(att_obj.thumbnail_key)
                if new_thumb:
                    att_obj.thumbnail_key = new_thumb
            att_obj.status = "deleted"

    # Если удалили primary — назначаем primary последний оставшийся аватар (по дате)
    if was_primary:
        last_res = await session.execute(
            select(UserAvatar)
            .where(UserAvatar.user_id == current_user.id)
            .order_by(desc(UserAvatar.created_at))
            .limit(1)
        )
        last_av = last_res.scalar_one_or_none()
        if last_av is not None:
            current_user.primary_avatar_attachment_id = last_av.full_attachment_id
            current_user.primary_avatar_thumb_attachment_id = last_av.crop_attachment_id

    current_user.updated_at = datetime.now(timezone.utc)
    await session.commit()
    return OkResponse(detail="Deleted")