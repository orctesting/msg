import uuid
import pytest
from httpx import AsyncClient

from app.db.models.user import User


@pytest.mark.asyncio
async def test_admin_list_users(client: AsyncClient, admin_token: str, test_user: User):
    resp = await client.get(
        "/api/v1/admin/users",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 200
    assert isinstance(resp.json(), list)


@pytest.mark.asyncio
async def test_admin_create_chat(client: AsyncClient, admin_token: str, test_user: User):
    resp = await client.post(
        "/api/v1/admin/chats",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={
            "name": "Admin Chat",
            "type": "group",
            "member_ids": [str(test_user.id)],
        },
    )
    assert resp.status_code == 201
    assert resp.json()["name"] == "Admin Chat"


@pytest.mark.asyncio
async def test_non_admin_forbidden(client: AsyncClient, user_token: str):
    resp = await client.get(
        "/api/v1/admin/users",
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert resp.status_code == 403