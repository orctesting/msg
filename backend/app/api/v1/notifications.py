import uuid
from datetime import datetime, timezone

from fastapi import APIRouter, Depends, Path
from sqlalchemy import select, delete
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.notification_settings import (
    NotificationSettings,
    NotificationChatWhitelist,
)
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException, ConflictException
from app.api.schemas.notifications import (
    NotificationSettingsItem,
    NotificationSettingsListOut,
    UpdateNotificationSettingsIn,
    VALID_PLATFORMS,
)

logger = structlog.get_logger()
router = APIRouter(prefix="/me/notification-settings", tags=["notifications"])


async def _get_or_create_settings(
    session: AsyncSession,
    user_id: uuid.UUID,
    platform: str,
) -> NotificationSettings:
    res = await session.execute(
        select(NotificationSettings).where(
            NotificationSettings.user_id == user_id,
            NotificationSettings.platform == platform,
        )
    )
    s = res.scalar_one_or_none()
    if s is None:
        s = NotificationSettings(user_id=user_id, platform=platform, mode="all")
        session.add(s)
        await session.flush()
    return s


@router.get("", response_model=NotificationSettingsListOut)
async def list_my_settings(
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    res = await session.execute(
        select(NotificationSettings).where(NotificationSettings.user_id == current_user.id)
    )
    settings_rows = list(res.scalars().all())

    wl_res = await session.execute(
        select(NotificationChatWhitelist).where(
            NotificationChatWhitelist.user_id == current_user.id
        )
    )
    wl_rows = list(wl_res.scalars().all())
    wl_map: dict[str, list[uuid.UUID]] = {}
    for w in wl_rows:
        wl_map.setdefault(w.platform, []).append(w.chat_id)

    items: list[NotificationSettingsItem] = []
    seen_platforms = set()
    for s in settings_rows:
        items.append(NotificationSettingsItem(
            platform=s.platform,
            mode=s.mode,
            whitelist_chat_ids=wl_map.get(s.platform, []),
        ))
        seen_platforms.add(s.platform)

    # Defaults для платформ, для которых ещё нет записи
    for p in VALID_PLATFORMS:
        if p not in seen_platforms:
            items.append(NotificationSettingsItem(
                platform=p,
                mode="all",
                whitelist_chat_ids=[],
            ))

    return NotificationSettingsListOut(items=items)


@router.put("/{platform}", response_model=NotificationSettingsItem)
async def update_my_settings(
    body: UpdateNotificationSettingsIn,
    platform: str = Path(...),
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    if platform not in VALID_PLATFORMS:
        raise ConflictException(f"platform must be one of {VALID_PLATFORMS}")

    settings_row = await _get_or_create_settings(session, current_user.id, platform)
    settings_row.mode = body.mode
    settings_row.updated_at = datetime.now(timezone.utc)

    # Сначала чистим whitelist для (user, platform)
    await session.execute(
        delete(NotificationChatWhitelist).where(
            NotificationChatWhitelist.user_id == current_user.id,
            NotificationChatWhitelist.platform == platform,
        )
    )

    final_chat_ids: list[uuid.UUID] = []
    if body.mode == "whitelist" and body.chat_ids:
        # Проверяем, что юзер — член всех указанных чатов
        member_res = await session.execute(
            select(ChatMember.chat_id).where(
                ChatMember.user_id == current_user.id,
                ChatMember.chat_id.in_(body.chat_ids),
            )
        )
        valid_ids = {row[0] for row in member_res.all()}
        for cid in body.chat_ids:
            if cid in valid_ids:
                session.add(NotificationChatWhitelist(
                    user_id=current_user.id,
                    platform=platform,
                    chat_id=cid,
                ))
                final_chat_ids.append(cid)

    await session.commit()
    await session.refresh(settings_row)

    return NotificationSettingsItem(
        platform=settings_row.platform,
        mode=settings_row.mode,
        whitelist_chat_ids=final_chat_ids,
    )