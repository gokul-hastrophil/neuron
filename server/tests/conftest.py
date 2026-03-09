"""Shared fixtures for Neuron server tests."""

import pytest


@pytest.fixture
def mock_adb_response():
    """Factory for mock ADB responses."""

    def _make(stdout: str = "", returncode: int = 0, stderr: str = ""):
        class MockProcess:
            def __init__(self):
                self.returncode = returncode

            async def communicate(self):
                return stdout.encode(), stderr.encode()

        return MockProcess()

    return _make


@pytest.fixture
def mock_rpc_response():
    """Factory for mock Neuron RPC responses."""

    def _make(result: dict | None = None, error: str | None = None):
        resp = {"jsonrpc": "2.0", "id": 1}
        if error:
            resp["error"] = {"code": -1, "message": error}
        else:
            resp["result"] = result or {}
        return resp

    return _make
