# backend/app/workers/tasks/push.py
import uuid
from datetime import datetime, timezone

from app.workers.celery_app import celery_app
from sqlalchemy import create_engine, select
from sqlalchemy.orm import Session, sessionmaker
import structlog

from app.config import settings
from app.db.models.message import Message
from app.db.models.device import Device
from app.db.models.push_token import PushToken
from app.db.models.push_log import PushLog

logger = structlog.get_logger()

_sync_engine = create_engine(settings.database_url.replace("+asyncpg", ""), future=True)
SyncSessionLocal = sessionmaker(bind=_sync_engine, autoflush=False, autocommit=False)


def _sync_db_session():
    return SyncSessionLocal()


def _make_push_idempotency_key(message_id: uuid.UUID, device_id: uuid.UUID, attempt_number: int) -> uuid.UUID:
    return uuid.uuid5(
        uuid.NAMESPACE_URL,
        f"push:{message_id}:{device_id}:{attempt_number}",
    )


# FCM ошибки, которые означают "токен мёртв навсегда" — не ретраим, инвалидируем токен
PERMANENT_FCM_ERRORS = (
    "UnregisteredError",
    "InvalidArgumentError",
    "SenderIdMismatchError",
    "ThirdPartyAuthError",
)

# Временные ошибки — имеет смысл ретраить
TRANSIENT_FCM_ERRORS = (
    "UnavailableError",
    "InternalError",
    "QuotaExceededError",
)


def _classify_fcm_error(exc: Exception) -> str:
    """Returns 'permanent', 'transient', or 'unknown'."""
    name = type(exc).__name__
    if name in PERMANENT_FCM_ERRORS:
        return "permanent"
    if name in TRANSIENT_FCM_ERRORS:
        return "transient"
    # По строке пытаемся угадать
    msg = str(exc).lower()
    if "unregistered" in msg or "not found" in msg or "invalid" in msg:
        return "permanent"
    if "unavailable" in msg or "timeout" in msg or "503" in msg or "500" in msg:
        return "transient"
    return "unknown"


def _send_to_provider(token: str, token_type: str, payload: dict) -> dict:
    if token_type == "fcm":
        from app.services.fcm_service import send_fcm_push
        msg_id = send_fcm_push(
            token=token,
            title=payload.get("title", "New message"),
            body=payload.get("body", ""),
            data={
                "chat_id": payload.get("chat_id", ""),
                "message_id": payload.get("message_id", ""),
            },
        )
        return {
            "status": "sent",
            "provider_message_id": msg_id or "",
            "provider": "fcm",
        }

    return {
        "status": "sent",
        "provider_message_id": str(uuid.uuid4()),
        "provider": token_type,
    }


@celery_app.task(bind=True, max_retries=0)
def send_push_notification(self, message_id: str, recipient_user_ids: list[str]):
    """
    Master task: enqueues per-device subtasks ONLY for devices that have
    at least one valid push token. Devices without valid tokens are skipped
    entirely (no log, no resources wasted).
    """
    session: Session = _sync_db_session()
    try:
        message = session.execute(
            select(Message).where(Message.id == uuid.UUID(message_id))
        ).scalar_one_or_none()
        if message is None:
            logger.error("push_message_not_found", message_id=message_id)
            return

        # Фильтр: только активные девайсы с минимум одним валидным push-токеном
        has_valid_token = (
            select(PushToken.id)
            .where(
                PushToken.device_id == Device.id,
                PushToken.is_valid.is_(True),
            )
            .exists()
        )

        devices = session.execute(
            select(Device).where(
                Device.user_id.in_([uuid.UUID(uid) for uid in recipient_user_ids]),
                Device.is_active.is_(True),
                has_valid_token,
            )
        ).scalars().all()

        enqueued = 0
        for device in devices:
            send_push_to_device.delay(str(message.id), str(device.id))
            enqueued += 1

        logger.info(
            "push_dispatch",
            message_id=message_id,
            recipients=len(recipient_user_ids),
            enqueued=enqueued,
        )

    except Exception as exc:
        logger.error("push_master_task_error", message_id=message_id, error=str(exc))
        session.rollback()
        raise
    finally:
        session.close()


@celery_app.task(bind=True, max_retries=3, default_retry_delay=30)
def send_push_to_device(self, message_id: str, device_id: str):
    """
    Sends a push to ONE device. Retries only on transient errors.
    Permanent errors (invalid token) -> mark token invalid, no retry.
    """
    session: Session = _sync_db_session()
    try:
        message = session.execute(
            select(Message).where(Message.id == uuid.UUID(message_id))
        ).scalar_one_or_none()
        if message is None:
            logger.error("push_message_not_found", message_id=message_id)
            return

        device = session.execute(
            select(Device).where(Device.id == uuid.UUID(device_id))
        ).scalar_one_or_none()
        if device is None or not device.is_active:
            logger.info("push_device_not_active", device_id=device_id)
            return

        attempt_number = int(self.request.retries) + 1

        push_token = session.execute(
            select(PushToken).where(
                PushToken.device_id == device.id,
                PushToken.is_valid.is_(True),
            )
        ).scalar_one_or_none()

        log_idempotency_key = _make_push_idempotency_key(
            message.id, device.id, attempt_number,
        )

        # Idempotency check — если уже отправляли на этой попытке, не повторяем
        existing_log = session.execute(
            select(PushLog).where(PushLog.idempotency_key == log_idempotency_key)
        ).scalar_one_or_none()
        if existing_log is not None:
            logger.info("push_already_logged", message_id=message_id, device_id=device_id, attempt=attempt_number)
            return

        # Нет валидного токена → skipped, без ретрая
        if push_token is None:
            log = PushLog(
                message_id=message.id,
                device_id=device.id,
                push_token_id=None,
                status="skipped_inactive",
                attempt_number=attempt_number,
                error_details="No active push token",
                idempotency_key=log_idempotency_key,
            )
            session.add(log)
            session.commit()
            logger.info("push_skipped", device_id=str(device.id), reason="no_token")
            return

        payload = {
            "title": "New message",
            "body": message.content[:120],
            "chat_id": str(message.chat_id),
            "message_id": str(message.id),
        }

        try:
            provider_response = _send_to_provider(
                push_token.token, push_token.token_type, payload,
            )

            # Успех: логируем, обновляем last_used_at, сбрасываем failure_count
            log = PushLog(
                message_id=message.id,
                device_id=device.id,
                push_token_id=push_token.id,
                status="sent_to_provider",
                provider_message_id=provider_response.get("provider_message_id"),
                attempt_number=attempt_number,
                idempotency_key=log_idempotency_key,
            )
            session.add(log)

            push_token.last_used_at = datetime.now(timezone.utc)
            push_token.failure_count = 0
            push_token.last_failure_reason = None

            session.commit()
            logger.info(
                "push_sent",
                device_id=str(device.id),
                provider=provider_response.get("provider"),
                attempt=attempt_number,
            )
            return  # УСПЕХ — никогда не ретраим

        except Exception as exc:
            session.rollback()
            error_class = _classify_fcm_error(exc)
            error_text = f"{type(exc).__name__}: {str(exc)[:400]}"

            # Записываем лог об ошибке
            log = PushLog(
                message_id=message.id,
                device_id=device.id,
                push_token_id=push_token.id,
                status="failed_provider",
                attempt_number=attempt_number,
                error_details=error_text,
                idempotency_key=log_idempotency_key,
            )
            session.add(log)

            push_token.failure_count = (push_token.failure_count or 0) + 1
            push_token.last_failure_reason = error_text[:255]

            if error_class == "permanent":
                # Инвалидируем токен, ретраить бессмысленно
                push_token.is_valid = False
                push_token.last_used_at = datetime.now(timezone.utc)
                session.commit()
                logger.warning(
                    "push_token_invalidated",
                    device_id=str(device.id),
                    error=error_text,
                )
                return

            # Transient/unknown: сохраняем лог и ретраим
            session.commit()
            logger.error(
                "push_failed_transient",
                device_id=str(device.id),
                error=error_text,
                attempt=attempt_number,
            )

            if attempt_number <= self.max_retries:
                raise self.retry(exc=exc)
            else:
                logger.error("push_max_retries_exceeded", device_id=str(device.id))
                return

    except self.MaxRetriesExceededError:
        pass
    except Exception as exc:
        # Retry'ы Celery ре-поднимают Retry-исключение — не логируем его как ошибку
        from celery.exceptions import Retry
        if not isinstance(exc, Retry):
            logger.error("push_subtask_error", message_id=message_id, device_id=device_id, error=str(exc))
        session.rollback()
        raise
    finally:
        session.close()