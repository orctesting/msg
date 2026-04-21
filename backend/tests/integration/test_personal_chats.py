import pytest
from httpx import AsyncClient

from tests.utils import create_user_and_login


@pytest.mark.asyncio
async def test_create_personal_chat_by_phone_registered(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000001")
    b = await create_user_and_login(client, phone="+79990000002")

    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    assert r.status_code == 201, r.text
    data = r.json()
    assert data["type"] == "personal"
    assert data["peer_user"] is not None
    assert data["peer_user"]["phone"] == b["phone"]
    assert data["peer_is_in_contacts"] is False
    assert data["peer_dismissed"] is False


@pytest.mark.asyncio
async def test_create_personal_chat_unregistered_phone_rejected(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000010")
    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": "+79990099999"},
    )
    assert r.status_code == 409


@pytest.mark.asyncio
async def test_create_personal_chat_self_rejected(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000020")
    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": a["phone"]},
    )
    assert r.status_code == 409


@pytest.mark.asyncio
async def test_create_personal_chat_idempotent(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000030")
    b = await create_user_and_login(client, phone="+79990000031")

    r1 = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    r2 = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    assert r1.status_code == 201
    assert r2.status_code == 201
    assert r1.json()["id"] == r2.json()["id"]


@pytest.mark.asyncio
async def test_personal_chat_invisible_to_peer_until_message(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000040")
    b = await create_user_and_login(client, phone="+79990000041")

    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    chat_id = r.json()["id"]

    rb = await client.get("/api/v1/chats", headers=b["headers"])
    assert rb.status_code == 200
    ids_b = [c["id"] for c in rb.json()["chats"]]
    assert chat_id not in ids_b

    ra = await client.get("/api/v1/chats", headers=a["headers"])
    ids_a = [c["id"] for c in ra.json()["chats"]]
    assert chat_id in ids_a


@pytest.mark.asyncio
async def test_personal_chat_becomes_visible_after_first_message(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000050")
    b = await create_user_and_login(client, phone="+79990000051")

    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    chat_id = r.json()["id"]

    sm = await client.post(
        f"/api/v1/chats/{chat_id}/messages",
        headers=a["headers"],
        json={"content": "hi"},
    )
    assert sm.status_code == 201, sm.text

    rb = await client.get("/api/v1/chats", headers=b["headers"])
    ids_b = [c["id"] for c in rb.json()["chats"]]
    assert chat_id in ids_b


@pytest.mark.asyncio
async def test_personal_chat_peer_in_contacts_flag(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000060")
    b = await create_user_and_login(client, phone="+79990000061")

    await client.post(
        "/api/v1/contacts",
        headers=a["headers"],
        json={"phone": b["phone"], "display_name": "Bob"},
    )

    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    assert r.status_code == 201
    data = r.json()
    assert data["peer_is_in_contacts"] is True
    assert data["name"] == "Bob"


@pytest.mark.asyncio
async def test_personal_chat_create_by_contact_id(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000070")
    b = await create_user_and_login(client, phone="+79990000071")

    rc = await client.post(
        "/api/v1/contacts",
        headers=a["headers"],
        json={"phone": b["phone"], "display_name": "Bobby"},
    )
    contact_id = rc.json()["id"]

    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"contact_id": contact_id},
    )
    assert r.status_code == 201
    assert r.json()["name"] == "Bobby"


@pytest.mark.asyncio
async def test_personal_chat_create_by_contact_id_unregistered(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990000080")

    rc = await client.post(
        "/api/v1/contacts",
        headers=a["headers"],
        json={"phone": "+79990099000", "display_name": "Ghost"},
    )
    contact_id = rc.json()["id"]

    r = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"contact_id": contact_id},
    )
    assert r.status_code == 409