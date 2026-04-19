import uuid
import pytest
from httpx import AsyncClient

from app.db.models.chat import Chat


@pytest.mark.asyncio
async def test_edit_message(client: AsyncClient, user_token: str, test_chat: Chat):
    headers = {"Authorization": f"Bearer {user_token}"}
    resp = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers=headers,
        json={"content": "orig", "message_type": "text"},
    )
    msg_id = resp.json()["id"]

    resp = await client.patch(
        f"/api/v1/chats/{test_chat.id}/messages/{msg_id}",
        headers=headers,
        json={"content": "edited"},
    )
    assert resp.status_code == 200
    data = resp.json()
    assert data["content"] == "edited"
    assert data["edited_at"] is not None


@pytest.mark.asyncio
async def test_delete_message(client: AsyncClient, user_token: str, test_chat: Chat):
    headers = {"Authorization": f"Bearer {user_token}"}
    resp = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers=headers,
        json={"content": "to delete", "message_type": "text"},
    )
    msg_id = resp.json()["id"]

    resp = await client.delete(
        f"/api/v1/chats/{test_chat.id}/messages/{msg_id}",
        headers=headers,
    )
    assert resp.status_code == 200

    resp = await client.get(f"/api/v1/chats/{test_chat.id}/messages", headers=headers)
    ids = [m["id"] for m in resp.json()["messages"]]
    assert msg_id not in ids


@pytest.mark.asyncio
async def test_reply_message(client: AsyncClient, user_token: str, test_chat: Chat):
    headers = {"Authorization": f"Bearer {user_token}"}
    r1 = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers=headers,
        json={"content": "original", "message_type": "text"},
    )
    orig_id = r1.json()["id"]

    r2 = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers=headers,
        json={"content": "reply", "reply_to_message_id": orig_id},
    )
    assert r2.status_code == 201
    assert r2.json()["reply_to"]["id"] == orig_id


@pytest.mark.asyncio
async def test_pin_and_unpin_local(client: AsyncClient, user_token: str, test_chat: Chat):
    headers = {"Authorization": f"Bearer {user_token}"}
    r = await client.post(
        f"/api/v1/chats/{test_chat.id}/messages",
        headers=headers,
        json={"content": "to pin"},
    )
    msg_id = r.json()["id"]

    r = await client.post(
        f"/api/v1/chats/{test_chat.id}/pin",
        headers=headers,
        json={"message_id": msg_id},
    )
    assert r.status_code == 200

    r = await client.get(f"/api/v1/chats/{test_chat.id}", headers=headers)
    assert r.json()["pinned_message"]["id"] == msg_id

    r = await client.delete(
        f"/api/v1/chats/{test_chat.id}/pin?scope=local",
        headers=headers,
    )
    assert r.status_code == 200

    r = await client.get(f"/api/v1/chats/{test_chat.id}", headers=headers)
    assert r.json()["pinned_message"] is None