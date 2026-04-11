import uuid
from datetime import datetime

from pydantic import BaseModel, ConfigDict


class BaseSchema(BaseModel):
    model_config = ConfigDict(from_attributes=True)


class TimestampMixin(BaseModel):
    created_at: datetime
    updated_at: datetime


class OkResponse(BaseModel):
    ok: bool = True
    detail: str = "success"


class ErrorResponse(BaseModel):
    ok: bool = False
    error_code: str
    detail: str