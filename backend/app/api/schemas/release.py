import uuid
from datetime import datetime
from pydantic import BaseModel, Field


class ReleaseLatestOut(BaseModel):
    version_name: str
    version_code: int
    download_url: str
    sha256: str
    file_size_bytes: int
    release_notes: str | None = None
    is_mandatory: bool = False


class ReleaseCreateIn(BaseModel):
    platform: str = Field(..., pattern=r"^(android|desktop_win|desktop_mac|desktop_linux)$")
    channel: str = "stable"
    version_name: str = Field(..., max_length=32)
    version_code: int = Field(..., ge=1)
    min_supported_version_code: int | None = None
    storage_key: str
    file_size_bytes: int = Field(..., ge=0)
    sha256: str = Field(..., min_length=64, max_length=64)
    release_notes: str | None = None
    is_published: bool = False


class ReleaseOut(BaseModel):
    id: uuid.UUID
    platform: str
    channel: str
    version_name: str
    version_code: int
    min_supported_version_code: int | None
    storage_key: str
    file_size_bytes: int
    sha256: str
    release_notes: str | None
    is_published: bool
    created_at: datetime
    published_at: datetime | None

    model_config = {"from_attributes": True}