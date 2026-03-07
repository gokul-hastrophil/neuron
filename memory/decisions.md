# NEURON — Architecture Decision Records

## ADR-001: AccessibilityService as Primary Nerve Layer
**Date:** March 2026  
**Status:** Accepted  
**Context:** Multiple options for Android UI interaction: ADB, UiAutomator2, Instrumentation, Root/Xposed, AccessibilityService  
**Decision:** AccessibilityService is the sole primary interaction mechanism  
**Consequences:** No root required, works on 3B+ devices, Play Store compatible (accessibility path), all capabilities in one API  
**Alternatives considered:** ADB (requires dev mode, 100-500ms latency), Root (breaks Play Integrity), UiAutomator2 (testing only)

---

## ADR-002: Tiered LLM Routing (T0-T4)
**Date:** March 2026  
**Status:** Accepted  
**Context:** Need to balance speed, cost, privacy, and capability across all task types  
**Decision:** 5-tier routing: T0 (Porcupine/no LLM), T1 (Gemma 3n), T2 (Gemini Flash), T3 (Claude/Gemini Pro), T4 (on-device only, sensitive)  
**Consequences:** Fast and private for simple tasks, powerful for complex ones, always safe for sensitive data  
**Alternatives considered:** Always-cloud (privacy issue), always-local (quality issue)

---

## ADR-003: Kotlin for Android, Python for Server
**Date:** March 2026  
**Status:** Accepted  
**Context:** Team has Python/AI and Kotlin/Android skills  
**Decision:** Pure Kotlin for Android app, pure Python for server/MCP/AI  
**Consequences:** Clear boundaries, each language in its strength zone  
**Alternatives considered:** KMP (too complex for MVP), React Native (can't implement AccessibilityService)

---

## ADR-004: Sideload APK Distribution (Play Store Later)
**Date:** March 2026  
**Status:** Accepted  
**Context:** Google Play Store policy prohibits autonomous AI control via Accessibility API  
**Decision:** Ship as sideloadable APK from GitHub Releases, pursue Play Store via accessibility positioning in parallel  
**Consequences:** Faster iteration, broader capabilities, smaller initial user base  
**Alternatives considered:** Play Store from day 1 (blocks core feature), alternative stores (fragmented)

---

## ADR-005: Plan-and-Execute Over Pure ReAct
**Date:** March 2026  
**Status:** Accepted  
**Context:** Pure ReAct (action-observation loop) is slow and expensive for multi-step tasks  
**Decision:** Get full plan from T3 LLM first, execute step-by-step with T2, replan only on failure  
**Consequences:** Fewer total LLM calls, clearer progress visibility, faster execution  
**Alternatives considered:** Pure ReAct (too many cloud calls), pure scripting (no generalization)

---

## Current Tech Stack Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Android min SDK | API 26 (Android 8.0) | takeScreenshot() requires API 30, but core nerve works from 26 |
| Primary cloud LLM | Gemini 2.5 Flash | Best price/performance for action selection ($0.15/M) |
| Complex planner LLM | Claude Sonnet 4.5 | Best tool-calling + agentic reasoning |
| On-device LLM | Gemma 3n via MediaPipe | Deepest Android integration, multimodal, NPU-optimized |
| Vector DB | sqlite-vec | No separate server, integrates with Android SQLite |
| Wake word | Porcupine | On-device, custom words, 2MB, Kotlin SDK |
| Offline STT | whisper.cpp | Best quality/size ratio, ARM NEON optimized |
| DI framework | Hilt | Standard for new Android projects |
| UI | Jetpack Compose | Modern, less boilerplate for overlay UI |
