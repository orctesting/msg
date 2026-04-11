import uuid
from datetime import datetime
from pydantic import BaseModel, Field

from app.api.schemas.base import BaseSchema


class UserOut(BaseSchema):
    id: uuid.UUID
    phone: str
    display_name: str
    role: str
    is_active: bool
    created_at: datetime


class UserUpdateIn(BaseModel):
    display_name: str | None = Field(None, max_length=255)


class UserSearchOut(BaseModel):
    id: uuid.UUID
    display_name: str