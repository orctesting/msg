import uuid
import pytest
from httpx import AsyncClient

from app.db.models.user import User
from app.db.models.chat import Chat


@pytest.mark.asyncio
async def test_send_message(client: AsyncClient, user_token: str, test_chat: Chat):
    resp = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers={"Authorization": f"Bearer {user_token}"},
        json={"content": "Hello!", "message_type": "text"},
    )
    assert resp.status_code == 201
    data = resp.json()
    assert data["content"] == "Hello!"
    assert data["chat_id"] == str(test_chat.id)


@pytest.mark.asyncio
async def test_send_message_idempotency(client: AsyncClient, user_token: str, test_chat: Chat):
    key = str(uuid.uuid4())
    headers = {"Authorization": f"Bearer {user_token}"}
    body = {"content": "Idempotent", "message_type": "text", "idempotency_key": key}

    resp1 = await client.post(f"/api/v1/chats/{test_chat.id}/messages", headers=headers, json=body)
    resp2 = await client.post(f"/api/v1/chats/{test_chat.id}/messages", headers=headers, json=body)

    assert resp1.status_code == 201
    assert resp2.status_code == 201
    assert resp1.json()["id"] == resp2.json()["id"]


@pytest.mark.asyncio
async def test_get_messages(client: AsyncClient, user_token: str, test_chat: Chat):
    headers = {"Authorization": f"Bearer {user_token}"}

    # Send a few messages
    for i in range(3):
        await client.post(
            f"/api/v1/chats/{test_chat.id}/messages",
            headers=headers,
            json={"content": f"msg {i}", "message_type": "text"},
        )

    resp = await client.get(f"/api/v1/chats/{test_chat.id}/messages", headers=headers)
    assert resp.status_code == 200
    data = resp.json()
    assert len(data["messages"]) >= 3
    assert "has_more" in data


@pytest.mark.asyncio
async def test_send_message_not_member(client: AsyncClient, admin_token: str, test_chat: Chat):
    resp = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers={"Authorization": f"Bearer {admin_token}"},
        json={"content": "Should fail", "message_type": "text"},
    )
    assert resp.status_code == 403


@pytest.mark.asyncio
async def test_mark_read(client: AsyncClient, user_token: str, test_chat: Chat):
    headers = {"Authorization": f"Bearer {user_token}"}

    resp = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers=headers,
        json={"content": "Read me", "message_type": "text"},
    )
    msg_id = resp.json()["id"]

    resp = await client.post(
        f"/api/v1/chats/{test_chat.id}/read",
        headers=headers,
        json={"message_ids": [msg_id]},
    )
    assert resp.status_code == 204