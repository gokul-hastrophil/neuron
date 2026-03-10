# Performance Benchmarks & Optimizations

Benchmark results, optimization discoveries, latency measurements, and resource usage patterns.

Track performance metrics and optimization opportunities for each major component.

Format: `### [YYYY-MM-DD] — [Component/Feature]`

---

### 2026-03-10 — Integration Benchmark Results (Redmi Note 9 Pro, Android 12)

**Best run: 75% (12/16)** — Target >=70% MET

| Category | Pass/Total | Avg Time |
|----------|-----------|----------|
| Single-step (pattern-match) | 10/10 | 3-6s |
| Multi-step (cloud LLM) | 2/6 | 10-37s |

Pattern-matched commands complete in ~3s (sub-second for the action, rest is benchmark overhead).
Cloud LLM multi-step: Gemini Flash T2 averages 10-15s per step. Timeout at 69s catches slow chains.

Key finding: Moving pattern matching before tier routing was the single biggest improvement (31% → 75%).

---
