from app.services.redis_service import get_redis


async def acquire_message_idempotency(key: str, ttl_seconds: int = 86400) -> bool:
    redis = get_redis()
    return bool(await redis.set(f"idempotency:message:{key}", "1", ex=ttl_seconds, nx=True))