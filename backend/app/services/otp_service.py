import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select, func
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.config import settings
from app.db.models.otp_session import OTPSession
from app.core.exceptions import RateLimitException, NotFoundException, UnauthorizedException

logger = structlog.get_logger()


class OTPService:
    def __init__(self, session: AsyncSession):
        self.session = session

    async def request_otp(self, phone: str) -> OTPSession:
        # Rate limit check
        one_hour_ago = datetime.now(timezone.utc) - timedelta(hours=1)
        result = await self.session.execute(
            select(func.count(OTPSession.id)).where(
                OTPSession.phone == phone,
                OTPSession.created_at > one_hour_ago,
            )
        )
        count = result.scalar()
        if count >= settings.otp_max_requests_per_hour:
            raise RateLimitException("Too many OTP requests. Try again later.")

        # Cooldown check
        cooldown_ago = datetime.now(timezone.utc) - timedelta(
            seconds=settings.otp_request_cooldown_seconds
        )
        result = await self.session.execute(
            select(OTPSession).where(
                OTPSession.phone == phone,
                OTPSession.created_at > cooldown_ago,
            ).order_by(OTPSession.created_at.desc()).limit(1)
        )
        recent = result.scalar_one_or_none()
        if recent:
            raise RateLimitException("Please wait before requesting a new code.")

        # TODO: Integrate with Multifactor API
        # For now, create a stub OTP session
        request_id = f"stub_{uuid.uuid4().hex}"

        otp_session = OTPSession(
            phone=phone,
            request_id=request_id,
            expires_at=datetime.now(timezone.utc) + timedelta(minutes=5),
        )
        self.session.add(otp_session)
        await self.session.commit()

        logger.info("otp_requested", phone=phone, session_id=str(otp_session.id))
        return otp_session

    async def verify_otp(self, otp_session_id: uuid.UUID, code: str) -> str:
        result = await self.session.execute(
            select(OTPSession).where(OTPSession.id == otp_session_id)
        )
        otp_session = result.scalar_one_or_none()

        if otp_session is None:
            raise NotFoundException("OTP session not found")

        if otp_session.is_verified:
            raise UnauthorizedException("OTP already used")

        if otp_session.expires_at < datetime.now(timezone.utc):
            raise UnauthorizedException("OTP expired")

        if otp_session.attempts >= settings.otp_max_verify_attempts:
            raise RateLimitException("Too many verification attempts")

        otp_session.attempts += 1

        # TODO: Verify with Multifactor API
        # STUB: accept code "0000" in development
        if settings.environment == "development":
            if code != "0000":
                await self.session.commit()
                raise UnauthorizedException("Invalid OTP code")
        else:
            # Real Multifactor verification here
            pass

        otp_session.is_verified = True
        await self.session.commit()

        logger.info("otp_verified", phone=otp_session.phone, session_id=str(otp_session.id))
        return otp_session.phone