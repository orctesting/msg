from fastapi import Request
from app.config import settings


def derive_s3_public_base(request: Request) -> str:
    """
    Returns the public S3 endpoint URL.
    If S3_PUBLIC_ENDPOINT_URL is explicitly set and not localhost,
    uses it. Otherwise derives from the incoming request (assumes nginx
    proxies /s3/ to MinIO).
    """
    configured = (settings.s3_public_endpoint_url or "").strip()
    if configured and "localhost" not in configured and "127.0.0.1" not in configured:
        return configured

    forwarded_proto = request.headers.get("x-forwarded-proto")
    forwarded_host = request.headers.get("x-forwarded-host") or request.headers.get("host")
    scheme = forwarded_proto or request.url.scheme
    host = forwarded_host or request.url.netloc
    if not host:
        return configured or ""
    return f"{scheme}://{host}/s3"