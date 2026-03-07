# Daily Startup Research Agent — Neuron Project

> **Role:** Daily startup research agent
> **Run:** At start of each work session to fetch latest info and suggest improvements
> **Output:** Update MEMORY.md and generate actionable improvement suggestions

---

## Startup Checklist (10 Minutes)

Run this checklist every morning to stay aligned with the latest technology trends and project dependencies.

### 1. Technology Updates (WebSearch)

Search for latest news and updates on critical technologies:

```
Topics to check:
□ Android AccessibilityService — new APIs, bugs, best practices
□ Gemma 3n — model updates, quantization improvements
□ Gemini API — new features, rate limits, pricing
□ Anthropic Claude API — new models, improvements
□ Model Context Protocol (MCP) — spec updates, new tools
□ Porcupine Wake Word — new features, accuracy improvements
□ Jetpack Compose — new stable features, performance fixes
□ Hilt Dependency Injection — updates, best practices
□ Room Database — new features, migration guides
□ FastAPI — new releases, security patches
□ Pydantic — new validation features
□ httpx HTTP client — performance improvements
```

For each topic:
- Search: "[topic] latest [month year]"
- Note any breaking changes, new versions, or critical bugs
- Add to memory/knowledge/tech_radar.md with date and link

### 2. Dependency Version Check

```bash
# Android dependencies — Check against latest
# File: android/build.gradle.kts

# Check current vs latest:
# - org.jetbrains.kotlin:kotlin-gradle-plugin
# - com.android.tools.build:gradle
# - com.google.dagger:hilt-android
# - androidx.room:room-runtime
# - io.mockk:mockk
# - org.junit.jupiter:junit-jupiter

# Python dependencies — Check against PyPI
python -m pip list --outdated

# Check server/requirements.txt for updates:
# - anthropic
# - google-generativeai
# - fastapi
# - pydantic
# - httpx
# - pytest
# - pytest-asyncio
```

### 3. Configuration & Tool Improvements

- [ ] Claude Code features — check for new capabilities
- [ ] GitHub Actions — new actions, better caching
- [ ] Docker — new build features, performance improvements
- [ ] Gradle — new optimization features
- [ ] Kotlin — new language features, deprecations

### 4. Update Knowledge Base

After researching, update the tech radar:

```markdown
# File: memory/knowledge/tech_radar.md

## Latest Updates (As of 2026-03-08)

### Android AccessibilityService
- Last checked: 2026-03-08
- Status: Stable (API 26-35)
- Recent changes: None (stable API)
- Known issues: Samsung OneUI crashes (tracked in #42)
- Recommendation: Use latest com.android.platform:framework

### Gemma 3n
- Last checked: 2026-03-08
- Status: Production-ready
- Latest model: Gemma 3n-4b-it (quantized)
- New feature: Improved token efficiency
- Link: https://ai.google.dev/gemma
- Recommendation: Update MediaPipe LLM to latest

### Gemini API
- Last checked: 2026-03-08
- Latest model: Gemini 2.5 Flash (v001)
- Pricing: $0.075 per 1M input tokens
- Rate limits: 15 RPS free tier
- Link: https://ai.google.dev/gemini
- Action needed: Monitor rate limit usage in production

### Claude API
- Last checked: 2026-03-08
- Latest model: Claude Sonnet 4.5 (2024-12)
- Pricing: $3 per 1M input tokens
- Link: https://docs.anthropic.com
- Action needed: Benchmark T3 routing performance

### MCP Specification
- Last checked: 2026-03-08
- Current version: 1.0 stable
- New in 0.1.10: Improved error handling
- Link: https://modelcontextprotocol.io
- Action needed: None (using stable API)

### Porcupine Wake Word
- Last checked: 2026-03-08
- Latest version: 3.0.1
- Status: Stable
- Accuracy: 97% @ 0 dB SNR
- Link: https://picovoice.ai/products/porcupine
- Action needed: Consider for Week 5 (Always-Listening)
```

### 5. Vulnerability & Security Checks

```bash
# Check for known vulnerabilities
./gradlew dependencyCheckAnalyze

# Python vulnerability check
pip install safety
safety check --file server/requirements.txt

# Manual review
# [ ] Any new CVEs in Android framework?
# [ ] Any new CVEs in key dependencies?
# [ ] Any breaking changes in security policies?
```

### 6. Generate Improvement Suggestions

Based on research, categorize improvements as:

**Immediate (This Sprint)**
- Fix critical bugs found in dependencies
- Update pinned versions if security updates available
- Implement new optimization features

**Short-Term (Next 2 Sprints)**
- Adopt new stable features that improve code
- Refactor to use latest best practices
- Update documentation for new APIs

**Long-Term (Month 2+)**
- Plan for major version upgrades
- Evaluate new tools/frameworks
- Research emerging technologies

---

## Daily Startup Template (10 min)

```bash
#!/bin/bash
# File: scripts/daily_startup.sh

echo "=== Neuron Daily Startup ($(date)) ==="
echo ""

# 1. Pull latest changes
echo "1. Pulling latest changes..."
git fetch origin

# 2. Check dependency updates
echo "2. Checking dependency updates..."
cd android && ./gradlew dependencyUpdates 2>/dev/null || true
cd ../server && pip list --outdated | head -10

# 3. Run quick tests
echo "3. Running smoke tests..."
cd ../android && ./gradlew test --max-workers=1 -q 2>/dev/null || echo "Tests need full setup"

# 4. Check for stale issues/PRs
echo "4. Checking GitHub status..."
gh issue list --state open --limit 5
gh pr list --state open --limit 5

# 5. Display key metrics
echo ""
echo "=== Key Metrics ==="
echo "- Last stable version: $(git describe --tags --abbrev=0)"
echo "- Open issues: $(gh issue list --state open --json number | wc -l)"
echo "- Open PRs: $(gh pr list --state open --json number | wc -l)"

echo ""
echo "Ready to start development. Check memory/MEMORY.md for context."
```

Run with: `bash scripts/daily_startup.sh`

---

## Integration with Claude Code

When Claude Code starts, it reads:
1. CLAUDE.md (master config)
2. SPRINT.md (current sprint)
3. memory/MEMORY.md (persistent context from daily startup)

The daily startup agent automatically updates MEMORY.md with:
- Latest tech updates
- Dependency version recommendations
- Known issues/workarounds
- Performance baselines

---

**Last Updated:** 2026-03-08 | **Version:** 0.1.0-alpha
