import uuid

from celery import shared_task
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
    return {
        "status": "sent",
        "provider_message_id": str(uuid.uuid4()),
        "provider": token_type,
    }


@shared_task(bind=True, max_retries=3, default_retry_delay=30)
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

        for device in devices:
            push_token = session.execute(
                select(PushToken).where(
                    PushToken.device_id == device.id,
                    PushToken.is_valid.is_(True),
                )
            ).scalar_one_or_none()

            log_idempotency_key = _make_push_idempotency_key(
                message.id,
                device.id,
                attempt_number,
            )

            if push_token is None:
                session.add(
                    PushLog(
                        message_id=message.id,
                        device_id=device.id,
                        push_token_id=None,
                        status="skipped_inactive",
                        attempt_number=attempt_number,
                        error_details="No active push token",
                        idempotency_key=log_idempotency_key,
                    )
                )
                continue

            try:
                payload = {
                    "title": "New message",
                    "body": message.content[:120],
                    "chat_id": str(message.chat_id),
                    "message_id": str(message.id),
                }

                provider_response = _send_to_provider(
                    push_token.token,
                    push_token.token_type,
                    payload,
                )

                session.add(
                    PushLog(
                        message_id=message.id,
                        device_id=device.id,
                        push_token_id=push_token.id,
                        status="sent_to_provider",
                        provider_message_id=provider_response.get("provider_message_id"),
                        attempt_number=attempt_number,
                        idempotency_key=log_idempotency_key,
                    )
                )

            except Exception as exc:
                push_token.failure_count = (push_token.failure_count or 0) + 1
                push_token.last_failure_reason = str(exc)

                session.add(
                    PushLog(
                        message_id=message.id,
                        device_id=device.id,
                        push_token_id=push_token.id,
                        status="failed_provider",
                        attempt_number=attempt_number,
                        error_details=str(exc),
                        idempotency_key=log_idempotency_key,
                    )
                )

                if attempt_number < self.max_retries:
                    session.commit()
                    raise self.retry(exc=exc)

        session.commit()

    except Exception as exc:
        logger.error("push_task_failed", message_id=message_id, error=str(exc))
        session.rollback()
        raise

    finally:
        session.close()