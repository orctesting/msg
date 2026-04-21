from httpx import AsyncClient
from sqlalchemy import select

from app.db.models.user import User
from app.db.models.device import Device
from app.core.security import create_access_token


async def create_user_and_login(
    client: AsyncClient,
    phone: str,
    display_name: str = "Test User",
    role: str = "user",
) -> dict:
    """
    Creates a User directly via DB session (bypassing OTP flow) and returns
    a dict with phone, user_id, headers (with bearer token), device_id.
    """
    # Import here to use the same overridden session as fixtures
    from app.db.session import get_async_session
    from app.main import app

    # Grab the overridden session factory
    override = app.dependency_overrides.get(get_async_session)
    if override is None:
        raise RuntimeError("Test session override not installed")

    session_gen = override()
    session = await session_gen.__anext__()

    existing = await session.execute(select(User).where(User.phone == phone))
    user = existing.scalar_one_or_none()
    if user is None:
        user = User(phone=phone, display_name=display_name, role=role)
        session.add(user)
        await session.flush()

    device = Device(
        user_id=user.id,
        device_id=f"test-device-{phone}",
        platform="android",
        is_active=True,
    )
    session.add(device)
    await session.flush()

    token = create_access_token(user.id, user.role, device.id)

    return {
        "phone": phone,
        "user_id": str(user.id),
        "device_id": str(device.id),
        "headers": {"Authorization": f"Bearer {token}"},
    }