import json
import structlog
from sqlalchemy import select, and_
from sqlalchemy.orm import Session

from app.workers.celery_app import celery_app
from app.config import settings

logger = structlog.get_logger()


def _get_sync_session():
    """Create a sync DB session for Celery tasks."""
    from sqlalchemy import create_engine
    from sqlalchemy.orm import sessionmaker

    # Convert async URL to sync
    sync_url = settings.database_url.replace(
        "postgresql+asyncpg://", "postgresql+psycopg2://"
    )
    engine = create_engine(sync_url)
    SessionLocal = sessionmaker(bind=engine)
    return SessionLocal()


@celery_app.task(
    name="send_push_for_message",
    bind=True,
    max_retries=3,
    default_retry_delay=10,
    acks_late=True,
)
def send_push_for_message(
    self,
    message_id: str,
    chat_id: str,
    sender_id: str,
    sender_name: str,
    content: str,
):
    """
    Send push notifications to all chat members (except sender)
    who have valid push tokens.
    """
    session = _get_sync_session()

    try:
        from app.db.models.chat_member import ChatMember
        from app.db.models.device import Device
        from app.db.models.push_token import PushToken
        from app.db.models.push_log import PushLog
        from app.db.models.chat import Chat
        import uuid

        # Get chat name
        chat = session.execute(
            select(Chat).where(Chat.id == uuid.UUID(chat_id))
        ).scalar_one_or_none()
        chat_name = chat.name if chat else "Chat"

        # Get all members except sender
        members = session.execute(
            select(ChatMember.user_id).where(
                ChatMember.chat_id == uuid.UUID(chat_id),
                ChatMember.user_id != uuid.UUID(sender_id),
            )
        ).all()

        member_user_ids = [m[0] for m in members]

        if not member_user_ids:
            logger.info("push_no_recipients", chat_id=chat_id)
            return

        # Get active devices with valid push tokens for these users
        tokens = session.execute(
            select(PushToken, Device).join(
                Device, PushToken.device_id == Device.id
            ).where(
                Device.user_id.in_(member_user_ids),
                Device.is_active == True,
                PushToken.is_valid == True,
            )
        ).all()

        if not tokens:
            logger.info("push_no_tokens", chat_id=chat_id, member_count=len(member_user_ids))
            return

        # Build notification payload
        notification_data = {
            "title": f"{sender_name} in {chat_name}",
            "body": content[:200],
            "data": {
                "type": "new_message",
                "chat_id": chat_id,
                "message_id": message_id,
                "sender_id": sender_id,
            },
        }

        for push_token, device in tokens:
            try:
                _send_single_push(push_token, notification_data, message_id, session)
            except Exception as e:
                logger.error(
                    "push_send_error",
                    token_id=str(push_token.id),
                    error=str(e),
                )

        session.commit()
        logger.info(
            "push_sent",
            message_id=message_id,
            chat_id=chat_id,
            token_count=len(tokens),
        )

    except Exception as e:
        session.rollback()
        logger.error("push_task_error", error=str(e), message_id=message_id)
        raise self.retry(exc=e)
    finally:
        session.close()


def _send_single_push(push_token, notification_data: dict, message_id: str, session):
    """Send a single push notification via FCM."""
    import uuid
    from app.db.models.push_log import PushLog

    if push_token.token_type == "fcm":
        success, response = _send_fcm(push_token.token, notification_data)
    else:
        success = False
        response = {"error": f"Unknown token type: {push_token.token_type}"}

    log = PushLog(
        push_token_id=push_token.id,
        message_id=uuid.UUID(message_id) if message_id else None,
        status="sent" if success else "failed",
        provider_response=response,
        error_message=response.get("error") if not success else None,
    )
    session.add(log)

    # Mark token as invalid if FCM says so
    if not success and response.get("invalid_token"):
        push_token.is_valid = False
        logger.info("push_token_invalidated", token_id=str(push_token.id))


def _send_fcm(token: str, notification_data: dict) -> tuple[bool, dict]:
    """
    Send FCM push notification.
    Returns (success, response_dict).

    In development mode without FCM config, simulates success.
    """
    if not settings.fcm_service_account_json:
        logger.info("fcm_stub_mode", token=token[:20] + "...")
        return True, {"stub": True, "message": "FCM not configured, simulated send"}

    try:
        import firebase_admin
        from firebase_admin import credentials, messaging

        # Initialize Firebase if not already done
        if not firebase_admin._apps:
            cred = credentials.Certificate(json.loads(settings.fcm_service_account_json))
            firebase_admin.initialize_app(cred)

        message = messaging.Message(
            notification=messaging.Notification(
                title=notification_data["title"],
                body=notification_data["body"],
            ),
            data={k: str(v) for k, v in notification_data.get("data", {}).items()},
            token=token,
            android=messaging.AndroidConfig(
                priority="high",
                notification=messaging.AndroidNotification(
                    click_action="OPEN_CHAT",
                ),
            ),
        )

        response = messaging.send(message)
        return True, {"message_id": response}

    except Exception as e:
        error_str = str(e)
        invalid = "not-registered" in error_str.lower() or "invalid-registration" in error_str.lower()
        return False, {"error": error_str, "invalid_token": invalid}