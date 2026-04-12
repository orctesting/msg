from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends
from sqlalchemy import select, update
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.device import Device
from app.db.models.refresh_token import RefreshToken
from app.core.security import create_access_token, create_refresh_token, decode_token, hash_token
from app.core.dependencies import get_current_user
from app.core.exceptions import UnauthorizedException, NotFoundException
from app.api.schemas.auth import (
    OTPRequestIn,
    OTPRequestOut,
    OTPVerifyIn,
    OTPVerifyOut,
    TokenRefreshIn,
    TokenRefreshOut,
)
from app.api.schemas.base import OkResponse
from app.services.otp_service import OTPService

logger = structlog.get_logger()
router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/request-otp", response_model=OTPRequestOut)
async def request_otp(
    body: OTPRequestIn,
    session: AsyncSession = Depends(get_async_session),
):
    otp_service = OTPService(session)
    otp_session = await otp_service.request_otp(body.phone)
    return OTPRequestOut(otp_session_id=otp_session.id)


@router.post("/verify-otp", response_model=OTPVerifyOut)
async def verify_otp(
    body: OTPVerifyIn,
    session: AsyncSession = Depends(get_async_session),
):
    otp_service = OTPService(session)
    verified_phone = await otp_service.verify_otp(body.phone, body.code)

    result = await session.execute(
        select(User).where(User.phone == verified_phone, User.is_active == True)
    )
    user = result.scalar_one_or_none()
    if user is None:
        raise NotFoundException("User not found or inactive")

    result = await session.execute(
        select(Device).where(
            Device.user_id == user.id,
            Device.device_id == body.device_id,
        )
    )
    device = result.scalar_one_or_none()

    if device is None:
        device = Device(
            user_id=user.id,
            device_id=body.device_id,
            platform=body.platform,
            app_version=body.app_version,
            os_version=body.os_version,
            is_active=True,
        )
        session.add(device)
        await session.flush()
    else:
        device.platform = body.platform
        device.app_version = body.app_version
        device.os_version = body.os_version
        device.is_active = True
        await session.flush()

    access_token = create_access_token(user.id, user.role, device.id)
    raw_refresh, token_hash = create_refresh_token(user.id)

    session.add(
        RefreshToken(
            user_id=user.id,
            device_id=device.id,
            token_hash=token_hash,
            expires_at=datetime.now(timezone.utc) + timedelta(days=30),
        )
    )
    await session.commit()

    logger.info("user_authenticated", user_id=str(user.id), device_id=str(device.id))

    return OTPVerifyOut(
        access_token=access_token,
        refresh_token=raw_refresh,
        user_id=user.id,
        device_id=device.id,
    )


@router.post("/refresh", response_model=TokenRefreshOut)
async def refresh_tokens(
    body: TokenRefreshIn,
    session: AsyncSession = Depends(get_async_session),
):
    payload = decode_token(body.refresh_token)
    if payload is None or payload.get("type") != "refresh":
        raise UnauthorizedException("Invalid refresh token")

    token_hash = hash_token(body.refresh_token)
    result = await session.execute(
        select(RefreshToken).where(
            RefreshToken.token_hash == token_hash,
            RefreshToken.is_revoked == False,
        )
    )
    stored_token = result.scalar_one_or_none()

    if stored_token is None:
        raise UnauthorizedException("Refresh token not found or revoked")

    if stored_token.expires_at < datetime.now(timezone.utc):
        raise UnauthorizedException("Refresh token expired")

    stored_token.is_revoked = True

    result = await session.execute(
        select(User).where(User.id == stored_token.user_id, User.is_active == True)
    )
    user = result.scalar_one_or_none()
    if user is None:
        raise UnauthorizedException("User not found or inactive")

    if stored_token.device_id is None:
        raise UnauthorizedException("Device not found")

    access_token = create_access_token(user.id, user.role, stored_token.device_id)
    raw_refresh, new_token_hash = create_refresh_token(user.id)

    session.add(
        RefreshToken(
            user_id=user.id,
            device_id=stored_token.device_id,
            token_hash=new_token_hash,
            expires_at=datetime.now(timezone.utc) + timedelta(days=30),
        )
    )
    await session.commit()

    return TokenRefreshOut(access_token=access_token, refresh_token=raw_refresh)


@router.post("/logout", response_model=OkResponse)
async def logout(
    body: TokenRefreshIn,
    current_user: User = Depends(get_current_user),
    session: AsyncSession = Depends(get_async_session),
):
    token_hash = hash_token(body.refresh_token)
    result = await session.execute(
        select(RefreshToken).where(
            RefreshToken.token_hash == token_hash,
            RefreshToken.user_id == current_user.id,
            RefreshToken.is_revoked == False,
        )
    )
    stored_token = result.scalar_one_or_none()

    if stored_token is not None:
        stored_token.is_revoked = True
        if stored_token.device_id:
            await session.execute(
                update(Device).where(Device.id == stored_token.device_id).values(is_active=False)
            )
        await session.commit()
        logger.info("user_logged_out", user_id=str(current_user.id))

    return OkResponse(detail="Logged out")