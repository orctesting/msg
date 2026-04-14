import firebase_admin
from firebase_admin import credentials, messaging
from app.config import settings
import structlog

logger = structlog.get_logger()

_initialized = False


def _ensure_initialized():
    global _initialized
    if _initialized:
        return
    
    cred_path = settings.fcm_service_account_json
    if not cred_path:
        logger.warning("fcm_no_credentials_path")
        return
    
    try:
        cred = credentials.Certificate(cred_path)
        firebase_admin.initialize_app(cred)
        _initialized = True
        logger.info("fcm_initialized")
    except Exception as e:
        logger.error("fcm_init_error", error=str(e))


def send_fcm_push(token: str, title: str, body: str, data: dict | None = None) -> str | None:
    """Send FCM data-only push. Returns provider message_id or None on failure."""
    _ensure_initialized()
    if not _initialized:
        logger.warning("fcm_not_initialized")
        return None

    # Send data-only message (no notification block) so onMessageReceived always fires
    message = messaging.Message(
        token=token,
        data={
            "title": title,
            "body": body,
            **(({k: str(v) for k, v in data.items()}) if data else {}),
        },
    )
    try:
        result = messaging.send(message)
        logger.info("fcm_send_success", message_id=result, token_prefix=token[:20])
        return result
    except messaging.UnregisteredError:
        logger.warning("fcm_token_unregistered", token_prefix=token[:20])
        raise
    except Exception as e:
        logger.error("fcm_send_error", error=str(e), token_prefix=token[:20])
        raise