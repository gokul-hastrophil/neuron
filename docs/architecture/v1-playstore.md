# Neuron V1 — Play Store-first execution contract

Status: Proposed (feature branch). This is a contract, not a working Android executor.

## Release boundary

The Play Store build uses explicit Android intents, supported platform APIs and user-defined deterministic routines. It does not ship generic AI-driven AccessibilityService navigation, continuous screen capture, ADB, or an ARTEMIS daemon. ARTEMIS is a development/test tool only. Play policy must be reviewed again at release.

## Trust boundary

User request → local deterministic parser or authenticated AI gateway → **untrusted** `tool-proposal.schema.json` → Android schema validation → app-owned policy authorization → optional confirmation → typed tool adapter → observable outcome → redacted local audit.

The gateway cannot authorize actions. The model's text, confidence, and reason never constitute consent. External documents, screens, messages and web content are untrusted. No model-generated tool names outside the allowlist can execute.

## V1 action semantics

- `apps.open`: launch an installed application; verify launch intent resolution, not arbitrary app state.
- `maps.navigate`: open a maps navigation intent; report **handoff**, not arrival.
- `web.search`: open an approved search intent; report **handoff**, not successful web retrieval.
- `reminder.create/list/cancel`: local persisted reminders with permission-aware scheduling.
- `briefing.generate`: local user-authorized reminders/calendar summary.
- `memory.propose`: **proposal only**; user must approve before storage.
- `message.prepare`: **draft/handoff only**; no automated sending.

The Android layer must check installed packages, user permission, contact ambiguity, reminder schedule validity and other runtime context. Schema validation alone does not guarantee any of these.

## Lifecycle and safety invariants

States: CREATED → RESOLVING → AUTHORIZING → [AWAITING_CONFIRMATION] → RUNNING → VERIFYING → COMPLETED. Terminal alternatives: FAILED, CANCELLED, NEEDS_USER_ACTION. Every task has an app-generated UUID, monotonic deadline, cancellation signal and redacted audit record. Idempotency keys prevent duplicate reminder creation. Never automatically retry uncertain external side effects. A handoff is not a verified completion.

## Acceptance criteria

1. Valid examples for each allowlisted tool pass Draft 2020-12 JSON Schema validation.
2. Unknown tools, extra properties, missing required fields, malformed date-times and cross-tool argument substitutions fail validation.
3. A valid proposal cannot bypass the separate Android policy/confirmation layer.
4. No secret or private user memory is required in a proposal.
5. All clients reject unsupported schema versions.
6. Local/offline deterministic tools do not depend on cloud availability.

## Implementation sequence

1. Contract tests and cross-language schema validation (this PR).
2. Kotlin task coordinator with durable states and idempotency.
3. Android policy gate and confirmation UI.
4. Intent-based tools, local reminders and task verification.
5. FastAPI gateway that emits proposals, never direct privileged actions.
6. Device testing and Play Store policy review.

## Scope decisions

No always-on microphone, remote phone-control service, unrestricted background screenshots, automated payments, or generic autonomous tapping in V1. A user-controlled sideload research build may be developed separately but is not part of this release contract.
