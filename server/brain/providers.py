"""LLM provider clients with fallback chain support."""

from __future__ import annotations

import asyncio
import os
import time
import uuid
from collections.abc import AsyncIterator

import structlog

from brain.models import (
    ChatChoice,
    ChatMessage,
    ChatParams,
    ChatResponse,
    ModelTier,
    StreamChunk,
    UsageInfo,
)

log = structlog.get_logger(__name__)

# ── Default timeout for all LLM calls ────────────────────────────────────────
LLM_TIMEOUT_SEC = float(os.getenv("LLM_TIMEOUT_SEC", "30"))


# ── Provider interface ────────────────────────────────────────────────────────


class LLMProviderError(Exception):
    """Raised when a provider call fails (triggers fallback)."""


class LLMProvider:
    """Base class for LLM providers."""

    name: str = "base"
    model: str = ""

    async def chat(self, messages: list[ChatMessage], params: ChatParams) -> ChatResponse:
        raise NotImplementedError

    async def chat_stream(
        self, messages: list[ChatMessage], params: ChatParams
    ) -> AsyncIterator[StreamChunk]:
        raise NotImplementedError
        yield  # pragma: no cover — make this an async generator


# ── Anthropic (Claude) ────────────────────────────────────────────────────────


class AnthropicProvider(LLMProvider):
    name = "anthropic"
    model = "claude-sonnet-4-20250514"

    def __init__(self) -> None:
        import anthropic

        api_key = os.environ.get("ANTHROPIC_API_KEY", "")
        if not api_key:
            raise LLMProviderError("ANTHROPIC_API_KEY not set")
        self.client = anthropic.AsyncAnthropic(api_key=api_key)

    async def chat(self, messages: list[ChatMessage], params: ChatParams) -> ChatResponse:
        import anthropic

        system_msg = ""
        user_msgs: list[dict[str, str]] = []
        for m in messages:
            if m.role == "system":
                system_msg = m.content
            else:
                user_msgs.append({"role": m.role, "content": m.content})

        start = time.monotonic()
        try:
            async with asyncio.timeout(LLM_TIMEOUT_SEC):
                resp = await self.client.messages.create(
                    model=self.model,
                    max_tokens=params.max_tokens,
                    temperature=params.temperature,
                    top_p=params.top_p,
                    system=system_msg if system_msg else anthropic.NOT_GIVEN,
                    messages=user_msgs,
                )
        except Exception as e:
            raise LLMProviderError(f"Anthropic call failed: {e}") from e
        elapsed = (time.monotonic() - start) * 1000

        content_text = "".join(block.text for block in resp.content if block.type == "text")
        return ChatResponse(
            id=resp.id,
            model=resp.model,
            provider=self.name,
            tier=ModelTier.T3,
            choices=[
                ChatChoice(
                    message=ChatMessage(role="assistant", content=content_text),
                    finish_reason=resp.stop_reason or "stop",
                )
            ],
            usage=UsageInfo(
                prompt_tokens=resp.usage.input_tokens,
                completion_tokens=resp.usage.output_tokens,
                total_tokens=resp.usage.input_tokens + resp.usage.output_tokens,
            ),
            latency_ms=elapsed,
        )

    async def chat_stream(
        self, messages: list[ChatMessage], params: ChatParams
    ) -> AsyncIterator[StreamChunk]:
        import anthropic

        system_msg = ""
        user_msgs: list[dict[str, str]] = []
        for m in messages:
            if m.role == "system":
                system_msg = m.content
            else:
                user_msgs.append({"role": m.role, "content": m.content})

        try:
            async with asyncio.timeout(LLM_TIMEOUT_SEC):
                async with self.client.messages.stream(
                    model=self.model,
                    max_tokens=params.max_tokens,
                    temperature=params.temperature,
                    top_p=params.top_p,
                    system=system_msg if system_msg else anthropic.NOT_GIVEN,
                    messages=user_msgs,
                ) as stream:
                    msg_id = f"chatcmpl-{uuid.uuid4().hex[:12]}"
                    async for text in stream.text_stream:
                        yield StreamChunk(
                            id=msg_id,
                            model=self.model,
                            provider=self.name,
                            delta=text,
                        )
                    yield StreamChunk(
                        id=msg_id,
                        model=self.model,
                        provider=self.name,
                        delta="",
                        finish_reason="stop",
                    )
        except Exception as e:
            raise LLMProviderError(f"Anthropic stream failed: {e}") from e


# ── Google Gemini ─────────────────────────────────────────────────────────────


class GeminiProvider(LLMProvider):
    name = "google"
    model = "gemini-2.5-flash"

    def __init__(self) -> None:
        api_key = os.environ.get("GEMINI_API_KEY", "")
        if not api_key:
            raise LLMProviderError("GEMINI_API_KEY not set")
        try:
            from google import genai
        except ImportError as e:
            raise LLMProviderError(f"google-genai package not installed: {e}") from e
        self.client = genai.Client(api_key=api_key)

    async def chat(self, messages: list[ChatMessage], params: ChatParams) -> ChatResponse:
        from google.genai import types

        contents: list[types.Content] = []
        system_instruction: str | None = None
        for m in messages:
            if m.role == "system":
                system_instruction = m.content
            else:
                role = "model" if m.role == "assistant" else "user"
                contents.append(types.Content(role=role, parts=[types.Part(text=m.content)]))

        config = types.GenerateContentConfig(
            temperature=params.temperature,
            max_output_tokens=params.max_tokens,
            top_p=params.top_p,
            system_instruction=system_instruction,
        )

        start = time.monotonic()
        try:
            async with asyncio.timeout(LLM_TIMEOUT_SEC):
                resp = await self.client.aio.models.generate_content(
                    model=self.model,
                    contents=contents,
                    config=config,
                )
        except Exception as e:
            raise LLMProviderError(f"Gemini call failed: {e}") from e
        elapsed = (time.monotonic() - start) * 1000

        text = resp.text or ""
        usage_meta = resp.usage_metadata
        return ChatResponse(
            id=f"gemini-{uuid.uuid4().hex[:12]}",
            model=self.model,
            provider=self.name,
            tier=ModelTier.T2,
            choices=[
                ChatChoice(
                    message=ChatMessage(role="assistant", content=text),
                    finish_reason="stop",
                )
            ],
            usage=UsageInfo(
                prompt_tokens=getattr(usage_meta, "prompt_token_count", 0) or 0,
                completion_tokens=getattr(usage_meta, "candidates_token_count", 0) or 0,
                total_tokens=getattr(usage_meta, "total_token_count", 0) or 0,
            ),
            latency_ms=elapsed,
        )

    async def chat_stream(
        self, messages: list[ChatMessage], params: ChatParams
    ) -> AsyncIterator[StreamChunk]:
        from google.genai import types

        contents: list[types.Content] = []
        system_instruction: str | None = None
        for m in messages:
            if m.role == "system":
                system_instruction = m.content
            else:
                role = "model" if m.role == "assistant" else "user"
                contents.append(types.Content(role=role, parts=[types.Part(text=m.content)]))

        config = types.GenerateContentConfig(
            temperature=params.temperature,
            max_output_tokens=params.max_tokens,
            top_p=params.top_p,
            system_instruction=system_instruction,
        )

        try:
            async with asyncio.timeout(LLM_TIMEOUT_SEC):
                msg_id = f"gemini-{uuid.uuid4().hex[:12]}"
                async for chunk in self.client.aio.models.generate_content_stream(
                    model=self.model,
                    contents=contents,
                    config=config,
                ):
                    yield StreamChunk(
                        id=msg_id,
                        model=self.model,
                        provider=self.name,
                        delta=chunk.text or "",
                    )
                yield StreamChunk(
                    id=msg_id,
                    model=self.model,
                    provider=self.name,
                    delta="",
                    finish_reason="stop",
                )
        except Exception as e:
            raise LLMProviderError(f"Gemini stream failed: {e}") from e


# ── OpenAI-compatible (OpenRouter, NVIDIA, Ollama) ────────────────────────────


class OpenAICompatProvider(LLMProvider):
    """Works with any OpenAI-API-compatible endpoint."""

    def __init__(self, name: str, api_key_env: str, base_url: str, model: str) -> None:
        import openai

        self.name = name
        self.model = model
        api_key = os.environ.get(api_key_env, "")
        if not api_key and name != "ollama":
            raise LLMProviderError(f"{api_key_env} not set")
        self.client = openai.AsyncOpenAI(
            api_key=api_key or "ollama",
            base_url=base_url,
        )

    async def chat(self, messages: list[ChatMessage], params: ChatParams) -> ChatResponse:
        oai_msgs = [{"role": m.role, "content": m.content} for m in messages]

        start = time.monotonic()
        try:
            async with asyncio.timeout(LLM_TIMEOUT_SEC):
                resp = await self.client.chat.completions.create(
                    model=self.model,
                    messages=oai_msgs,
                    temperature=params.temperature,
                    max_tokens=params.max_tokens,
                    top_p=params.top_p,
                    stream=False,
                )
        except Exception as e:
            raise LLMProviderError(f"{self.name} call failed: {e}") from e
        elapsed = (time.monotonic() - start) * 1000

        choice = resp.choices[0] if resp.choices else None
        content = choice.message.content if choice and choice.message else ""
        finish = choice.finish_reason if choice else "stop"
        usage = resp.usage
        return ChatResponse(
            id=resp.id or f"{self.name}-{uuid.uuid4().hex[:12]}",
            model=resp.model or self.model,
            provider=self.name,
            tier=ModelTier.T2,
            choices=[
                ChatChoice(
                    message=ChatMessage(role="assistant", content=content or ""),
                    finish_reason=finish or "stop",
                )
            ],
            usage=UsageInfo(
                prompt_tokens=getattr(usage, "prompt_tokens", 0) or 0,
                completion_tokens=getattr(usage, "completion_tokens", 0) or 0,
                total_tokens=getattr(usage, "total_tokens", 0) or 0,
            ),
            latency_ms=elapsed,
        )

    async def chat_stream(
        self, messages: list[ChatMessage], params: ChatParams
    ) -> AsyncIterator[StreamChunk]:
        oai_msgs = [{"role": m.role, "content": m.content} for m in messages]

        try:
            async with asyncio.timeout(LLM_TIMEOUT_SEC):
                stream = await self.client.chat.completions.create(
                    model=self.model,
                    messages=oai_msgs,
                    temperature=params.temperature,
                    max_tokens=params.max_tokens,
                    top_p=params.top_p,
                    stream=True,
                )
                msg_id = f"{self.name}-{uuid.uuid4().hex[:12]}"
                async for chunk in stream:
                    delta = ""
                    finish = None
                    if chunk.choices:
                        c = chunk.choices[0]
                        delta = c.delta.content or "" if c.delta else ""
                        finish = c.finish_reason
                    yield StreamChunk(
                        id=msg_id,
                        model=self.model,
                        provider=self.name,
                        delta=delta,
                        finish_reason=finish,
                    )
        except Exception as e:
            raise LLMProviderError(f"{self.name} stream failed: {e}") from e


# ── Provider registry & fallback chains ───────────────────────────────────────

# T2: primary = Gemini Flash, fallbacks = OpenRouter → NVIDIA → Ollama
# T3: primary = Anthropic Claude, fallbacks = OpenRouter → NVIDIA → Ollama

FALLBACK_CONFIGS: list[tuple[str, str, str, str]] = [
    # (name, api_key_env, base_url, model)
    (
        "openrouter",
        "OPENROUTER_API_KEY",
        "https://openrouter.ai/api/v1",
        "google/gemini-2.5-flash",
    ),
    (
        "nvidia",
        "NVIDIA_API_KEY",
        "https://integrate.api.nvidia.com/v1",
        "meta/llama-3.1-70b-instruct",
    ),
    (
        "ollama",
        "OLLAMA_API_KEY",
        os.getenv("OLLAMA_BASE_URL", "http://localhost:11434/v1"),
        os.getenv("OLLAMA_MODEL", "llama3.1"),
    ),
]


def _build_fallbacks() -> list[LLMProvider]:
    """Build fallback providers, skipping those without keys."""
    providers: list[LLMProvider] = []
    for name, key_env, base_url, model in FALLBACK_CONFIGS:
        try:
            providers.append(
                OpenAICompatProvider(name=name, api_key_env=key_env, base_url=base_url, model=model)
            )
        except LLMProviderError:
            log.info("fallback_provider_skipped", provider=name, reason="no_api_key")
    return providers


def get_provider_chain(tier: ModelTier) -> list[LLMProvider]:
    """Return ordered provider list: [primary, ...fallbacks]."""
    chain: list[LLMProvider] = []

    if tier == ModelTier.T2:
        try:
            chain.append(GeminiProvider())
        except LLMProviderError:
            log.warning("primary_provider_unavailable", tier="t2", provider="google")
    elif tier == ModelTier.T3:
        try:
            chain.append(AnthropicProvider())
        except LLMProviderError:
            log.warning("primary_provider_unavailable", tier="t3", provider="anthropic")

    chain.extend(_build_fallbacks())

    if not chain:
        raise LLMProviderError(f"No providers available for tier {tier.value}")

    return chain


async def route_chat(
    messages: list[ChatMessage],
    params: ChatParams,
    tier: ModelTier,
) -> ChatResponse:
    """Route a chat request through the provider chain with fallback."""
    chain = get_provider_chain(tier)
    last_err: Exception | None = None

    for provider in chain:
        try:
            log.info(
                "llm_call_start",
                provider=provider.name,
                model=provider.model,
                tier=tier.value,
            )
            resp = await provider.chat(messages, params)
            resp.tier = tier
            log.info(
                "llm_call_complete",
                provider=provider.name,
                model=resp.model,
                tier=tier.value,
                latency_ms=round(resp.latency_ms, 1),
                tokens=resp.usage.total_tokens,
            )
            return resp
        except LLMProviderError as e:
            last_err = e
            log.warning(
                "llm_provider_failed",
                provider=provider.name,
                error=str(e),
                tier=tier.value,
            )

    raise LLMProviderError(f"All providers exhausted for tier {tier.value}: {last_err}")


async def route_chat_stream(
    messages: list[ChatMessage],
    params: ChatParams,
    tier: ModelTier,
) -> AsyncIterator[StreamChunk]:
    """Route a streaming chat request through the provider chain with fallback."""
    chain = get_provider_chain(tier)
    last_err: Exception | None = None

    for provider in chain:
        try:
            log.info(
                "llm_stream_start",
                provider=provider.name,
                model=provider.model,
                tier=tier.value,
            )
            async for chunk in provider.chat_stream(messages, params):
                yield chunk
            log.info(
                "llm_stream_complete",
                provider=provider.name,
                tier=tier.value,
            )
            return
        except LLMProviderError as e:
            last_err = e
            log.warning(
                "llm_stream_provider_failed",
                provider=provider.name,
                error=str(e),
                tier=tier.value,
            )

    raise LLMProviderError(f"All stream providers exhausted for tier {tier.value}: {last_err}")
