import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession
import structlog

from app.config import settings
from app.db.models.user import User
from app.db.models.otp_session import OTPSession
from app.core.exceptions import RateLimitException, NotFoundException, UnauthorizedException
from app.services.redis_service import get_redis

logger = structlog.get_logger()


class OTPService:
    def __init__(self, session: AsyncSession):
        self.session = session
        self.redis = get_redis()

    async def request_otp(self, phone: str) -> OTPSession:
        user_result = await self.session.execute(
            select(User).where(User.phone == phone, User.is_active == True)
        )
        user = user_result.scalar_one_or_none()
        if user is None:
            raise NotFoundException("User not found or inactive")

        cooldown_key = f"otp:cooldown:{phone}"
        hourly_key = f"otp:hour:{phone}"

        if await self.redis.exists(cooldown_key):
            raise RateLimitException("Please wait before requesting a new code.")

        current_hour_count = await self.redis.incr(hourly_key)
        if current_hour_count == 1:
            await self.redis.expire(hourly_key, 3600)
        if current_hour_count > settings.otp_max_requests_per_hour:
            raise RateLimitException("Too many OTP requests. Try again later.")

        await self.redis.setex(cooldown_key, settings.otp_request_cooldown_seconds, "1")

        session_id = f"stub_{uuid.uuid4().hex}"

        otp_session = OTPSession(
            phone=phone,
            session_id=session_id,
            status="pending",
            attempts=0,
            max_attempts=settings.otp_max_verify_attempts,
            expires_at=datetime.now(timezone.utc) + timedelta(seconds=settings.otp_session_ttl_seconds),
        )
        self.session.add(otp_session)
        await self.session.commit()
        await self.session.refresh(otp_session)

        logger.info("otp_requested", phone=phone, otp_session_id=str(otp_session.id))
        return otp_session

    async def verify_otp(self, phone: str, code: str) -> str:
        result = await self.session.execute(
            select(OTPSession)
            .where(OTPSession.phone == phone)
            .order_by(OTPSession.created_at.desc())
            .limit(1)
        )
        otp_session = result.scalar_one_or_none()

        if otp_session is None:
            raise NotFoundException("OTP session not found")

        if otp_session.status == "verified":
            raise UnauthorizedException("OTP already used")

        if otp_session.status == "expired" or otp_session.expires_at < datetime.now(timezone.utc):
            otp_session.status = "expired"
            await self.session.commit()
            raise UnauthorizedException("OTP expired")

        if otp_session.attempts >= otp_session.max_attempts:
            raise RateLimitException("Too many verification attempts")

        otp_session.attempts += 1

        if settings.environment == "development":
            if code != "0000":
                await self.session.commit()
                raise UnauthorizedException("Invalid OTP code")
        else:
            pass

        otp_session.status = "verified"
        await self.session.commit()

        logger.info("otp_verified", phone=phone, otp_session_id=str(otp_session.id))
        return otp_session.phone