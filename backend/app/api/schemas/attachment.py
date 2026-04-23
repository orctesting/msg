import uuid
from datetime import datetime
from pydantic import BaseModel, Field


class PresignUploadIn(BaseModel):
    filename: str = Field(..., min_length=1, max_length=500)
    mime_type: str = Field(..., min_length=1, max_length=255)
    size_bytes: int = Field(..., gt=0)
    chat_id: uuid.UUID | None = None


class PresignUploadOut(BaseModel):
    attachment_id: uuid.UUID
    upload_url: str
    storage_key: str
    expires_in: int


class AttachmentCompleteIn(BaseModel):
    pass


class AttachmentOut(BaseModel):
    id: uuid.UUID
    original_filename: str
    mime_type: str
    size_bytes: int
    file_kind: str
    width: int | None = None
    height: int | None = None
    duration_ms: int | None = None
    has_thumbnail: bool = False
    status: str
    created_at: datetime
    download_url: str | None = None
    thumbnail_url: str | None = None

    model_config = {"from_attributes": True}