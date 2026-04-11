import uuid

from fastapi import Depends
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_async_session
from app.db.models.user import User
from app.core.security import decode_token
from app.core.exceptions import UnauthorizedException, ForbiddenException

bearer_scheme = HTTPBearer()


async def get_current_user(
    credentials: HTTPAuthorizationCredentials = Depends(bearer_scheme),
    session: AsyncSession = Depends(get_async_session),
) -> User:
    payload = decode_token(credentials.credentials)
    if payload is None or payload.get("type") != "access":
        raise UnauthorizedException("Invalid access token")

    user_id = payload.get("sub")
    if user_id is None:
        raise UnauthorizedException("Invalid token payload")

    result = await session.execute(
        select(User).where(User.id == uuid.UUID(user_id), User.is_active == True)
    )
    user = result.scalar_one_or_none()
    if user is None:
        raise UnauthorizedException("User not found or inactive")

    return user


async def get_current_admin(
    user: User = Depends(get_current_user),
) -> User:
    if user.role != "admin":
        raise ForbiddenException("Admin access required")
    return user