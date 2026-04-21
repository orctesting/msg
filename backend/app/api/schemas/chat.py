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

    model_config = {"from_attributes": True}


class PinnedMessageOut(BaseModel):
    id: uuid.UUID
    chat_id: uuid.UUID
    sender_id: uuid.UUID | None
    sender_name: str | None = None
    content: str
    message_type: str
    created_at: datetime
    pinned_by_user_id: uuid.UUID | None = None
    pinned_at: datetime | None = None


class PeerUserOut(BaseModel):
    id: uuid.UUID
    phone: str
    display_name: str


class ChatOut(BaseModel):
    id: uuid.UUID
    name: str
    type: str
    unread_count: int = 0
    last_message: LastMessageOut | None = None
    pinned_message: PinnedMessageOut | None = None
    peer_user: PeerUserOut | None = None
    peer_is_in_contacts: bool | None = None
    peer_dismissed: bool | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class ChatListOut(BaseModel):
    chats: list[ChatOut]


class ChatCreateIn(BaseModel):
    name: str
    type: str = "group"
    member_ids: list[uuid.UUID]


class PinMessageIn(BaseModel):
    message_id: uuid.UUID


class PersonalChatCreateIn(BaseModel):
    contact_id: uuid.UUID | None = None
    phone: str | None = None