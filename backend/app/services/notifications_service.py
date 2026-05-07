import uuid
import json
from typing import Iterable

import structlog

from app.services.ws_manager import ws_manager

logger = structlog.get_logger()


async def publish_dismiss_to_user(
    user_id: uuid.UUID,
    chat_id: uuid.UUID,
    message_ids: Iterable[uuid.UUID],
    reason: str,
):
    """
    Публикует WS-event 'notification_dismiss' в канал user:<id>.
    Доставляется всем активным WS-сессиям пользователя.
    """
    ids_list = [str(m) for m in message_ids]
    if not ids_list:
        return
    event = {
        "type": "notification_dismiss",
        "data": {
            "chat_id": str(chat_id),
            "message_ids": ids_list,
            "reason": reason,
        },
    }
    try:
        await ws_manager.publish_to_user(user_id=user_id, event=event)
    except Exception as e:
        logger.error("publish_dismiss_ws_error", error=str(e))


async def publish_dismiss_to_chat(
    chat_id: uuid.UUID,
    message_ids: Iterable[uuid.UUID],
    reason: str,
    exclude_user_id: uuid.UUID | None = None,
):
    """
    Публикует WS-event 'notification_dismiss' всем участникам чата.
    Используется при удалении сообщений.
    """
    ids_list = [str(m) for m in message_ids]
    if not ids_list:
        return
    event = {
        "type": "notification_dismiss",
        "data": {
            "chat_id": str(chat_id),
            "message_ids": ids_list,
            "reason": reason,
        },
    }
    try:
        await ws_manager.publish_event(
            chat_id=chat_id,
            event=event,
            exclude_user_id=exclude_user_id,
        )
    except Exception as e:
        logger.error("publish_dismiss_chat_ws_error", error=str(e))


def enqueue_fcm_dismiss_for_user(
    user_id: uuid.UUID,
    chat_id: uuid.UUID,
    message_ids: list[uuid.UUID],
    reason: str,
):
    """
    Ставит Celery-таску, которая разошлёт FCM data-dismiss всем оффлайн
    Android-устройствам пользователя.
    """
    if not message_ids:
        return
    try:
        from app.workers.tasks.push import send_dismiss_push_for_user
        send_dismiss_push_for_user.delay(
            str(user_id),
            str(chat_id),
            [str(m) for m in message_ids],
            reason,
        )
    except Exception as e:
        logger.error("enqueue_fcm_dismiss_error", error=str(e))


def enqueue_fcm_dismiss_for_chat_recipients(
    recipient_user_ids: list[uuid.UUID],
    chat_id: uuid.UUID,
    message_ids: list[uuid.UUID],
    reason: str,
):
    if not message_ids or not recipient_user_ids:
        return
    try:
        from app.workers.tasks.push import send_dismiss_push_for_users
        send_dismiss_push_for_users.delay(
            [str(u) for u in recipient_user_ids],
            str(chat_id),
            [str(m) for m in message_ids],
            reason,
        )
    except Exception as e:
        logger.error("enqueue_fcm_dismiss_chat_error", error=str(e))