import uuid

from fastapi import APIRouter, Depends
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.device import Device
from app.db.models.push_token import PushToken
from app.core.dependencies import get_current_user
from app.core.exceptions import NotFoundException
from app.api.schemas.base import OkResponse

logger = structlog.get_logger()
router = APIRouter(prefix="/push", tags=["push"])


class PushTokenIn(BaseModel):
    device_id: str
    token: str
    token_type: str = "fcm"  # fcm | apns


@router.post("/token", response_model=OkResponse, status_code=201)
async def register_push_token(
    body: PushTokenIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    # Find device
    result = await session.execute(
        select(Device).where(
            Device.user_id == current_user.id,
            Device.device_id == body.device_id,
        )
    )
    device = result.scalar_one_or_none()
    if device is None:
        raise NotFoundException("Device not found. Please authenticate first.")

    # Check if token already exists
    result = await session.execute(
        select(PushToken).where(
            PushToken.device_id == device.id,
            PushToken.token == body.token,
            PushToken.token_type == body.token_type,
        )
    )
    existing = result.scalar_one_or_none()

    if existing:
        existing.is_valid = True
    else:
        # Invalidate old tokens for this device
        result = await session.execute(
            select(PushToken).where(
                PushToken.device_id == device.id,
                PushToken.token_type == body.token_type,
            )
        )
        for old_token in result.scalars().all():
            old_token.is_valid = False

        session.add(PushToken(
            device_id=device.id,
            token=body.token,
            token_type=body.token_type,
        ))

    await session.commit()
    logger.info("push_token_registered", user_id=str(current_user.id), device_id=body.device_id)
    return OkResponse(detail="Push token registered")


@router.delete("/token", response_model=OkResponse)
async def unregister_push_token(
    body: PushTokenIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    result = await session.execute(
        select(Device).where(
            Device.user_id == current_user.id,
            Device.device_id == body.device_id,
        )
    )
    device = result.scalar_one_or_none()
    if device is None:
        raise NotFoundException("Device not found")

    result = await session.execute(
        select(PushToken).where(
            PushToken.device_id == device.id,
            PushToken.token == body.token,
        )
    )
    token = result.scalar_one_or_none()
    if token:
        token.is_valid = False
        await session.commit()

    return OkResponse(detail="Push token unregistered")