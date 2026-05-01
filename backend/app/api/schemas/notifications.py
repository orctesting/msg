import uuid
from typing import Literal
from pydantic import BaseModel, Field, field_validator


VALID_MODES = ("all", "personal_only", "whitelist", "none")
VALID_PLATFORMS = ("android", "ios", "desktop", "web")


class NotificationSettingsItem(BaseModel):
    platform: str
    mode: str
    whitelist_chat_ids: list[uuid.UUID] = []


class NotificationSettingsListOut(BaseModel):
    items: list[NotificationSettingsItem]


class UpdateNotificationSettingsIn(BaseModel):
    mode: str = Field(..., description="all|personal_only|whitelist|none")
    chat_ids: list[uuid.UUID] = []

    @field_validator("mode")
    @classmethod
    def _validate_mode(cls, v: str) -> str:
        if v not in VALID_MODES:
            raise ValueError(f"mode must be one of {VALID_MODES}")
        return v