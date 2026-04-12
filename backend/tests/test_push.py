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
        "/api/v1/devices/push-token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "token": "fcm-token-abc123",
            "token_type": "fcm",
        },
    )
    assert resp.status_code == 200
    assert "id" in resp.json()


@pytest.mark.asyncio
async def test_register_push_token_updates_existing_for_same_device(
    client: AsyncClient,
    user_token: str,
    test_device: Device,
):
    resp1 = await client.post(
        "/api/v1/devices/push-token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "token": "fcm-token-old",
            "token_type": "fcm",
        },
    )
    assert resp1.status_code == 200

    resp2 = await client.post(
        "/api/v1/devices/push-token",
        headers={"Authorization": f"Bearer {user_token}"},
        json={
            "token": "fcm-token-new",
            "token_type": "fcm",
        },
    )
    assert resp2.status_code == 200
    assert resp1.json()["id"] == resp2.json()["id"]


@pytest.mark.asyncio
async def test_register_push_token_rejects_unknown_device_context(
    client: AsyncClient,
    test_user,
):
    from app.core.security import create_access_token

    bad_token = create_access_token(test_user.id, test_user.role, test_user.id)

    resp = await client.post(
        "/api/v1/devices/push-token",
        headers={"Authorization": f"Bearer {bad_token}"},
        json={
            "token": "fcm-token-abc123",
            "token_type": "fcm",
        },
    )
    assert resp.status_code == 401