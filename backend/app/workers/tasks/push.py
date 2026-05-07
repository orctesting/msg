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
    

def _should_deliver(
    session: Session,
    user_id: uuid.UUID,
    platform: str,
    chat_id: uuid.UUID,
) -> bool:
    """
    Возвращает True, если пуш нужно доставить пользователю на платформу
    с учётом notification_settings + notification_chat_whitelist.
    Default (нет записи) = доставлять.
    """
    from app.db.models.notification_settings import (
        NotificationSettings,
        NotificationChatWhitelist,
    )
    from app.db.models.chat import Chat

    settings_row = session.execute(
        select(NotificationSettings).where(
            NotificationSettings.user_id == user_id,
            NotificationSettings.platform == platform,
        )
    ).scalar_one_or_none()

    if settings_row is None:
        return True  # default: all

    mode = settings_row.mode

    if mode == "all":
        return True
    if mode == "none":
        return False

    if mode == "personal_only":
        chat = session.execute(
            select(Chat).where(Chat.id == chat_id)
        ).scalar_one_or_none()
        if chat is None:
            return False
        return chat.type == "personal"

    if mode == "whitelist":
        wl = session.execute(
            select(NotificationChatWhitelist).where(
                NotificationChatWhitelist.user_id == user_id,
                NotificationChatWhitelist.platform == platform,
                NotificationChatWhitelist.chat_id == chat_id,
            )
        ).scalar_one_or_none()
        return wl is not None

    # неизвестный режим → fail-safe: доставлять
    return True


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
    Также фильтрует доставку по notification_settings (mode/whitelist).
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

        # Idempotency check
        existing_log = session.execute(
            select(PushLog).where(PushLog.idempotency_key == log_idempotency_key)
        ).scalar_one_or_none()
        if existing_log is not None:
            logger.info(
                "push_already_logged",
                message_id=message_id,
                device_id=device_id,
                attempt=attempt_number,
            )
            return

        # ── Фильтрация по notification_settings ──
        if not _should_deliver(session, device.user_id, device.platform, message.chat_id):
            log = PushLog(
                message_id=message.id,
                device_id=device.id,
                push_token_id=push_token.id if push_token else None,
                status="skipped_settings",
                attempt_number=attempt_number,
                error_details="Filtered by notification_settings",
                idempotency_key=log_idempotency_key,
            )
            session.add(log)
            session.commit()
            logger.info(
                "push_skipped_settings",
                device_id=str(device.id),
                user_id=str(device.user_id),
                platform=device.platform,
            )
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

        # ── Формирование title/body для пуша ──
        from app.db.models.chat import Chat
        from app.db.models.user import User
        from app.db.models.message_attachment_link import MessageAttachmentLink
        from app.db.models.attachment import Attachment

        chat = session.execute(
            select(Chat).where(Chat.id == message.chat_id)
        ).scalar_one_or_none()
        chat_type = chat.type if chat else "group"
        chat_name = chat.name if chat else ""

        sender_name = ""
        if message.sender_id is not None:
            sender = session.execute(
                select(User).where(User.id == message.sender_id)
            ).scalar_one_or_none()
            if sender:
                sender_name = sender.display_name
        else:
            sender_name = "Admin"

        # Тело: текст сообщения, либо описание вложения
        body_text = (message.content or "").strip()
        if not body_text:
            att_res = session.execute(
                select(Attachment)
                .join(MessageAttachmentLink, MessageAttachmentLink.attachment_id == Attachment.id)
                .where(MessageAttachmentLink.message_id == message.id)
                .limit(1)
            ).scalar_one_or_none()
            if att_res is not None:
                kind = (att_res.file_kind or "").lower()
                body_text = {
                    "image": "📷 Изображение",
                    "video": "🎬 Видео",
                    "audio": "🎵 Аудио",
                }.get(kind, "📎 Вложение")
            else:
                body_text = "Сообщение"

        if chat_type == "personal":
            push_title = sender_name or "Сообщение"
            push_body = body_text[:200]
        else:
            push_title = chat_name or "Чат"
            push_body = f"{sender_name}: {body_text}"[:200] if sender_name else body_text[:200]

        payload = {
            "title": push_title,
            "body": push_body,
            "chat_id": str(message.chat_id),
            "message_id": str(message.id),
        }

        try:
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
            return

        except Exception as exc:
            session.rollback()
            error_class = _classify_fcm_error(exc)
            error_text = f"{type(exc).__name__}: {str(exc)[:400]}"

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
                push_token.is_valid = False
                push_token.last_used_at = datetime.now(timezone.utc)
                session.commit()
                logger.warning(
                    "push_token_invalidated",
                    device_id=str(device.id),
                    error=error_text,
                )
                return

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
        from celery.exceptions import Retry
        if not isinstance(exc, Retry):
            logger.error(
                "push_subtask_error",
                message_id=message_id,
                device_id=device_id,
                error=str(exc),
            )
        session.rollback()
        raise
    finally:
        session.close()
        
        
def _send_fcm_data_only(token: str, token_type: str, data: dict) -> bool:
    """Отправляет data-only FCM. Возвращает True/False, не бросает исключений наружу."""
    if token_type != "fcm":
        return False
    try:
        from app.services.fcm_service import send_fcm_push
        # send_fcm_push использует только data-payload (см. сервис), title/body уйдут в data
        send_fcm_push(
            token=token,
            title="",
            body="",
            data=data,
        )
        return True
    except Exception as e:
        logger.warning("fcm_dismiss_send_error", error=str(e))
        return False


@celery_app.task(bind=True, max_retries=0)
def send_dismiss_push_for_user(
    self,
    user_id: str,
    chat_id: str,
    message_ids: list,
    reason: str,
):
    """
    Шлёт FCM data-only с action=dismiss всем активным Android/iOS-устройствам пользователя.
    Лог в push_logs не пишется (служебный сигнал).
    """
    session: Session = _sync_db_session()
    try:
        devices = session.execute(
            select(Device).where(
                Device.user_id == uuid.UUID(user_id),
                Device.is_active.is_(True),
            )
        ).scalars().all()

        for device in devices:
            push_token = session.execute(
                select(PushToken).where(
                    PushToken.device_id == device.id,
                    PushToken.is_valid.is_(True),
                    PushToken.token_type == "fcm",
                )
            ).scalar_one_or_none()
            if push_token is None:
                continue

            payload = {
                "action": "dismiss",
                "chat_id": chat_id,
                "message_ids": ",".join(message_ids),
                "reason": reason,
            }
            _send_fcm_data_only(push_token.token, push_token.token_type, payload)
    except Exception as e:
        logger.error("send_dismiss_push_for_user_error", error=str(e))
    finally:
        session.close()


@celery_app.task(bind=True, max_retries=0)
def send_dismiss_push_for_users(
    self,
    user_ids: list,
    chat_id: str,
    message_ids: list,
    reason: str,
):
    for uid in user_ids:
        try:
            send_dismiss_push_for_user.delay(uid, chat_id, message_ids, reason)
        except Exception as e:
            logger.error("send_dismiss_push_for_users_enqueue_error", error=str(e), user_id=uid)