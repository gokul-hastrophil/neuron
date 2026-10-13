# Neuron continuous engineering controller — Phase 1

**Status: foundation only; not a deployed or autonomous coding system.**

Cloud VPS: private FastAPI controller + PostgreSQL.
Ubuntu PC: worker polling over a private network (Tailscale recommended).
GitHub: work intake via open issues labeled `agent-ready`. No other issue is eligible.

## VPS setup
1. Install Docker Engine and Docker Compose.
2. Copy `.env.example` to `.env`; replace secrets. Keep `.env` out of Git.
3. Use a GitHub App installation token with minimal repository permissions. This starter accepts a static token; **token renewal must be implemented before unattended production use**.
4. Run `docker compose up --build -d`.
5. Keep port 8080 private. The compose file binds it to localhost.
6. Run `curl -H "Authorization: Bearer $CONTROLLER_TOKEN" -X POST http://127.0.0.1:8080/sync` to import labeled issues. Automate sync with a restricted scheduler later.

## Ubuntu worker
Install Python 3.12 and `pip install httpx`.
Connect to VPS through a private tunnel, then set `NEURON_CONTROLLER_URL`, `CONTROLLER_TOKEN`, and optionally `WORKER_ID`.
Run `python worker.py`.

**Important:** The sample worker deliberately does not run a model or modify GitHub. It records jobs as failed with an explicit message until a sandboxed coding runner is implemented.

## Next hardening requirements
- Replace shared bearer token with per-worker credentials and rotation.
- GitHub App installation-token renewal and webhook signature validation.
- Lease heartbeats and reconciliation for interrupted external writes.
- Bounded retries, dead-letter handling, audit log, concurrency limits.
- Coding-model API adapter with spending caps and isolated ephemeral worktrees.
- GitHub PR creation, CI verification and issue-comment notifications.
- Integration tests against disposable PostgreSQL and a GitHub test repository.
- Security review, backup/restore test, private networking and monitoring.

No direct pushes to main, auto-merge, or auto-deployment should be enabled.
