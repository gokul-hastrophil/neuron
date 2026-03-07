# Neuron — Claude Code & AI Workflow Guide

> How to use agents, skills, workflows, and Claude Code features in the Neuron project.

---

## Table of Contents

1. [Quick Start](#quick-start)
2. [Project Configuration Files](#project-configuration-files)
3. [Agents — Specialized AI Assistants](#agents)
4. [Skills — Domain Reference Docs](#skills)
5. [Workflows — Repeatable Processes](#workflows)
6. [Claude Code Built-in Features](#claude-code-features)
7. [Team Collaboration with Agents](#team-collaboration)
8. [Daily Workflow](#daily-workflow)
9. [Memory System](#memory-system)
10. [Common Tasks Cheat Sheet](#cheat-sheet)

---

## Quick Start

```bash
# Launch Claude Code in the project root (loads CLAUDE.md automatically)
cd ~/neuron && claude

# Launch with focused module context
cd ~/neuron/android && claude   # Loads android/CLAUDE.md
cd ~/neuron/server && claude    # Loads server/CLAUDE.md
cd ~/neuron/sdk && claude       # Loads sdk/CLAUDE.md
```

When Claude Code starts, it reads the `CLAUDE.md` in your current directory. This gives it full context about the project architecture, coding standards, and current sprint tasks.

---

## Project Configuration Files

These files shape how Claude Code understands and works on the project:

| File | Purpose | When It's Read |
|------|---------|----------------|
| `CLAUDE.md` | Master config — architecture, tech stack, coding rules, hard rules | Always (auto-loaded) |
| `android/CLAUDE.md` | Android module — build commands, module map, Kotlin rules, ADB commands | When launching from `android/` |
| `server/CLAUDE.md` | Server module — Python commands, MCP guide, FastAPI patterns | When launching from `server/` |
| `sdk/CLAUDE.md` | SDK module — Python + Kotlin SDK APIs, build commands | When launching from `sdk/` |
| `SPRINT.md` | Current sprint plan — active tasks, day-by-day schedule | Referenced during implementation |
| `.claude/settings.local.json` | Pre-approved shell commands (gradlew, pytest, adb, gh, etc.) | Always (auto-loaded) |

---

## Agents

Agents are specialized AI assistant configurations in `agents/`. They define **roles, expertise, rules, and code patterns** for different development domains. You reference them when asking Claude Code for help in a specific area.

### Available Agents

| Agent File | Role | When to Use |
|------------|------|-------------|
| `agents/architect.md` | System architect | Architecture decisions, ADRs, layer boundary reviews, performance budgets |
| `agents/android_dev.md` | Android/Kotlin specialist | AccessibilityService, Compose UI, Hilt DI, coroutines, on-device ML |
| `agents/ai_engineer.md` | AI/ML engineer | LLM integration, prompt engineering, MCP server, Python backend |
| `agents/qa.md` | QA/testing specialist | Test writing, test strategy, coverage, integration benchmarks |
| `agents/devops.md` | Infrastructure/DevOps | Docker, CI/CD, Gradle optimization, APK signing, monitoring |
| `agents/github_manager.md` | GitHub & community manager | PRs, issues, releases, branch strategy, contributor onboarding |
| `agents/daily_startup.md` | Research agent | Daily tech updates, dependency checks, vulnerability scanning |
| `agents/team-config.md` | Team structure reference | Team roles, file ownership, sprint assignments, decision authority |
| `agents/workflows.md` | Process templates | Feature dev, bug fix, code review, sprint planning, releases |

### How to Use Agents

**Method 1: Ask Claude Code directly (recommended)**

Just describe what you need. Claude Code reads `CLAUDE.md` and has all the context:

```
> "Implement UITreeReader with smart pruning"
  → Claude uses android_dev.md patterns + architect.md performance budgets

> "Write tests for the LLM router"
  → Claude uses qa.md test patterns + ai_engineer.md LLM client code

> "Review this PR for security issues"
  → Claude uses the 4-dimension code review framework from workflows.md
```

**Method 2: Reference an agent explicitly**

```
> "Using the architect agent rules, review my ActionExecutor design"
> "Following the qa agent patterns, write tests for SensitivityGate"
> "As the devops agent, set up the Docker Compose config"
```

**Method 3: Use Claude Code's Agent tool (sub-agents)**

Claude Code can spawn specialized sub-agents for parallel work:

```
> "Launch the android_dev agent to implement UITreeReader
   and the qa agent to write tests — in parallel"
```

This uses the Agent tool with voltagent types like:
- `voltagent-lang:kotlin-specialist` — Kotlin/Android work
- `voltagent-lang:python-pro` — Python server work
- `voltagent-qa-sec:test-automator` — Test automation
- `voltagent-infra:devops-engineer` — CI/CD and infra
- `voltagent-qa-sec:security-auditor` — Security reviews

### Agent Details

#### architect.md
- **Enforces**: 6-layer architecture, performance budgets (UI tree < 50ms, T1 LLM < 500ms)
- **Creates**: Architecture Decision Records (ADRs) in `memory/decisions.md`
- **Knows**: Data classification (RED/AMBER/GREEN), trust boundaries

#### android_dev.md
- **Owns**: All files in `android/`
- **Patterns**: Sealed Result types, coroutine scopes, AccessibilityService actions
- **Rules**: Never block main thread, always use Hilt, always handle reconnection

#### ai_engineer.md
- **Owns**: All files in `server/`
- **Patterns**: Async LLM clients with timeouts, Pydantic NeuronAction schema, MCP tool registration
- **Rules**: Always validate LLM output, always set timeouts, sensitivity gate first

#### qa.md
- **Owns**: All test directories
- **Patterns**: MockK for Kotlin, pytest-asyncio for Python, `should_X_when_Y` naming
- **Tracks**: 10-task integration benchmark, 8 must-TDD components, coverage gates

#### devops.md
- **Owns**: `docker/`, `.github/workflows/`, `scripts/`
- **Provides**: Docker Compose configs, GitHub Actions CI/CD, Gradle optimization, port map
- **Targets**: CI < 5 minutes, reproducible builds, health checks everywhere

#### github_manager.md
- **Owns**: `.github/`, `CHANGELOG.md`, `CODEOWNERS`
- **Provides**: Branch strategy (main/develop/feature/*), PR templates, issue taxonomy, release process
- **Tools**: Full `gh` CLI reference for PRs, issues, releases

---

## Skills

Skills are **domain-specific technical reference docs** in `skills/`. They contain implementation details, code patterns, and design decisions for core subsystems. Claude Code uses them as context when implementing features.

### Available Skills

| Skill File | Domain | Key Content |
|------------|--------|-------------|
| `skills/android_accessibility.md` | AccessibilityService | Service registration, XML config, UI tree traversal, action execution, overlay management |
| `skills/llm_routing.md` | LLM Tier Routing | T0-T4 tier table, Kotlin LLMRouter class, Python LLM clients, sensitivity gate, cost tracking |
| `skills/memory_design.md` | Memory System | Room DB schema, DAO interfaces, WorkingMemory, vector search, prompt context builder |
| `skills/mcp_integration.md` | MCP Server | Architecture diagram, tool registration, ADB bridge, Claude Desktop config, testing |

### How to Use Skills

Skills are automatically consulted by Claude Code when relevant. You can also reference them explicitly:

```
> "Following the llm_routing skill, implement the LLMRouter class"
> "Use the memory_design skill to design the Room DB schema"
> "Implement a new MCP tool following mcp_integration.md patterns"
```

### Skill Highlights

#### android_accessibility.md
- Complete AndroidManifest.xml service registration
- XML accessibility_service_config
- UITreeReader traversal with pruning (invisible nodes, non-interactive leaves)
- ActionExecutor patterns (tap, type, swipe, scroll, global actions)
- OverlayManager with TYPE_ACCESSIBILITY_OVERLAY

#### llm_routing.md
- Tier table with model, latency budget, cost per 1K tokens
- Kotlin `LLMRouter` class with `@Inject` (Hilt)
- Python `BaseLLMClient` ABC, `ClaudeClient`, `GeminiFlashClient` with timeouts
- `SensitivityGate` — detects password fields, banking apps, health data
- Retry + fallback strategy (T2 fail → retry once → fallback to T3)

#### memory_design.md
- Room entities: `UserPreference`, `AppWorkflow`, `TaskTrace`
- DAO interfaces with query patterns
- `WorkingMemory` — in-memory state with SharedPreferences backup
- sqlite-vec vector operations for RAG retrieval
- Prompt context builder that injects memory into LLM prompts

#### mcp_integration.md
- MCP architecture: Claude Desktop → MCP Server → ADB → Android App
- `@server.tool()` registration pattern
- Step-by-step guide to add new MCP tools
- Testing MCP tools with pytest-asyncio

---

## Workflows

Defined in `agents/workflows.md`, these are **repeatable process templates** for common development tasks.

### Workflow 1: Feature Development (TDD-First)

```
Design (30 min) → TDD Red (write failing tests) → Implement (make tests pass) → Code Review (4 dimensions) → Merge
```

Usage:
```
> "Implement UITreeReader following the TDD workflow"
```

Claude Code will:
1. Read existing architecture docs
2. Write test cases first (RED phase)
3. Implement code to make tests pass (GREEN phase)
4. Run lint + tests
5. Create a commit with structured message

### Workflow 2: Bug Fix (Evidence-Based)

```
Triage → 3 Competing Hypotheses → Evidence Collection → Rank by Confidence → Winner Implements Fix
```

Usage:
```
> "Debug the Samsung accessibility crash using the bug fix workflow"
```

### Workflow 3: Code Review (4 Dimensions)

Every PR is reviewed across:
1. **Security** — no hardcoded secrets, sensitivity gate checked
2. **Performance** — no main thread blocking, timeouts set
3. **Correctness** — edge cases, error handling, async patterns
4. **Architecture** — layer boundaries, DI, naming consistency

Usage:
```
> "Review my ActionExecutor PR using the 4-dimension framework"
```

### Workflow 4: Sprint Planning

Weekly sprint ceremony template with story point estimation and team assignment.

### Workflow 5: Release & Deploy

```
Version bump → CHANGELOG update → Test suite → Tag → GitHub Actions → gh release create
```

Usage:
```
> "Prepare release v0.2.0 following the release workflow"
```

### Workflow 6: PR Creation

Structured PR creation with `gh pr create`, proper labels, linked issues, and checklist.

---

## Claude Code Features

### Slash Commands

```
/help              — Show available commands
/compact           — Compress conversation context (use when running long)
/clear             — Clear conversation history
/cost              — Show token usage and costs
/fast              — Toggle fast mode (same model, faster output)
```

### Built-in Skills (invoke with slash)

These are Claude Code skills you can trigger directly:

```
/commit            — Auto-generate a commit with a good message
/simplify          — Review changed code for quality and efficiency
/loop 5m /commit   — Run a command on a recurring interval
```

### Agent Team Skills

Spawn multi-agent teams for complex tasks:

```
> Use team-spawn to create a review team
> Use team-feature to develop UITreeReader and ActionExecutor in parallel
> Use team-debug to investigate the Samsung crash with competing hypotheses
> Use team-review to run a multi-dimensional code review
> Use team-status to check progress
> Use team-shutdown to wrap up
```

### TDD Skills

```
> Use tdd-red to write failing tests for LLMRouter
> Use tdd-green to implement code that makes the tests pass
> Use tdd-cycle for a full red-green-refactor loop
```

### Conductor (Project Management)

```
> Use conductor:setup to initialize project tracking
> Use conductor:new-track to create a new feature track
> Use conductor:implement to execute tasks from a track
> Use conductor:status to see project progress
```

---

## Team Collaboration

Defined in `agents/team-config.md`, the project has 5 teams:

| Team | Focus | File Ownership |
|------|-------|---------------|
| **Alpha** (Android) | AccessibilityService, UI, on-device ML | `android/**` |
| **Beta** (Python) | MCP server, LLM routing, prompts | `server/**` |
| **Gamma** (Cross-cutting) | Testing, security, DevOps, docs | `docker/`, `.github/`, `tests/`, `docs/` |
| **Delta** (Advisory) | Architecture decisions, long-term planning | No direct code ownership |
| **Epsilon** (GitHub) | PRs, issues, releases, community | `.github/`, `CHANGELOG.md`, `CODEOWNERS` |

### Parallel Development with Worktrees

Teams work in isolated git worktrees to avoid conflicts:

```bash
# Android team worktree
git worktree add neuron-android develop
cd neuron-android && claude

# Python team worktree
git worktree add neuron-server develop
cd neuron-server && claude
```

Both teams implement from the same **NeuronAction JSON schema** (`docs/architecture/action_schema.json`).

---

## Daily Workflow

### Morning Startup

1. **Run the daily startup script:**
   ```bash
   bash scripts/daily_startup.sh
   ```
   This pulls latest changes, checks dependencies, runs smoke tests, and shows GitHub status.

2. **Or ask Claude Code to run the daily startup agent:**
   ```
   > "Run the daily startup checklist from agents/daily_startup.md"
   ```
   This searches for tech updates, checks vulnerabilities, and updates `memory/knowledge/tech_radar.md`.

### During Development

```
> "Implement [feature] following TDD workflow"    # Feature work
> "Write tests for [component]"                    # Test writing
> "Debug [issue]"                                  # Bug investigation
> "Review [file] for security and performance"     # Code review
```

### End of Session

Claude Code automatically follows the **Auto Memory Protocol** (defined in CLAUDE.md):

1. Feature completed → appends to `memory/knowledge/features.md`
2. Bug fixed → appends to `memory/knowledge/bugs.md`
3. New pattern discovered → appends to `memory/knowledge/patterns.md`
4. Session ending → appends summary to `memory/changelog/sessions.md`
5. Test milestone → updates `memory/reliability/scores.json`

---

## Memory System

### Project Memory (version-controlled, in repo)

```
memory/
├── decisions.md              — Architecture Decision Records
├── project_memory.json       — Structured project state
├── lessons_learned.md        — What failed and why
├── knowledge/
│   ├── features.md           — Feature implementation notes
│   ├── bugs.md               — Bug fixes with root causes
│   ├── patterns.md           — Coding patterns discovered
│   ├── compatibility.md      — OEM quirks, device-specific notes
│   ├── performance.md        — Benchmark results
│   ├── dependencies.md       — Dependency notes and upgrade paths
│   └── tech_radar.md         — Latest tech updates (auto-updated)
├── reliability/
│   ├── scores.json           — Component reliability tracking (8 components)
│   └── test_history.md       — Test pass/fail trends
└── changelog/
    └── sessions.md           — Session summaries
```

### Claude Memory (persists across conversations, local)

Located at `~/.claude/projects/.../memory/`:
- `MEMORY.md` — Compact project overview (auto-loaded every session)
- `patterns.md` — Kotlin/Python/integration coding patterns
- `debugging.md` — Common issues, ADB commands, logcat tags
- `testing.md` — Test strategy, naming conventions, benchmark details

---

## Cheat Sheet

### Common Development Tasks

| Task | What to Say |
|------|-------------|
| Implement a feature | `"Implement UITreeReader with smart pruning"` |
| Write tests | `"Write tests for ActionExecutor following qa.md patterns"` |
| Debug a crash | `"Debug the Samsung accessibility crash"` |
| Code review | `"Review LLMRouter.kt for security and performance"` |
| Add MCP tool | `"Add a new MCP tool called neuron_scroll_to_element"` |
| Build the app | `"Run assembleDebug and report any errors"` |
| Run tests | `"Run all Android unit tests"` |
| Create a PR | `"Create a PR for the UITreeReader feature"` |
| Create a release | `"Prepare release v0.2.0"` |
| Check sprint status | `"What's the current sprint status?"` |

### Build & Test Commands (pre-approved, no permission prompts)

```bash
# Android
cd android && ./gradlew assembleDebug      # Build debug APK
cd android && ./gradlew test               # Run unit tests
cd android && ./gradlew lint               # Run lint
cd android && ./gradlew ktlintFormat       # Format code
cd android && ./gradlew installDebug       # Install on device

# Python
cd server && pytest tests/ -v              # Run all tests
cd server && ruff check .                  # Lint Python
cd server && python -m uvicorn server.mcp.neuron_mcp_server:app  # Start MCP server

# Docker
docker compose -f docker/docker-compose.yml up -d    # Start stack
docker compose -f docker/docker-compose.yml logs -f   # View logs

# Git & GitHub
git status && git diff                     # Check changes
gh pr list --state open                    # List open PRs
gh issue list --state open                 # List open issues
gh run list                                # Check CI status

# ADB (Android Debug Bridge)
adb devices                                # List connected devices
adb logcat -s NeuronAS                     # Filter Neuron logs
adb shell dumpsys accessibility            # Dump accessibility state
```

### Key Architecture Rules (from CLAUDE.md)

1. Never send password/PIN/CVV to cloud APIs
2. Never execute irreversible actions without user confirmation
3. Never block the main thread with synchronous LLM calls
4. Always verify actions by re-reading the UI tree after execution
5. Always check the sensitivity gate before any cloud call
6. Always log every action to the audit log

---

*For more details, read the individual agent and skill files directly. This guide provides an overview — the source files contain the complete code patterns and implementation details.*
