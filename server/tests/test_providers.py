"""Tests for LLM provider clients and fallback chain."""

from __future__ import annotations

from unittest.mock import AsyncMock, MagicMock, patch

import pytest

from brain.brain_api import TokenBucket
from brain.models import ChatMessage, ChatParams, ModelTier
from brain.providers import (
    AnthropicProvider,
    GeminiProvider,
    LLMProviderError,
    OpenAICompatProvider,
    get_provider_chain,
    route_chat,
)

# ── TokenBucket ───────────────────────────────────────────────────────────────


class TestTokenBucket:
    def test_allows_within_capacity(self):
        bucket = TokenBucket(capacity=100, refill_per_sec=10.0)
        assert bucket.allow("device-1", cost=50) is True
        assert bucket.allow("device-1", cost=50) is True

    def test_denies_over_capacity(self):
        bucket = TokenBucket(capacity=10, refill_per_sec=0.0)
        assert bucket.allow("device-1", cost=10) is True
        assert bucket.allow("device-1", cost=1) is False

    def test_separate_keys(self):
        bucket = TokenBucket(capacity=10, refill_per_sec=0.0)
        assert bucket.allow("device-1", cost=10) is True
        assert bucket.allow("device-2", cost=10) is True  # separate bucket


# ── Provider initialization ───────────────────────────────────────────────────


def test_anthropic_provider_requires_key():
    with patch.dict("os.environ", {"ANTHROPIC_API_KEY": ""}, clear=False):
        with pytest.raises(LLMProviderError, match="ANTHROPIC_API_KEY"):
            AnthropicProvider()


def test_gemini_provider_requires_key():
    with patch.dict("os.environ", {"GEMINI_API_KEY": ""}, clear=False):
        with pytest.raises(LLMProviderError, match="GEMINI_API_KEY"):
            GeminiProvider()


def test_openai_compat_requires_key_except_ollama():
    with patch.dict("os.environ", {"MISSING_KEY": ""}, clear=False):
        with pytest.raises(LLMProviderError):
            OpenAICompatProvider(
                name="openrouter",
                api_key_env="MISSING_KEY",
                base_url="https://example.com/v1",
                model="test",
            )
    # Ollama should not raise
    OpenAICompatProvider(
        name="ollama",
        api_key_env="MISSING_KEY",
        base_url="http://localhost:11434/v1",
        model="llama3.1",
    )


# ── get_provider_chain ────────────────────────────────────────────────────────


def test_chain_t2_starts_with_gemini():
    with patch.dict(
        "os.environ",
        {"GEMINI_API_KEY": "fake", "OPENROUTER_API_KEY": "", "NVIDIA_API_KEY": ""},
        clear=False,
    ):
        chain = get_provider_chain(ModelTier.T2)
        assert chain[0].name == "google"


def test_chain_t3_starts_with_anthropic():
    with patch.dict(
        "os.environ",
        {"ANTHROPIC_API_KEY": "fake", "OPENROUTER_API_KEY": "", "NVIDIA_API_KEY": ""},
        clear=False,
    ):
        chain = get_provider_chain(ModelTier.T3)
        assert chain[0].name == "anthropic"


def test_chain_raises_when_no_providers():
    with patch.dict(
        "os.environ",
        {
            "GEMINI_API_KEY": "",
            "ANTHROPIC_API_KEY": "",
            "OPENROUTER_API_KEY": "",
            "NVIDIA_API_KEY": "",
            "OLLAMA_API_KEY": "",
        },
        clear=False,
    ):
        # Ollama doesn't require a key, so it should still be present.
        # But let's patch it out to test the empty case.
        with patch("brain.providers._build_fallbacks", return_value=[]):
            with pytest.raises(LLMProviderError, match="No providers available"):
                get_provider_chain(ModelTier.T2)


# ── route_chat fallback ──────────────────────────────────────────────────────


@pytest.mark.asyncio
async def test_route_chat_falls_back_on_failure():
    """If primary fails, should try the next provider in chain."""
    msgs = [ChatMessage(role="user", content="hello")]
    params = ChatParams()

    mock_primary = MagicMock()
    mock_primary.name = "primary"
    mock_primary.model = "p-model"
    mock_primary.chat = AsyncMock(side_effect=LLMProviderError("primary down"))

    mock_fallback = MagicMock()
    mock_fallback.name = "fallback"
    mock_fallback.model = "f-model"

    from brain.models import ChatChoice, ChatResponse, UsageInfo

    mock_fallback.chat = AsyncMock(
        return_value=ChatResponse(
            id="fb-1",
            model="f-model",
            provider="fallback",
            tier=ModelTier.T2,
            choices=[
                ChatChoice(
                    message=ChatMessage(role="assistant", content="from fallback"),
                )
            ],
            usage=UsageInfo(prompt_tokens=5, completion_tokens=3, total_tokens=8),
            latency_ms=100.0,
        )
    )

    with patch("brain.providers.get_provider_chain", return_value=[mock_primary, mock_fallback]):
        resp = await route_chat(msgs, params, ModelTier.T2)
        assert resp.provider == "fallback"
        assert resp.choices[0].message.content == "from fallback"
        mock_primary.chat.assert_awaited_once()
        mock_fallback.chat.assert_awaited_once()


@pytest.mark.asyncio
async def test_route_chat_raises_when_all_fail():
    msgs = [ChatMessage(role="user", content="hello")]
    params = ChatParams()

    mock_p = MagicMock()
    mock_p.name = "dead"
    mock_p.model = "d"
    mock_p.chat = AsyncMock(side_effect=LLMProviderError("nope"))

    with patch("brain.providers.get_provider_chain", return_value=[mock_p]):
        with pytest.raises(LLMProviderError, match="All providers exhausted"):
            await route_chat(msgs, params, ModelTier.T2)


# ── Pydantic model validation ────────────────────────────────────────────────


def test_chat_message_rejects_invalid_role():
    from pydantic import ValidationError

    with pytest.raises(ValidationError):
        ChatMessage(role="hacker", content="x")


def test_chat_params_clamps_temperature():
    from pydantic import ValidationError

    with pytest.raises(ValidationError):
        ChatParams(temperature=5.0)
