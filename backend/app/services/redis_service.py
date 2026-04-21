import redis
import redis.asyncio as redis_async

from app.config import settings

_async_redis: redis_async.Redis | None = None
_sync_redis: redis.Redis | None = None


def get_redis() -> redis_async.Redis:
    global _async_redis
    if _async_redis is None:
        _async_redis = redis_async.from_url(settings.redis_url, decode_responses=True)
    return _async_redis


def get_sync_redis() -> redis.Redis:
    global _sync_redis
    if _sync_redis is None:
        _sync_redis = redis.from_url(settings.redis_url, decode_responses=True)
    return _sync_redis