import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

from codex_runner import CodexExecutionError, run_codex


class TestCodexRunner(unittest.TestCase):
    def test_requires_git_checkout(self):
        with tempfile.TemporaryDirectory() as path:
            with self.assertRaises(CodexExecutionError):
                run_codex("implement a test", path)

    def test_uses_workspace_sandbox_and_stdin(self):
        with tempfile.TemporaryDirectory() as path:
            (Path(path) / ".git").mkdir()
            with patch("codex_runner.subprocess.run") as call:
                call.return_value.returncode = 0
                call.return_value.stdout = "done"
                self.assertEqual(run_codex("small task", path), "done")
                args = call.call_args
                self.assertIn("workspace-write", args.args[0])
                self.assertEqual(args.kwargs["input"], "small task")
                self.assertNotIn("GITHUB_TOKEN", args.kwargs["env"])

    def test_rejects_unbounded_time(self):
        with tempfile.TemporaryDirectory() as path:
            (Path(path) / ".git").mkdir()
            with self.assertRaises(ValueError):
                run_codex("task", path, timeout_seconds=999999)
