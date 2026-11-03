import base64
import os
import time
from pathlib import Path
from typing import Any

import jwt
import pytest
from fastapi.testclient import TestClient

TEST_JWT_SECRET = "test-only-secret-for-ai-labs-1234567890"
JWT_ISSUER = "workshop-auth"
JWT_AUDIENCE = "workshop-api"
os.environ["WORKSHOP_JWT_SECRET"] = TEST_JWT_SECRET

from app.main import create_app


def token(
    username: str = "student",
    role: str = "STUDENT",
    *,
    secret: str = TEST_JWT_SECRET,
    **claims: Any,
) -> str:
    now = int(time.time())
    payload: dict[str, Any] = {
        "iss": JWT_ISSUER,
        "aud": JWT_AUDIENCE,
        "sub": username,
        "role": role,
        "iat": now,
        "exp": now + 3600,
    }
    payload.update(claims)
    return jwt.encode(payload, secret, algorithm="HS256")


def headers(username: str = "student", role: str = "STUDENT") -> dict[str, str]:
    return {"Authorization": f"Bearer {token(username, role)}"}


def test_lists_seeded_tasks(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=headers())
    assert response.status_code == 200
    assert len(response.json()) == 2


def test_creates_task_and_summary(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=headers(),
            json={"title": "Add tests", "description": "Improve coverage", "status": "TODO", "priority": "HIGH"},
        )
        summary = client.get("/api/summary", headers=headers())
    assert created.status_code == 201
    assert created.json()["owner"] == "student"
    assert summary.json()["total"] == 3


def test_uploads_requirement(tmp_path: Path) -> None:
    upload_dir = tmp_path / "uploads"
    app = create_app(str(tmp_path / "workshop.db"), str(upload_dir))
    with TestClient(app) as client:
        response = client.post(
            "/api/requirements",
            headers=headers(),
            files={"file": ("requirements.txt", b"Login must be accessible", "text/plain")},
        )
    assert response.status_code == 201
    assert (upload_dir / "requirements.txt").exists()


def bearer_headers() -> dict[str, str]:
    return headers()


def test_create_applies_default_fields_when_absent(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post("/api/tasks", headers=bearer_headers(), json={})
    assert created.status_code == 201
    body = created.json()
    assert body["title"] == ""
    assert body["status"] == "TODO"
    assert body["priority"] == "MEDIUM"
    assert body["owner"] == "student"


def test_create_keeps_quote_in_title_literal(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        created = client.post(
            "/api/tasks",
            headers=bearer_headers(),
            json={"title": "O'Brien", "description": "d", "status": "TODO", "priority": "LOW"},
        )
    assert created.status_code == 201
    assert created.json()["title"] == "O'Brien"


def test_owner_query_is_not_interpreted_as_sql(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks",
            headers=bearer_headers(),
            params={"owner": "x' OR '1'='1"},
        )
    assert response.status_code == 200
    assert response.json() == []


def test_rejects_invalid_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks", headers={"Authorization": "Bearer invalid"}
        )
    assert response.status_code == 401


@pytest.mark.parametrize(
    ("method", "path"),
    [
        ("GET", "/api/tasks"),
        ("POST", "/api/tasks"),
        ("GET", "/api/summary"),
        ("POST", "/api/requirements"),
        ("GET", "/api/backend-health"),
    ],
)
def test_rejects_invalid_token_for_protected_routes(
    method: str, path: str, tmp_path: Path
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    request_options: dict[str, Any] = {
        "headers": {"Authorization": "Bearer invalid"}
    }
    if path == "/api/tasks" and method == "POST":
        request_options["json"] = {}
    if path == "/api/requirements":
        request_options["files"] = {"file": ("requirements.txt", b"test")}

    with TestClient(app) as client:
        response = client.request(method, path, **request_options)

    assert response.status_code == 401


def test_rejects_token_signed_with_another_secret(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    invalid_headers = {
        "Authorization": f"Bearer {token(secret=TEST_JWT_SECRET + '-wrong')}"
    }
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=invalid_headers)
    assert response.status_code == 401


def test_rejects_token_for_another_audience(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    invalid_headers = {"Authorization": f"Bearer {token(aud='another-api')}"}
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=invalid_headers)
    assert response.status_code == 401


def test_rejects_expired_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    invalid_headers = {
        "Authorization": f"Bearer {token(exp=int(time.time()) - 1)}"
    }
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=invalid_headers)
    assert response.status_code == 401


def test_rejects_legacy_unsigned_token(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    legacy_payload = (
        f"student|STUDENT|{int(time.time()) + 3600}|WORKSHOP_SECRET_2026"
    )
    legacy_token = base64.b64encode(legacy_payload.encode()).decode()
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks", headers={"Authorization": f"Bearer {legacy_token}"}
        )
    assert response.status_code == 401


def test_accepts_raw_jwt_authorization_value(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get(
            "/api/tasks", headers={"Authorization": token()}
        )
    assert response.status_code == 200


def test_rejects_empty_authorization_value(tmp_path: Path) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers={"Authorization": ""})
    assert response.status_code == 401


@pytest.mark.parametrize("claims", [{"sub": ""}, {"role": ""}])
def test_rejects_empty_identity_claims(
    claims: dict[str, str], tmp_path: Path
) -> None:
    app = create_app(str(tmp_path / "workshop.db"), str(tmp_path / "uploads"))
    invalid_headers = {"Authorization": f"Bearer {token(**claims)}"}
    with TestClient(app) as client:
        response = client.get("/api/tasks", headers=invalid_headers)
    assert response.status_code == 401


def test_requires_signing_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.delenv("WORKSHOP_JWT_SECRET")
    with pytest.raises(RuntimeError, match="WORKSHOP_JWT_SECRET"):
        create_app()


def test_rejects_short_signing_secret(monkeypatch: pytest.MonkeyPatch) -> None:
    monkeypatch.setenv("WORKSHOP_JWT_SECRET", "too-short")
    with pytest.raises(RuntimeError, match="at least 32 bytes"):
        create_app()
