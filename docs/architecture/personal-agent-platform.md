# Neuron Personal Agent — implementation architecture

Status: proposed architecture, not a claim of implemented functionality.

## Product boundary
Neuron is evolving from Android screen control into a proactive personal assistant. The Android app remains a **client and optional on-device execution adapter**, not the universal task orchestrator. The system must function with no Android device attached.

## Components
- **Client**: Android first; web interface later. Conversation, approvals, notifications, memory controls.
- **Control plane**: authenticated API, task state machine, scheduler, policy decision point, audit log.
- **Agent runtime**: observe → plan → propose typed tool call → authorize → execute → independently verify → checkpoint.
- **Tool adapters**: API-first (calendar, mail, files), isolated browser fallback, Android accessibility only where permitted and supported.
- **Memory**: short-lived working context, user-editable preferences, source-linked episodic outcomes; explicit retention and deletion.
- **Infrastructure**: PostgreSQL authoritative state; worker leases, retries and idempotency; Redis optional for signaling; private local tools and cloud model inference.

## Task state machine
QUEUED → RUNNING → VERIFYING → COMPLETED.
RUNNING → WAITING_APPROVAL → RUNNING.
Any active state → CANCELLED, FAILED or EXPIRED.
Persist checkpoints before external side effects. A lease expiry does **not** prove the side effect failed; reconcile before retry.

## Trust boundaries
1. Untrusted content from web pages, emails and documents is **data**, not instructions.
2. Models may propose actions but may never grant permissions or override the policy engine.
3. External writes, communications, purchases, deletion, credential access, and security settings require narrowly scoped authorization. No universal 'approve all'.
4. An approval binds exact actor, tool, normalized arguments, destination, expiry and task ID.
5. Cloud models receive redacted/minimized context; credentials and authentication codes are never placed in prompts.
6. Browser and code workers run without privileged host mounts or Docker socket access.
7. Users can pause all automation, inspect audit logs, revoke integrations, and delete memory.

## Initial consumer journey
Every morning: read authorized calendar and mail metadata → extract candidate commitments with citations → de-duplicate → propose reminders → obtain approval for writes → verify persisted reminders → notify with evidence.

## Definition of done for first production pilot
- Multi-user authentication and per-user authorization tested.
- OAuth scopes minimal; tokens encrypted at rest and revocable.
- Worker crash recovery, retries and idempotency tested.
- Tool approvals cannot be bypassed by prompt injection.
- End-to-end test using a real supported integration in a dedicated test account.
- Backup restoration, monitoring, privacy policy and deletion flow validated.

## Migration strategy
Keep existing Android modules intact. Introduce the backend contract separately, add an Android client adapter, then move orchestration to the durable runtime. Do not represent roadmap features as shipped capabilities.
