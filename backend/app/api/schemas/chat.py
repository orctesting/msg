import uuid
from datetime import datetime
from pydantic import BaseModel, Field

from app.api.schemas.base import BaseSchema


class ChatCreateIn(BaseModel):
    name: str | None = Field(None, max_length=255)
    type: str = Field(default="group", pattern=r"^(personal|group)$")
    member_ids: list[uuid.UUID] = Field(..., min_length=1)


class ChatOut(BaseSchema):
    id: uuid.UUID
    name: str | None
    type: str
    created_at: datetime


class ChatListOut(BaseModel):
    chats: list[ChatOut]