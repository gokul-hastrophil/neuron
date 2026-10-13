# Codex + ChatGPT Work integration

## Division of responsibility
- **Codex CLI on Ubuntu:** coding, tests, local changes in disposable worktrees.
- **ChatGPT Work:** planning, backlog analysis, investigation and GitHub event-triggered workflows, subject to product permissions.
- **Controller on VPS:** persistent job state and dispatch.
- **GitHub:** issues, PRs, reviews and notifications.

ChatGPT Work is not invoked by the custom controller as a public API. Configure Work tasks in ChatGPT directly.

## Local Codex setup
1. Install Codex CLI from the official Codex documentation: https://developers.openai.com/codex/cli
2. Log in as the dedicated non-root worker user: `codex login`. On a headless machine use the documented device authentication flow.
3. Confirm with `codex login status`.
4. Run a harmless manual smoke test in a disposable clone: `codex exec --sandbox read-only "Summarize this repository."`
5. Do not copy `~/.codex/auth.json` into the repository, container images, CI logs or cloud VPS.
6. Keep GitHub App credentials in the controller, not the Codex sandbox.
7. Set `CODEX_SANDBOX_HOME` to a dedicated worker home. For ChatGPT sign-in, authenticate using that same home. The runner must be executed in a separate disposable container/VM with filesystem and network restrictions.

## IMPORTANT: not yet production-ready
The `codex_runner.py` module is an adapter, **not** a safe execution sandbox on its own.
It must run inside a restricted ephemeral container or VM, with no privileged mounts, no Docker socket, and no access to the host's personal files or credentials.
The current `worker.py` does not call this adapter. PR creation and CI verification are not yet implemented.

## ChatGPT Work
In ChatGPT, select Work, connect the GitHub repository, and create a project for Neuron. Use a scheduled task or a supported GitHub PR activity trigger to review progress and report blockers. Work and Codex Cloud have distinct task histories and access controls.

## Approval rules
No automatic merge, production deployment, purchases or permission escalation. GitHub issue label `agent-ready` identifies eligible tasks; the controller must still enforce execution and budget policy.
