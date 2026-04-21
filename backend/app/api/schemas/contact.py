import uuid
from datetime import datetime
from pydantic import BaseModel, Field


class ContactCreateIn(BaseModel):
    phone: str = Field(..., min_length=3, max_length=32)
    display_name: str = Field(..., min_length=1, max_length=255)


class ContactUpdateIn(BaseModel):
    display_name: str | None = Field(None, min_length=1, max_length=255)
    phone: str | None = Field(None, min_length=3, max_length=32)


class ContactOut(BaseModel):
    id: uuid.UUID
    phone: str
    display_name: str
    contact_user_id: uuid.UUID | None = None
    is_registered: bool
    created_at: datetime
    updated_at: datetime

    model_config = {"from_attributes": True}


class ContactListOut(BaseModel):
    contacts: list[ContactOut]
    
    
class ContactDismissIn(BaseModel):
    peer_user_id: uuid.UUID