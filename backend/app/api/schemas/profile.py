import re
import uuid
from datetime import datetime, date
from pydantic import BaseModel, Field, EmailStr, field_validator

from app.utils.username import USERNAME_PATTERN


class AvatarOut(BaseModel):
    id: uuid.UUID
    full_attachment_id: uuid.UUID
    crop_attachment_id: uuid.UUID
    full_url: str | None = None
    crop_url: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class MeOut(BaseModel):
    id: uuid.UUID
    phone: str
    username: str
    display_name: str
    first_name: str | None = None
    last_name: str | None = None
    birth_date: date | None = None
    bio: str | None = None
    email: str | None = None
    role: str
    primary_avatar_attachment_id: uuid.UUID | None = None
    primary_avatar_thumb_attachment_id: uuid.UUID | None = None
    primary_avatar_url: str | None = None
    primary_avatar_thumb_url: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}


class MeUpdateIn(BaseModel):
    username: str | None = Field(None, min_length=1, max_length=64)
    display_name: str | None = Field(None, min_length=1, max_length=255)
    first_name: str | None = Field(None, max_length=100)
    last_name: str | None = Field(None, max_length=100)
    birth_date: date | None = None
    bio: str | None = Field(None, max_length=1000)
    email: EmailStr | None = None

    @field_validator("username")
    @classmethod
    def _validate_username(cls, v: str | None) -> str | None:
        if v is None:
            return v
        v = v.strip().lower()
        if not USERNAME_PATTERN.match(v):
            raise ValueError("Invalid username: only a-z, 0-9, _, -, ., # allowed (1..64)")
        return v


class AvatarCreateIn(BaseModel):
    """Загрузить уже готовый файл-исходник (attachment_id) и координаты crop'а."""
    source_attachment_id: uuid.UUID
    crop_x: int = Field(..., ge=0)
    crop_y: int = Field(..., ge=0)
    crop_size: int = Field(..., gt=0)


class AvatarListOut(BaseModel):
    avatars: list[AvatarOut]
    primary_avatar_id: uuid.UUID | None = None


class SetPrimaryAvatarIn(BaseModel):
    avatar_id: uuid.UUID
    

class PublicUserOut(BaseModel):
    id: uuid.UUID
    username: str
    display_name: str
    first_name: str | None = None
    last_name: str | None = None
    bio: str | None = None
    primary_avatar_url: str | None = None
    primary_avatar_thumb_url: str | None = None
    created_at: datetime

    model_config = {"from_attributes": True}