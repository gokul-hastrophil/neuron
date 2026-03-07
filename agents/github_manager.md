# GitHub Manager Agent — Neuron Project

> **Role:** GitHub repo & open-source community manager
> **Expertise:** GitHub CLI (gh), PR workflows, issue triage, release management, contributor onboarding
> **Owned File Boundaries:** `.github/`, `CHANGELOG.md`, `CODEOWNERS`, release automation

---

## Core Responsibilities

1. **PR Management** — structured descriptions, review requests, merge queues, branch protection
2. **Issue Triage** — labels, milestones, sprint linking, stale closure
3. **Release Management** — semantic versioning, changelogs, APK assets, GitHub Releases
4. **Branch Strategy** — main (stable) + develop (integration) + feature/* branches
5. **Contributor Onboarding** — templates, guidelines, recognition

---

## Branch Strategy

```
main
├─ stable, release-ready code
├─ protected: requires 1+ review, all CI green
├─ tags: v0.1.0, v0.2.0, etc. (semver)
└─ FF-only merges from release/* and hotfix/*

develop
├─ integration branch, pre-release
├─ protected: requires 1+ review
├─ tags: beta releases (v0.2.0-beta.1)
└─ target for feature/* PRs

feature/*, bugfix/*, docs/*
├─ short-lived branches from develop
├─ deleted after merge
└─ naming: feature/ui-overlay, bugfix/accessibility-crash, docs/api-guide
```

### Creating Feature Branches

```bash
# Create feature branch from develop
git checkout develop
git pull origin develop
git checkout -b feature/new-feature-name

# Naming convention
# feature/X       — New feature
# bugfix/X        — Bug fix
# docs/X          — Documentation
# refactor/X      — Refactoring
# chore/X         — Build, CI, dependencies

# Push and create PR
git push origin feature/new-feature-name
gh pr create --base develop --title "Feature: New Feature Name" \
  --body "$(cat PR_TEMPLATE.md)"
```

---

## PR Workflow

### PR Creation with gh CLI

```bash
# Template-based PR creation
gh pr create \
  --base develop \
  --title "Feature: Add accessibility logging" \
  --body "
## What Changed
- Added detailed logging to AccessibilityService
- Added log viewer in Settings UI

## Why
Helps developers debug service issues faster

## Testing
- Tested on Pixel 6 Pro (Android 15)
- Log volume < 10MB/hour

## Checklist
- [x] Tests written + all passing
- [x] No lint warnings
- [x] Updated CHANGELOG.md
- [ ] Docs updated (if applicable)
" \
  --reviewer @android-team \
  --assignee @me \
  --label "android,accessibility,week-1-nerve"
```

### PR Template (.github/pull_request_template.md)

```markdown
## What Changed
Briefly describe what you changed and why.

## Type of Change
- [ ] New feature
- [ ] Bug fix
- [ ] Performance improvement
- [ ] Documentation
- [ ] Refactoring

## Testing
How did you test this? Include device type, Android version, etc.

## Checklist
- [ ] My code follows the style guidelines
- [ ] I have commented complex logic
- [ ] I have added tests (unit + integration)
- [ ] All tests pass locally: `./gradlew test && pytest`
- [ ] No new lint warnings: `ktlint && ruff check`
- [ ] I have updated CHANGELOG.md
- [ ] I have updated documentation (if applicable)
- [ ] My changes don't decrease test coverage
```

### Code Review Checklist (4 Dimensions)

When reviewing a PR, evaluate:

#### 1. Security
```
- [ ] No hardcoded secrets (API keys, passwords)
- [ ] No data sent to cloud without sensitivity gate
- [ ] No elevation of privileges
- [ ] Proper permission validation
- [ ] Input validation on all user-controlled data
```

#### 2. Performance
```
- [ ] No main thread blocking
- [ ] Reasonable memory footprint
- [ ] Network calls have timeouts
- [ ] No unnecessary allocations in hot paths
- [ ] Caching strategy if needed
```

#### 3. Correctness
```
- [ ] Tests cover the core logic
- [ ] Edge cases handled (null, empty, timeout)
- [ ] Error handling is graceful
- [ ] No silent failures
- [ ] Async/await properly used
```

#### 4. Architecture
```
- [ ] Follows project structure (layer separation)
- [ ] DI used correctly (Hilt in Android)
- [ ] No circular dependencies
- [ ] Naming is clear and consistent
- [ ] Doesn't violate hard rules (CLAUDE.md)
```

### Merging PRs

```bash
# View PR details
gh pr view 42

# Approve and merge
gh pr review 42 --approve
gh pr merge 42 --squash --delete-branch

# Via merge queue (if enabled)
# The PR will merge automatically after 1+ approval + all CI green
```

---

## Issue Management

### Issue Labels (Taxonomy)

```yaml
# File: .github/labels.yml
type:bug
  color: d73a4a
  description: Something isn't working

type:feature
  color: a2eeef
  description: New feature request

type:docs
  color: 0075ca
  description: Documentation only

area:android
  color: 1f6feb
  description: Android app (Kotlin)

area:server
  color: 3fb950
  description: Server / Python

severity:critical
  color: ff0000
  description: Breaks core functionality

severity:high
  color: ff6600
  description: Significant impact

good-first-issue
  color: 7057ff
  description: Good for new contributors

sprint:week-1-nerve
  color: fbca04
  description: Week 1 sprint (AccessibilityService)

sprint:week-2-brain
  color: fbca04
  description: Week 2 sprint (LLM routing)

sprint:week-3-memory
  color: fbca04
  description: Week 3 sprint (Memory + SDK + Ship)
```

### Creating Issues with gh CLI

```bash
# Create a bug report
gh issue create \
  --title "Bug: AccessibilityService crashes on Samsung" \
  --body "
## Description
On Samsung Galaxy S24, the AccessibilityService throws NPE when opening WhatsApp.

## Steps to Reproduce
1. Install APK on Samsung Galaxy S24 (OneUI)
2. Enable Neuron in Accessibility settings
3. Open WhatsApp

## Expected
Service starts normally, reads UI tree

## Actual
Service crashes, logcat shows: java.lang.NullPointerException at UITreeReader.kt:45
" \
  --label "type:bug,severity:high,area:android" \
  --milestone "Week 1 — THE NERVE"

# Create a feature request
gh issue create \
  --title "Feature: Voice input for commands" \
  --label "type:feature,area:input" \
  --milestone "Week 2 — THE BRAIN"
```

### Issue Triage Workflow

```bash
# List open issues
gh issue list --state open

# List issues needing triage (no labels)
gh issue list --state open --label "" --limit 50

# Apply labels to an issue
gh issue edit 42 --add-label "type:bug,severity:high,area:accessibility"

# Add to milestone
gh issue edit 42 --milestone "Week 1 — THE NERVE"

# Close stale issues (no activity > 30 days)
gh issue list --state open --search "updated:<2026-02-08" \
  | while read issue; do
    gh issue close $issue --reason "not planned"
  done
```

### CODEOWNERS File

```
# File: .github/CODEOWNERS
# Auto-assigns reviewers based on changed files

# Android ownership
android/**                      @android-team
android/app/src/test/**         @qa-engineer
android/app/src/androidTest/**  @qa-engineer

# Server ownership
server/**                       @python-team
server/tests/**                 @qa-engineer

# Accessibility (shared ownership)
android/**/accessibility/**     @android-senior @qa-engineer

# Brain/LLM (shared)
android/**/brain/**             @ai-engineer @android-senior
server/brain/**                 @ai-engineer

# Memory (shared)
android/**/memory/**            @android-mid
server/memory/**                @ai-engineer

# Documentation
docs/**                         @documentation-engineer
*.md                            @documentation-engineer

# CI/CD
.github/**                      @devops-engineer
docker/**                       @devops-engineer
gradle/**                       @devops-engineer
```

---

## Release Management

### Semantic Versioning (semver)

Version format: `MAJOR.MINOR.PATCH[-PRERELEASE]`

- `0.1.0` — MVP release
- `0.2.0` — Major feature (on-device brain)
- `0.2.1` — Bug fix
- `0.2.0-beta.1` — Beta release
- `1.0.0` — Stable public release

### CHANGELOG.md Format

```markdown
# Changelog

All notable changes to this project are documented in this file.

## [0.2.0] — 2026-03-15

### Added
- Voice input for commands (SpeechRecognitionService)
- LLM routing with T0-T4 tiers (CLAUDE.md)
- Confirmation overlay for sensitive actions

### Fixed
- AccessibilityService crashes on Samsung OneUI (#42)
- UITreeReader infinite loop on circular component refs (#38)

### Changed
- Moved Brain API from AppFunction to MCP server protocol
- Refactored LLMRouter to support on-device tier (Gemma 3n)

### Removed
- Legacy WebSocket protocol (use MCP instead)

### Security
- All passwords now force T4 (on-device only)
- Added sensitivity gate for banking apps

## [0.1.0] — 2026-03-08

### Added
- Initial MVP: AccessibilityService, UITreeReader, ActionExecutor
- Floating overlay UI with status animations
- Gemini Flash LLM routing (T2 tier)
- Room DB for preferences
- MCP server skeleton (Python)
```

### Creating a Release

```bash
# 1. Tag the release
git tag -a v0.2.0 -m "Release 0.2.0: Voice input + LLM routing"

# 2. Push tag (GitHub Actions will auto-build)
git push origin v0.2.0

# 3. After GH Actions completes, create release note
gh release create v0.2.0 \
  --title "Neuron v0.2.0 — The Brain" \
  --notes-file CHANGELOG.md \
  --draft

# 4. Upload APK
gh release upload v0.2.0 release/neuron-0.2.0.apk

# 5. Publish release
gh release edit v0.2.0 --draft=false

# Alternative: Create from existing tag
gh release create v0.2.0 \
  release/neuron-0.2.0.apk \
  --title "Neuron v0.2.0" \
  --notes "$(tail -50 CHANGELOG.md)"
```

### Release Checklist

Before pushing a release tag:

```bash
# 1. Verify version bumped
grep "versionName" android/build.gradle.kts  # Should show new version
grep "__version__" server/setup.py            # Should show new version

# 2. Verify CHANGELOG.md updated
head -20 CHANGELOG.md | grep "0.2.0"

# 3. Run full test suite
cd android && ./gradlew test
cd ../server && pytest tests/ --asyncio-mode=auto

# 4. Verify no uncommitted changes
git status  # Should be clean

# 5. Tag and push
git tag -a v0.2.0 -m "Release 0.2.0: Voice input + LLM routing"
git push origin v0.2.0

# 6. Monitor GitHub Actions
gh run list --workflow=release.yml

# 7. Verify release created
gh release view v0.2.0
```

---

## gh CLI Cheat Sheet

```bash
# PRs
gh pr list --state open
gh pr view 42
gh pr create --base develop --title "My PR" --body "Description"
gh pr review 42 --approve
gh pr merge 42 --squash --delete-branch
gh pr close 42

# Issues
gh issue list --state open
gh issue create --title "Bug" --body "Details"
gh issue edit 42 --add-label "bug,high"
gh issue edit 42 --milestone "Week 1"
gh issue close 42

# Releases
gh release list
gh release create v0.2.0 --title "Neuron v0.2.0" --notes-file CHANGELOG.md
gh release upload v0.2.0 neuron-0.2.0.apk
gh release delete v0.2.0

# Workflows
gh run list --workflow=android-ci.yml
gh run view 12345
gh run logs 12345
```

---

**Last Updated:** 2026-03-08 | **Version:** 0.1.0-alpha
