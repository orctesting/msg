import uuid
from pydantic import BaseModel, Field


class OTPRequestIn(BaseModel):
    phone: str = Field(..., pattern=r"^\+\d{10,15}$", examples=["+79991234567"])


class OTPRequestOut(BaseModel):
    ok: bool = True
    detail: str = "OTP sent"
    otp_session_id: uuid.UUID


class OTPVerifyIn(BaseModel):
    otp_session_id: uuid.UUID
    code: str = Field(..., min_length=4, max_length=8)
    device_id: str = Field(..., min_length=1, max_length=255)
    platform: str = Field(..., pattern=r"^(android|ios|web)$")
    app_version: str | None = None
    os_version: str | None = None
    display_name: str | None = Field(None, max_length=255)


class OTPVerifyOut(BaseModel):
    ok: bool = True
    access_token: str
    refresh_token: str
    user_id: uuid.UUID
    is_new_user: bool


class TokenRefreshIn(BaseModel):
    refresh_token: str


class TokenRefreshOut(BaseModel):
    access_token: str
    refresh_token: str