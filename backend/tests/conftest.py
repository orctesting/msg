import asyncio
import uuid
from collections.abc import AsyncGenerator

import pytest
import pytest_asyncio
from httpx import ASGITransport, AsyncClient
from sqlalchemy.ext.asyncio import AsyncSession, async_sessionmaker, create_async_engine

from app.main import app
from app.config import settings
from app.db.session import get_async_session
from app.db.models.user import User
from app.db.models.chat import Chat
from app.db.models.chat_member import ChatMember
from app.db.models.device import Device
from app.core.security import create_access_token


test_engine = create_async_engine(settings.database_url, echo=False)
TestSessionLocal = async_sessionmaker(test_engine, class_=AsyncSession, expire_on_commit=False)


@pytest.fixture(scope="session")
def event_loop():
    loop = asyncio.new_event_loop()
    yield loop
    loop.close()


@pytest_asyncio.fixture
async def db_session() -> AsyncGenerator[AsyncSession, None]:
    async with test_engine.connect() as conn:
        trans = await conn.begin()
        session = AsyncSession(bind=conn, expire_on_commit=False)

        async def _override():
            yield session

        app.dependency_overrides[get_async_session] = _override

        yield session

        await trans.rollback()
        await session.close()
        app.dependency_overrides.pop(get_async_session, None)


@pytest_asyncio.fixture
async def client(db_session: AsyncSession) -> AsyncGenerator[AsyncClient, None]:
    async with AsyncClient(
        transport=ASGITransport(app=app),
        base_url="http://test",
    ) as ac:
        yield ac


@pytest_asyncio.fixture
async def test_user(db_session: AsyncSession) -> User:
    user = User(
        phone=f"+7999{uuid.uuid4().hex[:7]}",
        display_name="Test User",
        role="user",
    )
    db_session.add(user)
    await db_session.flush()
    return user


@pytest_asyncio.fixture
async def test_admin(db_session: AsyncSession) -> User:
    admin = User(
        phone=f"+7888{uuid.uuid4().hex[:7]}",
        display_name="Test Admin",
        role="admin",
    )
    db_session.add(admin)
    await db_session.flush()
    return admin


@pytest_asyncio.fixture
async def user_token(test_user: User) -> str:
    return create_access_token(test_user.id, test_user.role)


@pytest_asyncio.fixture
async def admin_token(test_admin: User) -> str:
    return create_access_token(test_admin.id, test_admin.role)


@pytest_asyncio.fixture
async def test_chat(db_session: AsyncSession, test_user: User) -> Chat:
    chat = Chat(name="Test Chat", type="group")
    db_session.add(chat)
    await db_session.flush()

    member = ChatMember(chat_id=chat.id, user_id=test_user.id, role="member")
    db_session.add(member)
    await db_session.flush()
    return chat


@pytest_asyncio.fixture
async def test_device(db_session: AsyncSession, test_user: User) -> Device:
    device = Device(
        user_id=test_user.id,
        device_id="test-device-001",
        platform="android",
    )
    db_session.add(device)
    await db_session.flush()
    return device