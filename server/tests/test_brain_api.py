"""Tests for the LLM proxy endpoint (POST /v1/llm/chat)."""

from __future__ import annotations

import json
from unittest.mock import AsyncMock, patch

import pytest
from fastapi.testclient import TestClient

from brain.brain_api import app
from brain.models import (
    ChatChoice,
    ChatMessage,
    ChatResponse,
    ModelTier,
    StreamChunk,
    UsageInfo,
)
from brain.providers import LLMProviderError

# ── Fixtures ──────────────────────────────────────────────────────────────────


@pytest.fixture()
def client():
    """TestClient with auth disabled (NEURON_SECRET_TOKEN empty)."""
    with patch.dict("os.environ", {"NEURON_SECRET_TOKEN": ""}, clear=False):
        with TestClient(app) as c:
            yield c


@pytest.fixture()
def auth_client():
    """TestClient with auth enabled."""
    with patch.dict("os.environ", {"NEURON_SECRET_TOKEN": "test-secret"}, clear=False):
        # Reload the module-level variable
        import brain.brain_api as mod

        original = mod.NEURON_SECRET_TOKEN
        mod.NEURON_SECRET_TOKEN = "test-secret"
        with TestClient(app) as c:
            yield c
        mod.NEURON_SECRET_TOKEN = original


def _make_chat_response(
    content: str = "Hello!",
    provider: str = "mock",
    model: str = "mock-model",
    tier: ModelTier = ModelTier.T2,
) -> ChatResponse:
    return ChatResponse(
        id="test-123",
        model=model,
        provider=provider,
        tier=tier,
        choices=[
            ChatChoice(
                message=ChatMessage(role="assistant", content=content),
                finish_reason="stop",
            )
        ],
        usage=UsageInfo(prompt_tokens=10, completion_tokens=5, total_tokens=15),
        latency_ms=42.0,
    )


def _make_request_body(tier: str = "t2", stream: bool = False) -> dict:
    return {
        "model_tier": tier,
        "messages": [{"role": "user", "content": "Say hello"}],
        "params": {"temperature": 0.7, "max_tokens": 256},
        "stream": stream,
    }


# ── Health ────────────────────────────────────────────────────────────────────


def test_health(client: TestClient):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.json()["status"] == "ok"


# ── Auth ──────────────────────────────────────────────────────────────────────


def test_missing_auth_header_rejected(client: TestClient):
    """FastAPI HTTPBearer rejects requests without Authorization header."""
    resp = client.post("/v1/llm/chat", json=_make_request_body())
    assert resp.status_code in (401, 403)


def test_invalid_token_returns_401(auth_client: TestClient):
    resp = auth_client.post(
        "/v1/llm/chat",
        json=_make_request_body(),
        headers={"Authorization": "Bearer wrong-token"},
    )
    assert resp.status_code == 401


def test_valid_token_accepted(auth_client: TestClient):
    with patch("brain.brain_api.route_chat", new_callable=AsyncMock) as mock_route:
        mock_route.return_value = _make_chat_response()
        resp = auth_client.post(
            "/v1/llm/chat",
            json=_make_request_body(),
            headers={"Authorization": "Bearer test-secret"},
        )
        assert resp.status_code == 200


# ── Non-streaming chat ────────────────────────────────────────────────────────


def test_chat_t2_non_streaming(client: TestClient):
    with patch("brain.brain_api.route_chat", new_callable=AsyncMock) as mock_route:
        mock_route.return_value = _make_chat_response(
            content="Hi from Gemini", provider="google", tier=ModelTier.T2
        )
        resp = client.post(
            "/v1/llm/chat",
            json=_make_request_body(tier="t2"),
            headers={"Authorization": "Bearer any"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["provider"] == "google"
        assert data["choices"][0]["message"]["content"] == "Hi from Gemini"
        assert data["tier"] == "t2"


def test_chat_t3_non_streaming(client: TestClient):
    with patch("brain.brain_api.route_chat", new_callable=AsyncMock) as mock_route:
        mock_route.return_value = _make_chat_response(
            content="Hi from Claude", provider="anthropic", tier=ModelTier.T3
        )
        resp = client.post(
            "/v1/llm/chat",
            json=_make_request_body(tier="t3"),
            headers={"Authorization": "Bearer any"},
        )
        assert resp.status_code == 200
        data = resp.json()
        assert data["provider"] == "anthropic"
        assert data["tier"] == "t3"


# ── Streaming chat ────────────────────────────────────────────────────────────


def test_chat_streaming(client: TestClient):
    async def mock_stream(*args, **kwargs):
        yield StreamChunk(id="s1", model="m", provider="google", delta="Hello")
        yield StreamChunk(id="s1", model="m", provider="google", delta=" world")
        yield StreamChunk(id="s1", model="m", provider="google", delta="", finish_reason="stop")

    with patch("brain.brain_api.route_chat_stream", side_effect=mock_stream):
        resp = client.post(
            "/v1/llm/chat",
            json=_make_request_body(stream=True),
            headers={"Authorization": "Bearer any"},
        )
        assert resp.status_code == 200
        assert resp.headers["content-type"].startswith("text/event-stream")

        lines = resp.text.strip().split("\n\n")
        # Should have 3 data chunks + [DONE]
        assert len(lines) == 4
        assert lines[-1] == "data: [DONE]"

        # Parse first chunk
        first = json.loads(lines[0].removeprefix("data: "))
        assert first["delta"] == "Hello"


# ── Validation ────────────────────────────────────────────────────────────────


def test_invalid_tier_rejected(client: TestClient):
    body = _make_request_body()
    body["model_tier"] = "t0"  # on-device only, not proxied
    resp = client.post(
        "/v1/llm/chat",
        json=body,
        headers={"Authorization": "Bearer any"},
    )
    assert resp.status_code == 422


def test_empty_messages_rejected(client: TestClient):
    body = _make_request_body()
    body["messages"] = []
    resp = client.post(
        "/v1/llm/chat",
        json=body,
        headers={"Authorization": "Bearer any"},
    )
    assert resp.status_code == 422


def test_invalid_role_rejected(client: TestClient):
    body = _make_request_body()
    body["messages"] = [{"role": "hacker", "content": "inject"}]
    resp = client.post(
        "/v1/llm/chat",
        json=body,
        headers={"Authorization": "Bearer any"},
    )
    assert resp.status_code == 422


# ── Provider errors ───────────────────────────────────────────────────────────


def test_provider_error_returns_502(client: TestClient):
    with patch("brain.brain_api.route_chat", new_callable=AsyncMock) as mock_route:
        mock_route.side_effect = LLMProviderError("All providers down")
        resp = client.post(
            "/v1/llm/chat",
            json=_make_request_body(),
            headers={"Authorization": "Bearer any"},
        )
        assert resp.status_code == 502
        assert "llm_provider_error" in resp.json()["error"]


# ── Rate limiting ─────────────────────────────────────────────────────────────


def test_rate_limit_enforced(client: TestClient):
    """Exhaust the token bucket and verify 429."""
    with patch("brain.brain_api.route_chat", new_callable=AsyncMock) as mock_route:
        mock_route.return_value = _make_chat_response()

        # Patch the rate limiter to always deny
        with patch("brain.brain_api._rate_limiter") as mock_limiter:
            mock_limiter.allow.return_value = False
            resp = client.post(
                "/v1/llm/chat",
                json=_make_request_body(),
                headers={"Authorization": "Bearer any"},
            )
            assert resp.status_code == 429
