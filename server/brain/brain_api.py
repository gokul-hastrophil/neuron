"""
Neuron Brain API — LLM proxy endpoint.

Routes cloud LLM requests from the Android client so API keys
never ship on-device.  Supports tiered routing (T2/T3) with
automatic fallback chain and per-device rate limiting.

Usage:
    uvicorn brain.brain_api:app --reload --port 8384
"""

from __future__ import annotations

import json
import os
import time
from contextlib import asynccontextmanager
from typing import Any

import structlog
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.responses import JSONResponse, StreamingResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from brain.models import ChatRequest, ErrorResponse
from brain.providers import LLMProviderError, route_chat, route_chat_stream

log = structlog.get_logger(__name__)

# ── Configuration ─────────────────────────────────────────────────────────────

NEURON_SECRET_TOKEN = os.environ.get("NEURON_SECRET_TOKEN", "")

# Rate limit: max tokens per minute per device
RATE_LIMIT_TOKENS_PER_MIN = int(os.getenv("RATE_LIMIT_TOKENS_PER_MIN", "100000"))

# ── Rate limiter (token-bucket per device) ────────────────────────────────────


class TokenBucket:
    """Simple per-device token-rate limiter."""

    def __init__(self, capacity: int, refill_per_sec: float) -> None:
        self.capacity = capacity
        self.refill_per_sec = refill_per_sec
        self._buckets: dict[str, tuple[float, float]] = {}  # key -> (tokens, last_ts)

    def allow(self, key: str, cost: int = 1) -> bool:
        now = time.monotonic()
        tokens, last_ts = self._buckets.get(key, (float(self.capacity), now))
        elapsed = now - last_ts
        tokens = min(self.capacity, tokens + elapsed * self.refill_per_sec)
        if tokens >= cost:
            self._buckets[key] = (tokens - cost, now)
            return True
        self._buckets[key] = (tokens, now)
        return False


_rate_limiter = TokenBucket(
    capacity=RATE_LIMIT_TOKENS_PER_MIN,
    refill_per_sec=RATE_LIMIT_TOKENS_PER_MIN / 60.0,
)


# ── Auth ──────────────────────────────────────────────────────────────────────

_bearer_scheme = HTTPBearer()


async def verify_token(
    credentials: HTTPAuthorizationCredentials = Depends(_bearer_scheme),  # noqa: B008
) -> str:
    """Validate the device token. Returns the token (used as rate-limit key)."""
    if not NEURON_SECRET_TOKEN:
        return credentials.credentials  # auth disabled (dev mode)
    if credentials.credentials != NEURON_SECRET_TOKEN:
        raise HTTPException(status_code=401, detail="Invalid token")
    return credentials.credentials


# ── App lifecycle ─────────────────────────────────────────────────────────────


@asynccontextmanager
async def lifespan(app: FastAPI):  # type: ignore[type-arg]
    log.info("brain_api_startup", rate_limit_tpm=RATE_LIMIT_TOKENS_PER_MIN)
    yield
    log.info("brain_api_shutdown")


app = FastAPI(
    title="Neuron Brain API",
    version="0.1.0",
    lifespan=lifespan,
)


# ── Exception handlers ───────────────────────────────────────────────────────


@app.exception_handler(LLMProviderError)
async def llm_provider_error_handler(request: Request, exc: LLMProviderError) -> JSONResponse:
    log.error("llm_provider_error", error=str(exc), path=request.url.path)
    return JSONResponse(
        status_code=502,
        content=ErrorResponse(error="llm_provider_error", detail=str(exc)).model_dump(),
    )


# ── Health endpoint ───────────────────────────────────────────────────────────


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok", "service": "neuron-brain"}


# ── LLM proxy endpoint ───────────────────────────────────────────────────────


@app.post("/v1/llm/chat")
async def llm_chat(
    body: ChatRequest,
    device_token: str = Depends(verify_token),  # noqa: B008
) -> Any:
    # Rate-limit check (use estimated prompt tokens as cost)
    estimated_tokens = sum(len(m.content) // 4 for m in body.messages)
    if not _rate_limiter.allow(device_token, cost=max(estimated_tokens, 1)):
        raise HTTPException(
            status_code=429,
            detail="Rate limit exceeded. Try again shortly.",
        )

    log.info(
        "llm_proxy_request",
        tier=body.model_tier.value,
        stream=body.stream,
        msg_count=len(body.messages),
        estimated_tokens=estimated_tokens,
    )

    if body.stream:
        return StreamingResponse(
            _stream_response(body),
            media_type="text/event-stream",
            headers={
                "Cache-Control": "no-cache",
                "X-Accel-Buffering": "no",
            },
        )

    resp = await route_chat(body.messages, body.params, body.model_tier)
    return resp.model_dump()


async def _stream_response(body: ChatRequest):  # type: ignore[no-untyped-def]
    """Yield SSE events from the provider stream."""
    try:
        async for chunk in route_chat_stream(body.messages, body.params, body.model_tier):
            data = chunk.model_dump_json()
            yield f"data: {data}\n\n"
        yield "data: [DONE]\n\n"
    except LLMProviderError as e:
        error_data = json.dumps({"error": str(e)})
        yield f"data: {error_data}\n\n"
