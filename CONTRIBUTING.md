# Contributing to Neuron

Thank you for your interest in contributing to Neuron! We're building an AI-powered agentic OS layer for Android phones, and we'd love your help. This document outlines the process and guidelines for contributing.

## Quick Start

### 1. Fork and Clone
```bash
git clone https://github.com/YOUR-USERNAME/neuron.git
cd neuron
git remote add upstream https://github.com/gokul-hastrophil/neuron.git
```

### 2. Development Setup
We provide automated setup scripts for different platforms:

```bash
# Linux/WSL
bash scripts/setup_linux.sh

# macOS
bash scripts/setup_macos.sh

# Windows (via WSL recommended)
bash scripts/setup_linux.sh
```

These scripts handle:
- Python environment setup (venv, pip dependencies)
- Kotlin/Gradle toolchain configuration
- Pre-commit hooks installation
- IDE configuration for Kotlin linting

### 3. Create a Feature Branch
```bash
git checkout -b feat/your-feature-name
# or: git checkout -b fix/issue-description
```

Branch naming follows conventional commits:
- `feat/` - New features
- `fix/` - Bug fixes
- `docs/` - Documentation updates
- `refactor/` - Code refactoring (no behavior change)
- `test/` - Test improvements
- `chore/` - Tooling, config, dependencies

### 4. Make Changes and Commit
Follow our **Commit Message Format** (see below).

### 5. Push and Create a Pull Request
```bash
git push origin feat/your-feature-name
```

Create a PR with the provided template. See **Pull Request Requirements** below.

---

## Code Style & Linting

### Kotlin/Android
We use **Ktlint** + **Detekt** for code quality:

```bash
# Check style
./gradlew ktlintCheck

# Auto-fix
./gradlew ktlintFormat

# Static analysis
./gradlew detekt
```

### Python (Server, MCP, AI)
We use **Ruff** for linting and formatting:

```bash
# Check style
ruff check server/

# Auto-fix
ruff check --fix server/

# Format
ruff format server/
```

### Pre-commit Hooks
Setup scripts install Git pre-commit hooks that run:
- Ktlint/Detekt for Kotlin changes
- Ruff for Python changes
- Conventional commit message validation
- No hardcoded secrets detection

All checks must pass before commit.

---

## Commit Message Format

We follow **Conventional Commits** format:

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types
- **feat**: A new feature
- **fix**: A bug fix
- **docs**: Documentation only
- **style**: Code style changes (formatting, missing semicolons, etc.)
- **refactor**: Code refactoring without behavior change
- **perf**: Performance improvements
- **test**: Adding or updating tests
- **chore**: Build, tooling, dependencies
- **ci**: CI/CD configuration changes

### Scope
Optional. Examples:
- `feat(android)`: Android-specific feature
- `fix(server)`: Server bug fix
- `docs(contributing)`: Contributing docs

### Subject
- Imperative mood ("add feature" not "added feature")
- No capital letter at start
- No period at end
- Max 50 characters

### Body
- Explain **what** and **why**, not how
- Wrap at 72 characters
- Separate from subject with blank line

### Footer
Include issue references:
```
Fixes #123
Related-To #456
```

### Examples
```
feat(android): add accessibility overlay layer

Implement floating overlay for Neuron UI actions
using Android WindowManager. Handles:
- Drag-to-move gestures
- Multi-window scenarios
- Permission validation

Fixes #42
```

```
fix(server): handle MCP connection timeout

Connection was hanging indefinitely during
network disruption. Add 30s timeout with
exponential backoff retry strategy.

Fixes #89
```

---

## Pull Request Requirements

### 1. PR Description
Use the PR template (auto-populated) to describe:
- **Summary**: What problem does this solve?
- **Type**: Feature, bugfix, refactor, docs, infra
- **Related Issue**: Link to issue #number
- **Testing Done**: How was this tested?
- **Screenshots** (if UI changes): Before/after or demo

### 2. Linked Issue
Every PR should link to an issue:
- Click "Link an issue" in the PR sidebar, or
- Add to PR body: `Fixes #123`

### 3. CI Status
- All GitHub Actions checks must pass
- Android CI: Gradle build, Ktlint, Detekt, tests
- Server CI: Ruff, Python tests
- No red X marks

### 4. Code Review
- PR must be approved by at least one maintainer
- Address all comments before merging
- Maintainers: see CODEOWNERS for area-specific reviewers

### 5. Sensitive Checks
Before approval, verify:
- No hardcoded API keys, tokens, or secrets
- No sensitive paths (passwords, banking URLs)
- No personal information (emails, phone numbers)
- Appropriate LLM routing tier for data sensitivity (see CLAUDE.md)

### 6. Documentation
- Update SPRINT.md if implementing a sprint task
- Update README.md if user-facing behavior changes
- Add docstrings to new public APIs
- Link to skills/ reference docs if applicable

---

## Issue Reporting Guidelines

### Found a Bug?
1. **Check existing issues** first (use search)
2. **Use the bug template** (auto-populated when you click "New Issue")
3. **Provide steps to reproduce**:
   - Device/OS version
   - Exact steps
   - Expected vs actual behavior
   - Error logs if applicable
4. **Attach logs/screenshots** if possible

### Requesting a Feature?
1. **Use the feature template**
2. **Explain the use case**: Who needs this and why?
3. **Suggest implementation** (optional but helpful)
4. **Check alignment** with project roadmap in VISION.md

### Security Issues
**Do NOT open a public issue.** Email security concerns to the maintainers directly instead.

---

## Areas Needing Help

Neuron is a complex project spanning multiple domains. We especially need contributors for:

### OEM Integration & Testing
- Carrier bloatware handling
- OEM-specific accessibility limitations
- Device-specific gesture conflicts
- Testing across Samsung, Google Pixel, OnePlus, Xiaomi

### App Integrations
- New app action libraries (beyond Gmail, Maps, etc.)
- App-specific gesture recognition
- Floating action button detection
- Web-to-app action bridging

### On-Device Machine Learning
- Quantized model optimization
- On-device intent classification
- Low-latency speech recognition
- Privacy-preserving gesture prediction

### Accessibility & UX
- Screen reader compatibility
- Color contrast validation
- Voice control UX patterns
- Haptic feedback guidelines
- Mobile accessibility testing

### Documentation
- Tutorial videos
- API documentation
- Integration guides
- Troubleshooting FAQs

### Infrastructure & CI/CD
- Docker optimization
- GitHub Actions improvements
- LLM testing framework setup
- Performance benchmarking

---

## Development Workflow

### Typical Flow
```
1. Create issue (if not already exists)
2. Fork repo (one-time)
3. Create feature branch
4. Setup dev environment (one-time: bash scripts/setup_linux.sh)
5. Make changes
6. Run linters locally (./gradlew ktlintFormat, ruff format)
7. Commit with conventional format
8. Push to your fork
9. Create PR with template
10. Respond to review feedback
11. Merge when approved
```

### Before Opening PR
```bash
# Kotlin
./gradlew ktlintFormat
./gradlew detekt

# Python
ruff format server/
ruff check --fix server/

# Commit
git add .
git commit -m "feat(scope): description"

# Push
git push origin feat/your-feature-name
```

---

## Questions?

- **GitHub Discussions**: Post in [Discussions](https://github.com/gokul-hastrophil/neuron/discussions)
- **Issues**: Tag with `question` label
- **Email**: neuron@hastrophil.ai

---

## Code of Conduct

This project adheres to the Contributor Covenant Code of Conduct. By participating, you agree to abide by its terms. See CODE_OF_CONDUCT.md for details.

---

## License

By contributing to Neuron, you agree that your contributions will be licensed under the same license as the project (see LICENSE file). You must have permission to contribute the code you submit.

Thank you for contributing to Neuron!
