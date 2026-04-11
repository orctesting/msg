import pytest
from httpx import AsyncClient

from app.db.models.device import Device


@pytest.mark.asyncio
async def test_register_push_token(
    client: AsyncClient,
    user_token: str,
    test_device: Device,
):
    resp = await client.post(
        "/api/v1/push/token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "device_id": "test-device-001",
            "token": "fcm-token-abc123",
            "token_type": "fcm",
        },
    )
    assert resp.status_code == 201
    assert resp.json()["detail"] == "Push token registered"


@pytest.mark.asyncio
async def test_register_push_token_no_device(
    client: AsyncClient,
    user_token: str,
):
    resp = await client.post(
        "/api/v1/push/token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "device_id": "nonexistent-device",
            "token": "fcm-token-abc123",
            "token_type": "fcm",
        },
    )
    assert resp.status_code == 404


@pytest.mark.asyncio
async def test_unregister_push_token(
    client: AsyncClient,
    user_token: str,
    test_device: Device,
):
    await client.post(
        "/api/v1/push/token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "device_id": "test-device-001",
            "token": "fcm-token-to-remove",
            "token_type": "fcm",
        },
    )

    resp = await client.request(
        "DELETE",
        "/api/v1/push/token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "device_id": "test-device-001",
            "token": "fcm-token-to-remove",
            "token_type": "fcm",
        },
    )
    assert resp.status_code == 200