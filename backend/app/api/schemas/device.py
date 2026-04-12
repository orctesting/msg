import uuid
from pydantic import BaseModel, Field


class PushTokenRegisterIn(BaseModel):
    token: str = Field(..., min_length=1)
    token_type: str = Field(..., pattern=r"^(fcm|apns|web_push|ws)$")


class PushTokenRegisterOut(BaseModel):
    id: uuid.UUID