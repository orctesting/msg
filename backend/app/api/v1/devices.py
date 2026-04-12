from fastapi import APIRouter, Depends
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.device import Device
from app.db.models.push_token import PushToken
from app.core.dependencies import get_current_user
from app.core.exceptions import UnauthorizedException, NotFoundException
from app.api.schemas.device import PushTokenRegisterIn, PushTokenRegisterOut
from app.api.schemas.base import OkResponse

router = APIRouter(prefix="/devices", tags=["devices"])


async def _get_current_device(
    current_user: User,
    session: AsyncSession,
) -> Device:
    current_device_id = getattr(current_user, "current_device_id", None)
    if current_device_id is None:
        raise UnauthorizedException("Device context missing")

    device_result = await session.execute(
        select(Device).where(
            Device.id == current_device_id,
            Device.user_id == current_user.id,
        )
    )
    device = device_result.scalar_one_or_none()
    if device is None:
        raise UnauthorizedException("Device not found")

    return device


@router.post("/push-token", response_model=PushTokenRegisterOut)
async def register_push_token(
    body: PushTokenRegisterIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    device = await _get_current_device(current_user, session)

    existing_by_token = await session.execute(
        select(PushToken).where(
            PushToken.token == body.token,
            PushToken.token_type == body.token_type,
        )
    )
    push_token = existing_by_token.scalar_one_or_none()

    if push_token is not None:
        push_token.device_id = device.id
        push_token.is_valid = True
        if hasattr(push_token, "failure_count"):
            push_token.failure_count = 0
        if hasattr(push_token, "last_failure_reason"):
            push_token.last_failure_reason = None
        await session.commit()
        await session.refresh(push_token)
        return PushTokenRegisterOut(id=push_token.id)

    existing_for_device = await session.execute(
        select(PushToken).where(
            PushToken.device_id == device.id,
            PushToken.token_type == body.token_type,
        )
    )
    push_token = existing_for_device.scalar_one_or_none()

    if push_token is not None:
        push_token.token = body.token
        push_token.is_valid = True
        if hasattr(push_token, "failure_count"):
            push_token.failure_count = 0
        if hasattr(push_token, "last_failure_reason"):
            push_token.last_failure_reason = None
        await session.commit()
        await session.refresh(push_token)
        return PushTokenRegisterOut(id=push_token.id)

    push_token = PushToken(
        device_id=device.id,
        token=body.token,
        token_type=body.token_type,
        is_valid=True,
    )
    if hasattr(push_token, "failure_count"):
        push_token.failure_count = 0

    session.add(push_token)
    await session.commit()
    await session.refresh(push_token)

    return PushTokenRegisterOut(id=push_token.id)


@router.delete("/push-token", response_model=OkResponse)
async def unregister_push_token(
    body: PushTokenRegisterIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    device = await _get_current_device(current_user, session)

    result = await session.execute(
        select(PushToken).where(
            PushToken.device_id == device.id,
            PushToken.token == body.token,
            PushToken.token_type == body.token_type,
        )
    )
    push_token = result.scalar_one_or_none()

    if push_token is None:
        raise NotFoundException("Push token not found")

    push_token.is_valid = False
    await session.commit()

    return OkResponse(detail="Push token unregistered")