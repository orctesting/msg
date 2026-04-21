from fastapi import Depends
from sqlalchemy.ext.asyncio import AsyncSession

from app.db.session import get_async_session


async def get_db(session: AsyncSession = Depends(get_async_session)):
    yield session