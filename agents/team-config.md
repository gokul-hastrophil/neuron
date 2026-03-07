# Team Configuration & Roles — Neuron Project

> **Purpose:** Define team structure, responsibilities, and file ownership
> **Updated:** 2026-03-08
> **Status:** Active (MVP 3-week sprint)

---

## Team Structure (5 Sub-Teams)

### Team Alpha — Android/Kotlin (3-4 people)

**Focus:** AccessibilityService, UI control, on-device ML
**Lead:** Android Senior Engineer
**Support:** 1-2 Android Mid Engineers
**File Ownership:** `android/**`

**Week 1 (THE NERVE):**
- NeuronAccessibilityService, UITreeReader, ActionExecutor
- ScreenCapture, OverlayManager
- Debug tooling

**Responsible For:**
- [ ] All Kotlin code architecture
- [ ] APK builds and signing
- [ ] Device compatibility (Pixel, Samsung, OnePlus)
- [ ] Performance optimization (battery, memory)
- [ ] Integration with Hilt DI

**Success Metrics:**
- Week 1 milestone: UITreeReader + ActionExecutor proven reliable
- <2s avg response time for actions
- <5% crash rate on real devices
- APK size < 50MB

---

### Team Beta — Python/Server (2-3 people)

**Focus:** MCP server, LLM routing, prompt engineering
**Lead:** AI/ML Engineer
**Support:** 1 Backend Engineer
**File Ownership:** `server/**`

**Week 2 (THE BRAIN):**
- LLMRouter, IntentClassifier, SensitivityGate
- Prompt engineering, ReAct loop
- MCP server skeleton

**Responsible For:**
- [ ] Python code quality (Ruff, Black, type hints)
- [ ] LLM API integrations (Anthropic, Google, OpenAI)
- [ ] Async/await patterns
- [ ] Prompt versioning and optimization
- [ ] Memory service design

**Success Metrics:**
- 3-tier routing (T1, T2, T3) working with >80% accuracy
- <500ms avg LLM response time
- <2 hallucinations per 10 complex tasks
- Sensitivity gate >99% accurate

---

### Team Gamma — Cross-Cutting (Rotating, 2-3 people)

**Focus:** Testing, Security, DevOps, Documentation
**No permanent lead** — rotates per sprint week

**Roles (rotate weekly):**

**Week 1:** qa-engineer (automation testing)
- Build integration test framework
- Write accessibility test suite
- Ensure UITreeReader + ActionExecutor coverage >90%

**Week 2:** security-auditor (privacy, data handling)
- Verify sensitivity gate effectiveness
- Audit LLM prompt injection vectors
- Check T4 enforcement

**Week 3:** devops-engineer (CI/CD, release)
- Finalize docker-compose setup
- GitHub Actions optimization
- APK signing + release process

**Ongoing:** documentation-engineer (week 3+)
- API docs auto-generation
- Developer onboarding
- Architecture decision records (ADRs)

**File Ownership:** `docker/`, `.github/`, `tests/`, `docs/`, `skills/`

---

### Team Delta — Architecture Advisory (2 people)

**Focus:** Long-term vision, major decisions
**No direct code ownership** — guides all teams

**Lead:** Senior Architect / Tech Lead
**Support:** 1 LLM/Prompt Expert

**Responsibilities:**
- [ ] Architecture reviews on major PRs
- [ ] Decision making on framework choices
- [ ] Long-term planning (beyond Week 3)
- [ ] Cross-team coordination
- [ ] Research on emerging tech

**Week 2 Task:** LLM routing strategy deep dive
- Evaluate T3 models (Claude vs Gemini Pro)
- Finalize prompt template structure
- Design plan-and-execute state machine

---

### Team Epsilon — GitHub & DevOps (2 people)

**Focus:** GitHub automation, release management, community
**Lead:** DevOps Engineer
**Support:** 1 Project Manager / Community Manager

**Responsibilities:**
- [ ] Branch protection rules
- [ ] PR review queue management
- [ ] Issue triage and labeling
- [ ] Release automation
- [ ] Contributor onboarding
- [ ] GitHub Actions optimization

**File Ownership:** `.github/`, `CHANGELOG.md`, `CODEOWNERS`, release workflows

**Key Workflows:**
- PR creation → review → merge queue
- Issue triage → sprint planning
- Tag → CI → Release
- Contributor onboarding

---

## Deployment Boundaries (CI/CD Ownership)

| Task | Owner | When |
|------|-------|------|
| Lint (Ktlint, Ruff) | Team Alpha/Beta | Pre-commit (local) |
| Unit tests | Team Alpha/Beta | Pre-commit (local) |
| Integration tests | Team Gamma (qa) | CI (GitHub Actions) |
| Build APK | Team Epsilon | CI (GitHub Actions) |
| Sign APK | Team Epsilon | Manual or CI (tagged releases) |
| Create Release | Team Epsilon | Manual via gh CLI |
| Deploy docs | Team Gamma (docs) | Auto on main merge |

---

## Sprint Week Assignments

### Week 1 — THE NERVE (AccessibilityService)

| Team | Assigned Tasks | Owner |
|------|---------------|----|
| Alpha | NeuronAccessibilityService, UITreeReader, ActionExecutor | Android Senior |
| Alpha | ScreenCapture, OverlayManager, debug tools | Android Mid |
| Gamma (QA) | Integration tests: 10-task benchmark, OEM compat | qa-engineer |
| Epsilon | Android CI setup, lint + test cache | devops-engineer |

### Week 2 — THE BRAIN (LLM Routing)

| Team | Assigned Tasks | Owner |
|------|---------------|----|
| Beta | LLMRouter, IntentClassifier, SensitivityGate | AI Engineer |
| Beta | Prompt engineering, PlanAndExecuteEngine | AI Engineer |
| Alpha | NeuronBrainService, WorkingMemory, voice input | Android Senior |
| Gamma (Security) | Sensitivity gate audit, T4 enforcement | security-auditor |
| Epsilon | Python CI setup, MCP server Docker | devops-engineer |

### Week 3 — MEMORY + SDK + SHIP

| Team | Assigned Tasks | Owner |
|------|---------------|----|
| Beta | Room DB design, LongTermMemory, episodic memory | AI Engineer / Android Mid |
| Beta | MCP server Python implementation | AI Engineer |
| Alpha | SDK skeleton, AppFunctionsBridge | Android Senior |
| Gamma (DevOps) | Release automation, APK signing, CD pipeline | devops-engineer |
| Gamma (Docs) | API docs, onboarding guide, README | documentation-engineer |
| Epsilon | Beta release, contributor onboarding | project-manager |

---

## Code Review Routing (via CODEOWNERS)

```
# Primary owners (request review from these)
android/app/src/main/kotlin/ai/neuron/accessibility/**  @android-senior
server/brain/**                                          @ai-engineer
android/app/src/test/**                                  @qa-engineer

# Secondary owners (FYI, optional review)
android/**                                               @android-mid
server/**                                                @backend-engineer
.github/                                                 @devops-engineer
```

When a PR is opened:
1. GitHub auto-assigns code owners
2. Reviewer leaves comment: "✅ approved" (approval) or "changes requested"
3. PR can merge after 1+ approval + all CI green

---

## Daily Standup Format

Every morning (async or sync):

```
## What I Did Yesterday
- [x] Task 1
- [x] Task 2
- [x] Code review for PR #42

## What I'm Doing Today
- [ ] Task 3
- [ ] Task 4

## Blockers
- Waiting on PR #50 merge (blocking PR #51)
- Samsung OneUI accessibility issue (investigation ongoing)

## Help Needed
- Need security review on sensitivity gate logic
```

**Standup Location:** Slack #neuron-standup (async, 2pm UTC daily)

---

## Communication Channels

| Channel | Purpose | Frequency |
|---------|---------|-----------|
| #neuron-dev | General discussion | Real-time |
| #neuron-standup | Daily updates | Daily (async) |
| #neuron-reviews | Code review pings | As needed |
| #neuron-releases | Release notifications | Per release |
| Slack Huddle | Sync meetings | Wed 2pm, Fri 4pm UTC |
| GitHub Issues | Feature tracking | Always-on |

---

## Decision Authority Matrix

| Decision | Who Decides | Input From | Final Word |
|----------|-----------|-----------|-----------|
| Architecture | Delta (Tech Lead) | Alpha, Beta, Gamma | Tech Lead |
| API changes | Alpha + Beta consensus | QA, Docs | Architects |
| Release timeline | Epsilon + Tech Lead | All teams | Tech Lead |
| Tool choice (e.g., testing framework) | Relevant team | Tech Lead approval | Team Lead |
| Bug severity | Gamma (QA) + affected team | All | QA Lead |
| Performance targets | All teams | Tech Lead sets | Team consensus |

---

## Collaboration Model

### Parallel Execution (Worktrees)

Each team works in isolated worktrees to avoid conflicts:

```bash
# Main worktree — coordination and integration
git worktree list

# Android team — neuron-android worktree
git worktree add neuron-android develop
cd neuron-android
# Work on Android features

# Python team — neuron-server worktree
git worktree add neuron-server develop
cd neuron-server
# Work on server features

# When ready, each team PRs to develop
# Integration happens via NeuronAction JSON schema (contract)
```

**Integration Contract:**
File: `docs/architecture/action_schema.json`
- Defines NeuronAction JSON format (immutable during sprint)
- Android team implements ActionExecutor to handle actions
- Python team uses this schema for LLM output

---

## Onboarding Checklist (New Team Member)

When a new engineer joins:

- [ ] Read CLAUDE.md (project identity, architecture)
- [ ] Read SPRINT.md (current sprint plan)
- [ ] Read relevant team config (this file)
- [ ] Set up local dev environment: `scripts/setup_dev.sh`
- [ ] Clone and explore code structure
- [ ] Pick a "good-first-issue" label task
- [ ] Pair with team lead on first PR
- [ ] After 2-3 PRs, can review others' code

**Buddy System:** Each new team member pairs with a senior for first week

---

## Performance Reviews & Metrics

**Sprint Velocity:**
- Target: Team Alpha 20 story points/week
- Target: Team Beta 15 story points/week

**Code Quality:**
- Coverage: ≥80% on core modules
- Test pass rate: 100%
- Lint violations: 0 blockers

**Delivery:**
- On-time PR reviews: <24h turnaround
- Critical bugs: <2 per week
- Release readiness: green CI before tag

---

## Escalation Path

```
Issue → Closest Team Lead
  → If needs cross-team: Tech Lead (Delta)
  → If needs business decision: Project Manager
  → If strategic: Founder/CEO
```

Examples:
- "Sprint 1 won't fit in 3 weeks" → Tech Lead → PM → Founder
- "Two teams disagree on API design" → Tech Lead mediates
- "Critical security bug found" → QA Lead → Tech Lead → fast-track fix

---

## Team Rituals

**Daily:**
- Async standup (#neuron-standup, 2pm UTC)

**Weekly:**
- Sync huddle (Wed 2pm, Fri 4pm UTC, 30min each)
  - Demo changes from this week
  - Discuss blockers
  - Plan next week

**Sprint:**
- Sprint kickoff (Monday 9am UTC)
  - Review sprint tasks
  - Assign owners
  - Discuss unknowns

- Sprint retro (Friday 4pm UTC)
  - What went well?
  - What could improve?
  - Action items for next sprint

---

**Last Updated:** 2026-03-08 | **Version:** 0.1.0-alpha
