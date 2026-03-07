# DevOps & Infrastructure Agent — Neuron Project

> **Role:** Infrastructure/DevOps specialist
> **Expertise:** Docker Compose, GitHub Actions, Gradle optimization, APK signing, monitoring
> **Owned File Boundaries:** `docker/`, `.github/workflows/`, `gradle/`, `scripts/`, CI/CD automation

---

## Core Principles

1. **Reproducible builds** — pinned versions, lockfiles, deterministic outputs
2. **Fast CI** — lint + test under 5 minutes (target: 3min)
3. **Cache aggressively** — Gradle, pip, Docker layers
4. **Health checks on all services** — liveness + readiness probes
5. **Monitoring from day one** — log aggregation, metrics, alerting

---

## Port Map (Single Source of Truth)

```
7384  → MCP Server (ws://localhost:7384/neuron-mcp)
8384  → Brain API (http://localhost:8384)
8385  → Dev Server / Webhook receiver
```

Always use these exact ports in docker-compose files, GitHub Actions, and documentation.

---

## Docker & Docker Compose

### docker-compose.yml (Production-Ready)

```yaml
# File: docker/docker-compose.yml
version: '3.9'

services:
  neuron-mcp:
    image: neuron-mcp:latest
    container_name: neuron-mcp
    build:
      context: ..
      dockerfile: docker/Dockerfile.mcp
      cache_from:
        - neuron-mcp:latest
    ports:
      - "7384:7384"
    environment:
      NEURON_MCP_PORT: 7384
      LOG_LEVEL: INFO
    volumes:
      - ./logs:/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:7384/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 20s
    restart: unless-stopped
    networks:
      - neuron

  neuron-brain:
    image: neuron-brain:latest
    container_name: neuron-brain
    build:
      context: ..
      dockerfile: docker/Dockerfile.brain
    ports:
      - "8384:8384"
    depends_on:
      neuron-mcp:
        condition: service_healthy
    environment:
      NEURON_BRAIN_PORT: 8384
      MCP_SERVER_URL: ws://neuron-mcp:7384/neuron-mcp
      ANTHROPIC_API_KEY: ${ANTHROPIC_API_KEY}
      GEMINI_API_KEY: ${GEMINI_API_KEY}
      LOG_LEVEL: INFO
    volumes:
      - ./logs:/app/logs
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8384/health"]
      interval: 10s
      timeout: 5s
      retries: 3
      start_period: 15s
    restart: unless-stopped
    networks:
      - neuron

volumes:
  logs:
    driver: local

networks:
  neuron:
    driver: bridge
```

### docker-compose.dev.yml (Development Override)

```yaml
# File: docker/docker-compose.dev.yml
version: '3.9'

services:
  neuron-mcp:
    build:
      context: ..
      dockerfile: docker/Dockerfile.mcp
      target: development
    volumes:
      - ../server:/app/server
      - ../android:/app/android
    environment:
      LOG_LEVEL: DEBUG
      PYTHONUNBUFFERED: 1
    ports:
      - "7384:7384"
    command: python -m uvicorn server.mcp.neuron_mcp_server:app --host 0.0.0.0 --port 7384 --reload

  neuron-brain:
    build:
      context: ..
      dockerfile: docker/Dockerfile.brain
      target: development
    volumes:
      - ../server:/app/server
    environment:
      LOG_LEVEL: DEBUG
      PYTHONUNBUFFERED: 1
    ports:
      - "8384:8384"
    command: python -m uvicorn server.brain.brain_api:app --host 0.0.0.0 --port 8384 --reload
```

### Common Docker Commands

```bash
# Start full stack (production)
docker-compose -f docker/docker-compose.yml up -d

# Start with logs
docker-compose -f docker/docker-compose.yml up

# Development mode (reload on code change)
docker-compose -f docker/docker-compose.yml -f docker/docker-compose.dev.yml up

# Check service health
docker-compose -f docker/docker-compose.yml ps
docker-compose -f docker/docker-compose.yml logs neuron-mcp

# Stop all services
docker-compose -f docker/docker-compose.yml down

# Clean rebuild (no cache)
docker-compose -f docker/docker-compose.yml build --no-cache

# View logs from specific service
docker-compose -f docker/docker-compose.yml logs -f neuron-brain --tail=100

# Execute command in running container
docker-compose -f docker/docker-compose.yml exec neuron-mcp bash

# Prune unused images/volumes
docker system prune -a --volumes
```

---

## Gradle Optimization

### gradle.properties (Performance Tuning)

```properties
# File: gradle.properties

# Parallel builds
org.gradle.parallel=true
org.gradle.workers.max=4

# Build caching
org.gradle.caching=true

# JVM args
org.gradle.jvmargs=-Xmx4g

# Daemon settings
org.gradle.daemon=true
org.gradle.daemon.idletimeout=300000

# Configuration on demand
org.gradle.configureondemand=true

# Offline builds (for CI)
# org.gradle.offline=true
```

---

## GitHub Actions Workflows

### android-ci.yml — Build, Lint, Test

```yaml
# File: .github/workflows/android-ci.yml
name: Android CI

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'android/**'
      - '.github/workflows/android-ci.yml'
  pull_request:
    branches: [ main, develop ]
    paths:
      - 'android/**'

jobs:
  build-and-test:
    runs-on: ubuntu-latest
    timeout-minutes: 15

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Run Ktlint
        run: cd android && ./gradlew ktlint --stacktrace

      - name: Run Unit Tests
        run: cd android && ./gradlew test --stacktrace --parallel

      - name: Build Debug APK
        run: cd android && ./gradlew assembleDebug --stacktrace --parallel

      - name: Upload Test Reports
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: test-reports
          path: android/app/build/reports/tests/
          retention-days: 7
```

### python-ci.yml — Server Tests

```yaml
# File: .github/workflows/python-ci.yml
name: Python CI

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'server/**'
  pull_request:
    branches: [ main, develop ]
    paths:
      - 'server/**'

jobs:
  test:
    runs-on: ubuntu-latest
    timeout-minutes: 10

    steps:
      - uses: actions/checkout@v4

      - name: Set up Python 3.12
        uses: actions/setup-python@v5
        with:
          python-version: '3.12'
          cache: 'pip'

      - name: Install dependencies
        run: |
          cd server
          pip install --upgrade pip
          pip install -r requirements.txt
          pip install -r requirements-dev.txt

      - name: Lint with Ruff
        run: cd server && ruff check . --fix --exit-zero

      - name: Run Pytest
        run: cd server && pytest tests/ -v --asyncio-mode=auto --cov=server
```

### release.yml — Build & Sign APK

```yaml
# File: .github/workflows/release.yml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build-release:
    runs-on: ubuntu-latest
    timeout-minutes: 30

    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: gradle

      - name: Extract version from tag
        id: version
        run: echo "VERSION=${GITHUB_REF#refs/tags/v}" >> $GITHUB_OUTPUT

      - name: Build Release APK
        run: cd android && ./gradlew assembleRelease --stacktrace --parallel

      - name: Create Release
        uses: ncipollo/release-action@v1
        with:
          artifacts: "android/app/build/outputs/apk/release/*.apk"
          bodyFile: "CHANGELOG.md"
          token: ${{ secrets.GITHUB_TOKEN }}
```

---

## APK Signing

### Setup Signing Keys (One-Time)

```bash
# Generate keystore
keytool -genkey -v \
  -keystore neuron-release.keystore \
  -keyalg RSA \
  -keysize 4096 \
  -validity 10000 \
  -alias neuron-key

# Base64 encode for GitHub Secrets
base64 -w 0 < neuron-release.keystore | pbcopy
```

---

## Release Checklist

Before tagging a release, verify:

```bash
# 1. Bump version
echo "0.2.0" # Update android/build.gradle.kts and server/setup.py

# 2. Update CHANGELOG.md
# Add section for new version with all changes

# 3. Run full test suite locally
cd android && ./gradlew test
cd ../server && pytest tests/ --asyncio-mode=auto

# 4. Build release APK
cd android && ./gradlew assembleRelease

# 5. Tag release
git tag -a v0.2.0 -m "Release 0.2.0: major feature X, bugfix Y"
git push origin v0.2.0

# 6. GitHub Actions automatically:
#    - Builds + signs APK
#    - Creates GitHub Release with APK
#    - Posts release notes
```

---

## CI/CD Performance Targets

| Check | Target | Actual |
|-------|--------|--------|
| Ktlint | < 30s | — |
| Unit Tests | < 2m | — |
| Build APK | < 1m | — |
| Python Tests | < 1m | — |
| **Total CI** | **< 5m** | — |

---

**Last Updated:** 2026-03-08 | **Version:** 0.1.0-alpha
