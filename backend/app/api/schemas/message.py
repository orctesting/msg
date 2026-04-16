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
    sender_id: uuid.UUID | None
    sender_name: str | None = None
    sender_role: str | None = None
    content: str
    message_type: str
    created_at: datetime

    model_config = {"from_attributes": True}


class MessageListOut(BaseModel):
    messages: list[MessageOut]
    has_more: bool
    read_by_others_up_to: uuid.UUID | None = None


class MessageReadIn(BaseModel):
    last_read_message_id: uuid.UUID