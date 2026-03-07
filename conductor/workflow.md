# Workflow - Neuron

## TDD Policy

**Strictness: Strict** — Tests are required before implementation.

### Red-Green-Refactor Cycle

1. **RED**: Write failing tests that define the contract
2. **GREEN**: Write minimal code to make tests pass
3. **REFACTOR**: Clean up while keeping tests green

### Must-TDD Components (8 Core)

These components require >90% test coverage before merge:

1. `UITreeReader` — traversal, pruning, JSON serialization
2. `ActionExecutor` — tap, type, swipe, verification
3. `SensitivityGate` — password field, banking app detection
4. `LLMRouter` — T0-T4 routing decisions
5. `IntentClassifier` — task complexity classification
6. `PlanAndExecuteEngine` — ReAct loop state machine
7. `WorkingMemory` — state persistence, serialization
8. `server/brain/planner.py` — action plan generation

### Test Naming Convention

```
should_<expected>_when_<condition>
```

Examples:
- `should_returnPrunedTree_when_invisibleNodesExist`
- `should_routeToT4_when_passwordFieldDetected`

### Test Locations

| Type | Location |
|------|----------|
| Kotlin unit tests | `android/app/src/test/kotlin/ai/neuron/{Module}/{Class}Test.kt` |
| Android integration | `android/app/src/androidTest/kotlin/ai/neuron/{Feature}IntegrationTest.kt` |
| Python unit tests | `server/tests/test_{module}.py` |
| Python integration | `server/tests/integration/test_{feature}.py` |

## Commit Strategy

**Conventional Commits** — All commit messages follow this format:

```
<type>(<scope>): <description>

[optional body]

[optional footer]
```

### Types

| Type | When |
|------|------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `test` | Adding or updating tests |
| `refactor` | Code change that neither fixes a bug nor adds a feature |
| `chore` | Build, CI, dependencies, tooling |
| `perf` | Performance improvement |

### Scopes

`accessibility`, `brain`, `memory`, `input`, `sdk`, `ui`, `mcp`, `server`, `ci`, `docker`

### Examples

```
feat(accessibility): implement UITreeReader with smart pruning
fix(brain): handle timeout in Gemini Flash client
test(accessibility): add ActionExecutor tap verification tests
docs(memory): update memory design skill reference
chore(ci): add Python CI workflow with Ruff + pytest
```

## Code Review

**Policy: Required for all changes.**

Every PR must receive at least 1 approval before merge. Reviews evaluate 4 dimensions:

1. **Security** — No hardcoded secrets, sensitivity gate checked, input validated
2. **Performance** — No main thread blocking, timeouts set, latency budgets respected
3. **Correctness** — Edge cases handled, error handling graceful, async patterns correct
4. **Architecture** — Layer boundaries respected, DI used correctly, naming consistent

### PR Requirements

- All tests pass (CI green)
- No lint warnings (Ktlint + Ruff)
- Coverage doesn't decrease
- CHANGELOG.md updated
- Linked to an issue or SPRINT.md task

## Verification Checkpoints

**Policy: After each task completion.**

After completing each task:

1. Run the full test suite locally
2. Verify lint passes with zero warnings
3. Manual verification of the implemented behavior
4. Update `memory/reliability/scores.json` if component status changed
5. Update `memory/knowledge/features.md` or `memory/knowledge/bugs.md`

## Task Lifecycle

```
BACKLOG → IN_PROGRESS → REVIEW → VERIFY → DONE
```

1. **BACKLOG**: Task defined in track plan, not started
2. **IN_PROGRESS**: Developer actively working, tests being written (RED phase)
3. **REVIEW**: PR created, awaiting code review
4. **VERIFY**: Review approved, manual verification in progress
5. **DONE**: Merged to develop, verified working

## Branch Strategy

```
main (stable) ← release/* ← develop ← feature/*
```

- `main`: Protected, release-ready code
- `develop`: Integration branch, target for feature PRs
- `feature/*`: Short-lived branches from develop
- `release/*`: Release preparation branches
- `hotfix/*`: Emergency fixes from main
