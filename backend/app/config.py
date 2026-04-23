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

    otp_request_cooldown_seconds: int = 10
    otp_max_requests_per_hour: int = 20
    otp_max_verify_attempts: int = 20
    otp_session_ttl_seconds: int = 300
    
    s3_endpoint_url: str = "http://localhost:9000"
    s3_public_endpoint_url: str = "http://localhost:9000"
    s3_access_key: str = "minio_admin"
    s3_secret_key: str = "minio_secret_change_me"
    s3_bucket: str = "attachments"
    s3_region: str = "us-east-1"

    attachment_max_size_bytes: int = 50 * 1024 * 1024
    attachment_upload_url_ttl_seconds: int = 900
    attachment_download_url_ttl_seconds: int = 3600

    admin_phone: str = "+70000000000"
    admin_display_name: str = "Admin"

    log_json: bool = True

    model_config = {"env_file": ".env", "extra": "ignore"}


settings = Settings()