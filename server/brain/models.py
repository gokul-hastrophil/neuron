"""Pydantic models for the LLM proxy endpoint."""

from __future__ import annotations

import enum

from pydantic import BaseModel, Field


class ModelTier(enum.StrEnum):
    """LLM routing tier. T0/T1/T4 are on-device only — never proxied."""

    T2 = "t2"
    T3 = "t3"


class ChatMessage(BaseModel):
    role: str = Field(..., pattern=r"^(system|user|assistant)$")
    content: str


class ChatParams(BaseModel):
    temperature: float = Field(default=0.7, ge=0.0, le=2.0)
    max_tokens: int = Field(default=4096, ge=1, le=32768)
    top_p: float = Field(default=1.0, ge=0.0, le=1.0)


class ChatRequest(BaseModel):
    model_tier: ModelTier
    messages: list[ChatMessage] = Field(..., min_length=1)
    params: ChatParams = Field(default_factory=ChatParams)
    stream: bool = False


class ChatChoice(BaseModel):
    index: int = 0
    message: ChatMessage
    finish_reason: str = "stop"


class UsageInfo(BaseModel):
    prompt_tokens: int = 0
    completion_tokens: int = 0
    total_tokens: int = 0


class ChatResponse(BaseModel):
    id: str = ""
    model: str = ""
    provider: str = ""
    tier: ModelTier = ModelTier.T2
    choices: list[ChatChoice] = Field(default_factory=list)
    usage: UsageInfo = Field(default_factory=UsageInfo)
    latency_ms: float = 0.0


class StreamChunk(BaseModel):
    """One SSE chunk in a streaming response."""

    id: str = ""
    model: str = ""
    provider: str = ""
    delta: str = ""
    finish_reason: str | None = None


class ProviderConfig(BaseModel):
    """Configuration for a single LLM provider."""

    name: str
    api_key_env: str
    base_url: str | None = None
    model: str
    sdk: str = Field(..., pattern=r"^(anthropic|google|openai_compat)$")


class ErrorResponse(BaseModel):
    error: str
    detail: str | None = None
