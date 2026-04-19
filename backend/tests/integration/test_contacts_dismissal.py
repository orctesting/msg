import pytest
from httpx import AsyncClient

from tests.utils import create_user_and_login


@pytest.mark.asyncio
async def test_dismiss_peer_sets_flag_in_personal_chat(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990001001")
    b = await create_user_and_login(client, phone="+79990001002")

    rc = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    assert rc.status_code == 201
    chat = rc.json()
    assert chat["peer_dismissed"] is False

    rd = await client.post(
        "/api/v1/contacts/dismiss",
        headers=a["headers"],
        json={"peer_user_id": b["user_id"]},
    )
    assert rd.status_code == 200

    rg = await client.get(f"/api/v1/chats/{chat['id']}", headers=a["headers"])
    assert rg.status_code == 200
    assert rg.json()["peer_dismissed"] is True


@pytest.mark.asyncio
async def test_dismiss_peer_idempotent(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990001010")
    b = await create_user_and_login(client, phone="+79990001011")

    r1 = await client.post(
        "/api/v1/contacts/dismiss",
        headers=a["headers"],
        json={"peer_user_id": b["user_id"]},
    )
    r2 = await client.post(
        "/api/v1/contacts/dismiss",
        headers=a["headers"],
        json={"peer_user_id": b["user_id"]},
    )
    assert r1.status_code == 200
    assert r2.status_code == 200


@pytest.mark.asyncio
async def test_dismiss_self_rejected(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990001020")
    r = await client.post(
        "/api/v1/contacts/dismiss",
        headers=a["headers"],
        json={"peer_user_id": a["user_id"]},
    )
    assert r.status_code == 409


@pytest.mark.asyncio
async def test_adding_contact_clears_dismissal(client: AsyncClient):
    a = await create_user_and_login(client, phone="+79990001030")
    b = await create_user_and_login(client, phone="+79990001031")

    await client.post(
        "/api/v1/contacts/dismiss",
        headers=a["headers"],
        json={"peer_user_id": b["user_id"]},
    )

    await client.post(
        "/api/v1/contacts",
        headers=a["headers"],
        json={"phone": b["phone"], "display_name": "Bob"},
    )

    rc = await client.post(
        "/api/v1/chats/personal",
        headers=a["headers"],
        json={"phone": b["phone"]},
    )
    assert rc.status_code == 201
    data = rc.json()
    assert data["peer_is_in_contacts"] is True
    assert data["peer_dismissed"] is False