# Reusable Workflow Templates — Neuron Project

> **Purpose:** Define repeatable processes for common development tasks
> **Updated:** 2026-03-08
> **Audience:** All team members

---

## Workflow 1: Feature Development (TDD-First)

### Overview
Develop features with Test-Driven Development (TDD): RED → IMPLEMENT → GREEN → REVIEW

### Steps

#### 1. Design Phase (30 min)
```
Goal: Define the contract, not the implementation

- [ ] Read existing code + architecture (CLAUDE.md, skills/*.md)
- [ ] Sketch design on paper or whiteboard
- [ ] Identify dependencies (other modules, LLM APIs, etc.)
- [ ] List public functions + their contracts (input → output)
- [ ] Define error cases
- [ ] Get feedback from Tech Lead (if complex)
```

#### 2. TDD Red Phase (write tests first)

Create test file: `android/app/src/test/kotlin/ai/neuron/accessibility/UITreeReaderTest.kt`

```kotlin
class UITreeReaderTest {

    @Test
    fun should_returnEmptyTree_when_rootIsNull() {
        every { mockService.getRootInActiveWindow() } returns null

        val result = uiTreeReader.getUITree()

        assert(result.nodes.isEmpty())
    }

    @Test
    fun should_pruneInvisibleNodes_when_treeContainsInvisibleElements() {
        // Arrange, Act, Assert
    }

    // Write 5-10 more test cases covering all contracts
}
```

Run tests (they fail):
```bash
./gradlew test --tests UITreeReaderTest
# ❌ FAILED (expected — RED phase)
```

#### 3. Implement Phase (code to make tests pass)

Create: `android/app/src/main/kotlin/ai/neuron/accessibility/UITreeReader.kt`

Code implementation until all tests pass.

#### 4. Code Review Phase

```bash
# Commit your work
git add -A
git commit -m "feat: implement UITreeReader with smart pruning

- Prunes invisible nodes
- Prunes non-interactive leaves
- Serializes to JSON
- 10 test cases, 100% coverage"

# Create PR
gh pr create --base develop \
  --title "Feature: UITreeReader with pruning" \
  --body "Implements the UITreeReader contract from design.

- ✅ All tests pass
- ✅ Ktlint clean
- ✅ 100% coverage
- ✅ Tested on Pixel 6 Pro (Android 15)"
```

**Code review checklist (4 dimensions):**
- [ ] **Security:** No hardcoded IDs, proper null checks
- [ ] **Performance:** Tree traversal O(n), no allocations per node
- [ ] **Correctness:** Edge cases (null children, deep trees)
- [ ] **Architecture:** Follows CLAUDE.md layer rules

#### 5. Merge & Verify

```bash
# After approval
gh pr merge --squash --delete-branch

# Verify in CI
gh run list --workflow=android-ci.yml --limit 1
```

---

## Workflow 2: Bug Fix (Evidence-Based)

### When Something Breaks

Example: "AccessibilityService crashes on Samsung Galaxy"

#### 1. Triage
```
- [ ] Reproduce the bug (which device? which app? which action?)
- [ ] Check logs (logcat, crash reporter)
- [ ] Identify pattern (always crashes? sometimes? certain conditions?)
- [ ] Create GitHub issue with all details
```

#### 2. Parallel Investigation (3 Hypotheses)

Assign 3 developers to investigate competing hypotheses:

**Investigator A: "It's a Samsung OneUI compatibility issue"**
```
- [ ] Samsung OneUI accessibility changes (doc research)
- [ ] Test on both Samsung and Pixel side-by-side
- [ ] Check if AccessibilityNodeInfo behavior differs
- Evidence if true: Crash ONLY on Samsung, not on Pixel
```

**Investigator B: "It's a null pointer in UITreeReader"**
```
- [ ] Review UITreeReader.kt for potential nulls
- [ ] Check if node.getChild(i) can return null (yes, it can)
- [ ] Add defensive null checks
- Evidence if true: Crash has NPE in stack trace at UITreeReader:XXX
```

**Investigator C: "It's a concurrency issue in AccessibilityService"**
```
- [ ] Check if AccessibilityService callbacks are thread-safe
- [ ] Look for race conditions between multiple callbacks
- Evidence if true: Crash happens under high event frequency
```

#### 3. Evidence-Based Ranking

```
Hypothesis A (Samsung compatibility):
  Evidence: Crashes only on Samsung ✅
  Evidence: Same code works on Pixel ✅
  Confidence: HIGH (2/2 evidence)
  → INVESTIGATE FIRST

Hypothesis B (null pointer):
  Evidence: UITreeReader has null checks ✗
  Evidence: logcat shows NPE at UITreeReader ✅
  Confidence: MEDIUM (1/2 evidence)
  → INVESTIGATE SECOND

Hypothesis C (concurrency):
  Evidence: No race conditions found ✗
  Evidence: Crash is deterministic, not intermittent ✗
  Confidence: LOW (0/2 evidence)
  → INVESTIGATE LAST
```

#### 4. Winner Implements Fix

Investigator A (highest confidence) owns the fix:

```kotlin
// Android workaround for Samsung OneUI bug
private fun traverseAndPrune(node: AccessibilityNodeInfo, result: MutableList<UINode>) {
    if (!node.isVisibleToUser) return

    result.add(node.toUINode())

    // Samsung OneUI bug: sometimes getChildCount() returns wrong value
    try {
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            traverseAndPrune(child, result)
        }
    } catch (e: IndexOutOfBoundsException) {
        Log.w(TAG, "Samsung OneUI: child index out of bounds")
    }
}
```

Write test to prevent regression and update ACCESSIBILITY_NOTES.md.

---

## Workflow 3: Code Review (4-Dimension Framework)

### Dimension 1: Security
- [ ] No hardcoded secrets (API keys, passwords)
- [ ] Sensitive data checked against sensitivity gate
- [ ] All user inputs validated
- [ ] Proper permission checking

### Dimension 2: Performance
- [ ] No main thread blocking calls
- [ ] Memory footprint reasonable
- [ ] Network calls have timeouts
- [ ] No O(n²) algorithms

### Dimension 3: Correctness
- [ ] Error cases handled
- [ ] Null/empty cases covered
- [ ] Async operations properly await
- [ ] Logic correct for all code paths

### Dimension 4: Architecture
- [ ] Follows CLAUDE.md 6-layer architecture
- [ ] Module boundaries respected
- [ ] DI used correctly (Hilt)
- [ ] No circular dependencies

---

## Workflow 4: Sprint Planning

### Every Monday Morning (1 hour)

#### 1. Review Last Sprint (15 min)
- [ ] Count closed issues/PRs
- [ ] Review completed tasks vs planned
- [ ] Discuss what went well
- [ ] Discuss what was slow
- [ ] Update SPRINT.md with notes

#### 2. Backlog Refinement (20 min)
- [ ] Review "good-first-issue" backlog
- [ ] Estimate effort (story points: 1, 2, 3, 5, 8)
- [ ] Assign to team members with their input
- [ ] Identify blockers/unknowns

**Story Point Scale:**
- 1 = Trivial (1 hour)
- 2 = Small (few hours)
- 3 = Medium (half day)
- 5 = Large (full day)
- 8 = XL (2+ days)

#### 3. Sprint Assignment (25 min)
- [ ] Team Alpha gets ~20 points/week
- [ ] Team Beta gets ~15 points/week
- [ ] Team Gamma rotates assignment
- [ ] Identify critical path items (ship blockers)

---

## Workflow 5: Daily Standup (Async)

Every day at 2pm UTC, post to #neuron-standup:

```markdown
## 2026-03-09 Daily Standup

### Andy (Team Alpha — Android)
✅ What I did yesterday:
- Completed NeuronAccessibilityService.kt setup
- Got device permissions working

🔄 What I'm doing today:
- Starting UITreeReader implementation
- Writing pruning logic tests

🚧 Blockers:
- None
```

---

## Workflow 6: Release & Deploy

### When Shipping a Version

#### Pre-Release (2 days before)

```bash
# 1. Create release branch
git checkout -b release/v0.2.0

# 2. Update version numbers
sed -i 's/versionName ".*"/versionName "0.2.0"/' android/build.gradle.kts
sed -i 's/__version__ = ".*"/__version__ = "0.2.0"/' server/setup.py

# 3. Update CHANGELOG.md
cat >> CHANGELOG.md << 'EOF'
## [0.2.0] — 2026-03-15

### Added
- Voice input support
- LLM routing (T0-T4 tiers)
- Confirmation overlay for sensitive actions

### Fixed
- Samsung OneUI AccessibilityService crash (#42)
EOF

# 4. Test locally
cd android && ./gradlew test assembleRelease
cd ../server && pytest tests/

# 5. Push release branch for review
git push origin release/v0.2.0
gh pr create --base main --head release/v0.2.0 \
  --title "Release: v0.2.0"
```

#### Release Day

```bash
# 1. Merge release PR to main
gh pr merge <pr-number> --squash

# 2. Tag the release
git tag -a v0.2.0 -m "Release v0.2.0: Voice input + LLM routing"

# 3. Push tag (triggers GitHub Actions CI)
git push origin v0.2.0

# 4. Monitor CI
gh run list --workflow=release.yml

# 5. Create release note
gh release create v0.2.0 \
  release/neuron-0.2.0.apk \
  --title "Neuron v0.2.0 — The Brain" \
  --notes-file CHANGELOG.md
```

---

## Workflow 7: PR Creation Best Practices

### When You Have Code Ready

```bash
# 1. Ensure local tests pass
./gradlew test && pytest

# 2. Ensure no lint warnings
./gradlew ktlint && ruff check server/

# 3. Commit with clear message
git commit -m "feat: add voice input to overlay

- Integrated Android SpeechRecognizer
- Added waveform animation during recording
- Allow edit before submit

Closes #35"

# 4. Create PR with structured body
gh pr create \
  --base develop \
  --title "Feature: Voice input for commands" \
  --body "
## What Changed
- Added SpeechRecognitionService
- New OverlayRecorder UI component
- Waveform animation during recording

## Why
Addresses #35 — voice control is more natural than typing.

## Testing
- ✅ Tested on Pixel 6 Pro
- ✅ All unit tests pass
- ✅ Voice recognition works offline (fallback)

## Checklist
- [x] Tests written + all passing
- [x] No lint warnings
- [x] Coverage doesn't decrease
- [x] Updated CHANGELOG.md
- [x] Docs updated (if applicable)
"
```

---

## Workflow 8: TDD Must-Test Components

These 8 components MUST have >90% test coverage:

```
1. UITreeReader — traversal, pruning, JSON serialization
2. ActionExecutor — tap, type, swipe, verification
3. SensitivityGate — password field, banking app detection
4. LLMRouter — T0-T4 routing logic
5. IntentClassifier — task complexity, intent parsing
6. PlanAndExecuteEngine — state machine, ReAct loop
7. WorkingMemory — state management, serialization
8. server/brain/planner.py — action plan generation, LLM calls
```

**Rule:** Before merging any PR that touches these 8 components:
- [ ] All tests pass
- [ ] Coverage >= 90% (measure with Jacoco/pytest-cov)
- [ ] No new test failures
- [ ] Code review approved by team lead

---

**Last Updated:** 2026-03-08 | **Version:** 0.1.0-alpha
