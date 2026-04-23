import uuid
from datetime import datetime
from pydantic import BaseModel

from app.api.schemas.attachment import AttachmentOut


class ReplyPreview(BaseModel):
    id: uuid.UUID
    sender_id: uuid.UUID | None = None
    sender_name: str | None = None
    content: str
    message_type: str
    is_deleted: bool = False


class ForwardedInfo(BaseModel):
    original_message_id: uuid.UUID | None = None
    sender_name: str | None = None
    is_deleted: bool = False


class MessageSendIn(BaseModel):
    content: str = ""
    message_type: str = "text"
    idempotency_key: str | None = None
    reply_to_message_id: uuid.UUID | None = None
    forwarded_from_message_id: uuid.UUID | None = None
    attachment_ids: list[uuid.UUID] = []


class MessageEditIn(BaseModel):
    content: str


class MessageOut(BaseModel):
    id: uuid.UUID
    chat_id: uuid.UUID
    sender_id: uuid.UUID | None
    sender_name: str | None = None
    sender_role: str | None = None
    content: str
    message_type: str
    created_at: datetime
    edited_at: datetime | None = None
    reply_to: ReplyPreview | None = None
    forwarded_from: ForwardedInfo | None = None
    attachments: list[AttachmentOut] = []

    model_config = {"from_attributes": True}


class MessageListOut(BaseModel):
    messages: list[MessageOut]
    has_more: bool
    read_by_others_up_to: uuid.UUID | None = None


class MessageReadIn(BaseModel):
    last_read_message_id: uuid.UUID


class BulkDeleteIn(BaseModel):
    message_ids: list[uuid.UUID]


class ForwardMessageIn(BaseModel):
    source_chat_id: uuid.UUID
    message_id: uuid.UUID
    target_chat_id: uuid.UUID
    idempotency_key: str | None = None