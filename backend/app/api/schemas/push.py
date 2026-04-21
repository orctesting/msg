import uuid
from datetime import datetime
from pydantic import BaseModel


class PushBroadcastIn(BaseModel):
    chat_id: uuid.UUID
    content: str
    message_type: str = "notification"
    send_push: bool = True
    push_title: str | None = None
    idempotency_key: uuid.UUID


class PushBroadcastOut(BaseModel):
    message_id: uuid.UUID
    push_task_id: str | None
    recipients_count: int


class PushStatusItemOut(BaseModel):
    user_id: uuid.UUID
    device_id: uuid.UUID
    platform: str
    status: str
    provider_message_id: str | None = None
    attempt_number: int
    created_at: datetime


class PushStatusOut(BaseModel):
    message_id: uuid.UUID
    total_recipients: int
    delivery_stats: dict[str, int]
    details: list[PushStatusItemOut]