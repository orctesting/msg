import uuid
from datetime import datetime
from pydantic import BaseModel


class LastMessageOut(BaseModel):
    id: uuid.UUID
    chat_id: uuid.UUID
    sender_id: uuid.UUID | None
    content: str
    message_type: str
    created_at: datetime


class ChatOut(BaseModel):
    id: uuid.UUID
    name: str
    type: str
    unread_count: int = 0
    last_message: LastMessageOut | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class ChatListOut(BaseModel):
    chats: list[ChatOut]


class ChatCreateIn(BaseModel):
    name: str
    type: str = "group"
    member_ids: list[uuid.UUID]