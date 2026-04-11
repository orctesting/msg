import uuid
from unittest.mock import AsyncMock, patch

import pytest
from starlette.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from app.main import app
from app.core.security import create_access_token
from app.db.models.user import User


def _make_fake_user(**kwargs):
    """Create a detached User instance via normal constructor."""
    defaults = {
        "id": uuid.uuid4(),
        "phone": "+70001112233",
        "display_name": "WS Test",
        "role": "user",
        "is_active": True,
    }
    defaults.update(kwargs)
    user = User(
        phone=defaults["phone"],
        display_name=defaults["display_name"],
        role=defaults["role"],
        is_active=defaults["is_active"],
    )
    # Set id without going through SA session
    object.__setattr__(user, "id", defaults["id"])
    return user


def test_ws_rejects_bad_token():
    tc = TestClient(app)
    with pytest.raises(WebSocketDisconnect) as exc_info:
        with tc.websocket_connect("/api/v1/ws?token=invalid"):
            pass
    assert exc_info.value.code == 4001


def test_ws_ping_pong():
    fake_user = _make_fake_user()
    token = create_access_token(fake_user.id, fake_user.role)

    with (
        patch(
            "app.api.v1.ws._authenticate_ws",
            new_callable=AsyncMock,
            return_value=fake_user,
        ),
        patch(
            "app.api.v1.ws._get_user_chat_ids",
            new_callable=AsyncMock,
            return_value=[],
        ),
    ):
        tc = TestClient(app)
        with tc.websocket_connect(f"/api/v1/ws?token={token}") as ws:
            ws.send_json({"type": "ping"})
            data = ws.receive_json()
            assert data["type"] == "pong"


def test_ws_typing_event():
    fake_user = _make_fake_user(phone="+70001112234", display_name="Typer")
    token = create_access_token(fake_user.id, fake_user.role)
    chat_id = str(uuid.uuid4())

    with (
        patch(
            "app.api.v1.ws._authenticate_ws",
            new_callable=AsyncMock,
            return_value=fake_user,
        ),
        patch(
            "app.api.v1.ws._get_user_chat_ids",
            new_callable=AsyncMock,
            return_value=[uuid.UUID(chat_id)],
        ),
        patch(
            "app.services.ws_manager.ws_manager.publish_event",
            new_callable=AsyncMock,
        ),
    ):
        tc = TestClient(app)
        with tc.websocket_connect(f"/api/v1/ws?token={token}") as ws:
            ws.send_json({"type": "typing", "chat_id": chat_id})
            ws.send_json({"type": "ping"})
            data = ws.receive_json()
            assert data["type"] == "pong"