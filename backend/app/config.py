from pydantic_settings import BaseSettings
from pydantic import Field


class Settings(BaseSettings):
    app_name: str = "Messenger"
    environment: str = "development"
    debug: bool = Field(default=True)
    secret_key: str = "dev-secret-key-change-in-production"
    log_level: str = "DEBUG"

    database_url: str = "postgresql+asyncpg://messenger:messenger_secret@localhost:5432/messenger_db"
    redis_url: str = "redis://localhost:6379/0"

    jwt_access_token_expire_minutes: int = 15
    jwt_refresh_token_expire_days: int = 30
    jwt_algorithm: str = "HS256"

    multifactor_api_key: str = ""
    multifactor_api_url: str = "https://api.multifactor.ru/v1"

    fcm_service_account_json: str = ""
    internal_api_key: str | None = None

    otp_request_cooldown_seconds: int = 60
    otp_max_requests_per_hour: int = 5
    otp_max_verify_attempts: int = 5
    otp_session_ttl_seconds: int = 300

    admin_phone: str = "+70000000000"
    admin_display_name: str = "Admin"

    log_json: bool = True

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()