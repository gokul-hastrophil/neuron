# Tech Stack - Neuron

## Languages

| Language | Version | Usage |
|----------|---------|-------|
| Kotlin | 2.0+ | Android app (primary) |
| Python | 3.12+ | Server, MCP, AI backend |
| Java | Legacy only | Android framework APIs where Kotlin wrappers unavailable |

## Android

| Component | Library | Version |
|-----------|---------|---------|
| Min SDK | Android 8.0 | API 26 |
| Target SDK | Android 15 | API 35 |
| Build System | Gradle | 8.x |
| DI | Hilt | 2.51+ |
| Async | Kotlin Coroutines + Flow | 1.8+ |
| Local DB | Room | 2.6+ |
| HTTP | Retrofit + OkHttp | 2.9+ / 4.12+ |
| JSON | Kotlinx Serialization | 1.7+ |
| UI | Jetpack Compose | 1.7+ |
| On-Device LLM | MediaPipe LLM Inference | Latest |
| On-Device Embeddings | MediaPipe Embedding | Latest |
| Wake Word | Porcupine Android SDK | 3.x |
| STT | whisper.cpp | Latest |
| Vector DB | sqlite-vec | 0.1.x |
| AppFunctions | androidx.appfunctions | Beta |

## Server (Python)

| Component | Library | Version |
|-----------|---------|---------|
| MCP Server | mcp | 1.x |
| API Framework | FastAPI | 0.115+ |
| LLM (Anthropic) | anthropic | 0.40+ |
| LLM (Google) | google-generativeai | 0.8+ |
| LLM (OpenAI) | openai | 1.50+ |
| Async | asyncio + uvicorn | -- |
| Vector Ops | numpy | 2.x |
| HTTP Client | httpx | 0.27+ |

## Infrastructure

| Component | Tool |
|-----------|------|
| Containers | Docker + Docker Compose |
| CI/CD | GitHub Actions |
| APK Signing | Gradle Play Publisher |
| Code Quality | Ktlint + Detekt (Kotlin), Ruff (Python) |
| Testing | JUnit5 + Espresso + MockK (Android), Pytest + pytest-asyncio (Python) |
| Secrets | GitHub Secrets + local .env |

## Deployment

- **Day 1**: Android APK sideload distribution
- **Later**: Google Play Store (accessibility service approval path)
- **Server**: Docker self-hosted (docker-compose.yml)
- **Ports**: MCP Server (7384), Brain API (8384), Dev Server (8385)

## LLM Tiers

| Tier | Model | Location | Latency Budget |
|------|-------|----------|----------------|
| T0 | Porcupine | On-device | <10ms |
| T1 | Gemma 3n | On-device | <500ms |
| T2 | Gemini 2.5 Flash | Cloud | <2000ms |
| T3 | Claude Sonnet / Gemini Pro | Cloud | <5000ms |
| T4 | Gemma 3n ONLY | On-device | <500ms |
