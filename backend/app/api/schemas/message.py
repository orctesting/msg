import uuid
from datetime import datetime
from pydantic import BaseModel, Field

from app.api.schemas.base import BaseSchema


class MessageSendIn(BaseModel):
    content: str = Field(..., min_length=1, max_length=10000)
    message_type: str = Field(default="text", pattern=r"^(text|system)$")
    idempotency_key: uuid.UUID | None = None


class MessageOut(BaseSchema):
    id: uuid.UUID
    chat_id: uuid.UUID
    sender_id: uuid.UUID | None
    content: str
    message_type: str
    created_at: datetime


class MessageListOut(BaseModel):
    messages: list[MessageOut]
    has_more: bool


class MessageReadIn(BaseModel):
    message_ids: list[uuid.UUID]