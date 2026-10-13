"""Bounded Codex CLI runner. Run ONLY inside a disposable sandbox/worktree.

This adapter deliberately never passes credentials, shells, or arbitrary flags
from issue text. Git operations and PR creation are separate trusted steps.
"""
import os
import subprocess
from pathlib import Path


class CodexExecutionError(RuntimeError):
    pass


def run_codex(prompt: str, workspace: str, timeout_seconds: int = 1200) -> str:
    root = Path(workspace).resolve(strict=True)
    if not (root / ".git").exists():
        raise CodexExecutionError("Workspace must be a disposable Git checkout")
    if not 30 <= timeout_seconds <= 3600:
        raise ValueError("timeout_seconds must be between 30 and 3600")
    if len(prompt) > 16000:
        raise ValueError("Prompt exceeds 16000 characters")

    # Only explicitly provided Codex runtime variables cross into the worker.
    # Never inherit GitHub credentials, SSH agents or host secrets.
    environment = {
        "PATH": os.environ.get("PATH", "/usr/local/bin:/usr/bin:/bin"),
        "HOME": os.environ.get("CODEX_SANDBOX_HOME", "/nonexistent"),
        "CODEX_HOME": os.environ.get("CODEX_SANDBOX_HOME", "/nonexistent"),
    }
    if "CODEX_API_KEY" in os.environ:
        environment["CODEX_API_KEY"] = os.environ["CODEX_API_KEY"]

    command = [
        "codex", "exec", "--sandbox", "workspace-write",
        "--skip-git-repo-check", "-",
    ]
    try:
        result = subprocess.run(
            command, input=prompt, text=True, cwd=root, env=environment,
            capture_output=True, timeout=timeout_seconds, check=False,
        )
    except subprocess.TimeoutExpired as exc:
        raise CodexExecutionError("Codex exceeded time budget") from exc
    except FileNotFoundError as exc:
        raise CodexExecutionError("Codex CLI is not installed") from exc
    if result.returncode != 0:
        # Do not expose raw stderr: it may contain sensitive tool output.
        raise CodexExecutionError(f"Codex exited with code {result.returncode}")
    return result.stdout[-12000:]
