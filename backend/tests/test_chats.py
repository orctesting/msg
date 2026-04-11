import pytest
from httpx import AsyncClient

from app.db.models.user import User
from app.db.models.chat import Chat


@pytest.mark.asyncio
async def test_get_chats(client: AsyncClient, user_token: str, test_chat: Chat):
    resp = await client.get(
        "/api/v1/chats",
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert "chats" in data
    assert len(data["chats"]) >= 1


@pytest.mark.asyncio
async def test_get_chat_detail(client: AsyncClient, user_token: str, test_chat: Chat):
    resp = await client.get(
        f"/api/v1/chats/{test_chat.id}",
        headers={"Authorization": f"Bearer {user_token}"},
    )
    assert resp.status_code == 200
    assert resp.json()["id"] == str(test_chat.id)


@pytest.mark.asyncio
async def test_get_chat_forbidden(client: AsyncClient, test_chat: Chat, test_admin: User, admin_token: str):
    # admin is not a member of test_chat
    resp = await client.get(
        f"/api/v1/chats/{test_chat.id}",
        headers={"Authorization": f"Bearer {admin_token}"},
    )
    assert resp.status_code == 403