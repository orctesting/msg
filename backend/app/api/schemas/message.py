import uuid
from datetime import datetime
from pydantic import BaseModel


class MessageSendIn(BaseModel):
    content: str
    message_type: str = "text"
    idempotency_key: str | None = None


class MessageOut(BaseModel):
    id: uuid.UUID
    chat_id: uuid.UUID
    sender_id: uuid.UUID
    content: str
    message_type: str
    created_at: datetime

    model_config = {"from_attributes": True}


class MessageListOut(BaseModel):
    messages: list[MessageOut]
    has_more: bool


class MessageReadIn(BaseModel):
    message_ids: list[uuid.UUID]