import uuid
from datetime import datetime
from pydantic import BaseModel


class UserOut(BaseModel):
    id: uuid.UUID
    phone: str
    display_name: str
    role: str
    is_active: bool
    created_at: datetime

    model_config = {"from_attributes": True}


class UserUpdateIn(BaseModel):
    display_name: str | None = None