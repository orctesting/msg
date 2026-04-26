import asyncio
import structlog

from sqlalchemy import select

from app.config import settings
from app.db.session import async_session_factory
import app.db.models  # noqa — загружаем все модели
from app.db.models.user import User

logger = structlog.get_logger()


async def seed_admin():
    from app.utils.username import generate_unique_username

    async with async_session_factory() as session:
        result = await session.execute(
            select(User).where(User.phone == settings.admin_phone)
        )
        admin = result.scalar_one_or_none()

        if admin is None:
            username = await generate_unique_username(
                session, settings.admin_display_name, None, fallback_id="admin"
            )
            admin = User(
                phone=settings.admin_phone,
                display_name=settings.admin_display_name,
                username=username,
                role="admin",
            )
            session.add(admin)
            await session.commit()
            logger.info("admin_seeded", phone=settings.admin_phone, username=username)
        else:
            logger.info("admin_already_exists", phone=settings.admin_phone)


if __name__ == "__main__":
    asyncio.run(seed_admin())