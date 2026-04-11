import asyncio
import structlog

from sqlalchemy import select

from app.config import settings
from app.db.session import async_session_factory
from app.db.models.user import User

logger = structlog.get_logger()


async def seed_admin():
    async with async_session_factory() as session:
        result = await session.execute(
            select(User).where(User.phone == settings.admin_phone)
        )
        admin = result.scalar_one_or_none()

        if admin is None:
            admin = User(
                phone=settings.admin_phone,
                display_name=settings.admin_display_name,
                role="admin",
            )
            session.add(admin)
            await session.commit()
            logger.info("admin_seeded", phone=settings.admin_phone)
        else:
            logger.info("admin_already_exists", phone=settings.admin_phone)


if __name__ == "__main__":
    asyncio.run(seed_admin())