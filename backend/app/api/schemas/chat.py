import uuid
from datetime import datetime
from pydantic import BaseModel


class ChatOut(BaseModel):
    id: uuid.UUID
    name: str
    type: str
    created_at: datetime

    model_config = {"from_attributes": True}


class ChatListOut(BaseModel):
    chats: list[ChatOut]


class ChatCreateIn(BaseModel):
    name: str
    type: str = "group"
    member_ids: list[uuid.UUID]