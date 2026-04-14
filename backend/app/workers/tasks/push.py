import uuid

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


@celery_app.task(bind=True, max_retries=3, default_retry_delay=30)
def send_push_notification(self, message_id: str, recipient_user_ids: list[str]):
    session: Session = _sync_db_session()
    try:
        message = session.execute(
            select(Message).where(Message.id == uuid.UUID(message_id))
        ).scalar_one_or_none()
        if message is None:
            logger.error("push_message_not_found", message_id=message_id)
            return

        devices = session.execute(
            select(Device).where(
                Device.user_id.in_([uuid.UUID(uid) for uid in recipient_user_ids]),
                Device.is_active.is_(True),
            )
        ).scalars().all()

        attempt_number = int(self.request.retries) + 1
        failed_exc = None

        for device in devices:
            push_token = session.execute(
                select(PushToken).where(
                    PushToken.device_id == device.id,
                    PushToken.is_valid.is_(True),
                )
            ).scalar_one_or_none()

            log_idempotency_key = _make_push_idempotency_key(
                message.id, device.id, attempt_number,
            )

            # Check for existing log (idempotency)
            existing_log = session.execute(
                select(PushLog).where(
                    PushLog.idempotency_key == log_idempotency_key,
                )
            ).scalar_one_or_none()
            if existing_log is not None:
                continue

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
                continue

            try:
                payload = {
                    "title": "New message",
                    "body": message.content[:120],
                    "chat_id": str(message.chat_id),
                    "message_id": str(message.id),
                }

                provider_response = _send_to_provider(
                    push_token.token, push_token.token_type, payload,
                )

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
                session.commit()
                logger.info(
                    "push_sent",
                    device_id=str(device.id),
                    provider=provider_response.get("provider"),
                )

            except Exception as exc:
                session.rollback()
                push_token.failure_count = (push_token.failure_count or 0) + 1
                push_token.last_failure_reason = str(exc)[:255]

                log = PushLog(
                    message_id=message.id,
                    device_id=device.id,
                    push_token_id=push_token.id,
                    status="failed_provider",
                    attempt_number=attempt_number,
                    error_details=str(exc)[:500],
                    idempotency_key=log_idempotency_key,
                )
                session.add(log)
                session.commit()
                failed_exc = exc
                logger.error("push_failed", device_id=str(device.id), error=str(exc))

        if failed_exc and attempt_number <= self.max_retries:
            raise self.retry(exc=failed_exc)

    except Exception as exc:
        if not isinstance(exc, self.MaxRetriesExceededError):
            logger.error("push_task_error", message_id=message_id, error=str(exc))
        session.rollback()
        raise
    finally:
        session.close()