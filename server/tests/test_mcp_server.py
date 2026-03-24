"""Tests for Neuron MCP Server tool handlers."""

import json
import importlib.util
import os
import sys
from unittest.mock import AsyncMock, patch

import pytest

# Import the module under test
SERVER_DIR = os.path.join(os.path.dirname(__file__), "..")
sys.path.insert(0, SERVER_DIR)

# Skip entire module if the mcp SDK is not installed — neuron_mcp_server.py
# imports from mcp.server at module level and will fail without it.
try:
    _spec = importlib.util.spec_from_file_location(
        "neuron_mcp_server",
        os.path.join(SERVER_DIR, "mcp", "neuron_mcp_server.py"),
    )
    _mod = importlib.util.module_from_spec(_spec)
    _spec.loader.exec_module(_mod)

    adb = _mod.adb
    call_tool = _mod.call_tool
    TOOLS = _mod.TOOLS
except (ImportError, ModuleNotFoundError):
    pytest.skip("mcp SDK not installed — skipping MCP server tests", allow_module_level=True)


class TestToolDefinitions:
    """Verify all expected tools are registered."""

    def test_should_have_all_expected_tools(self):
        tool_names = {t.name for t in TOOLS}
        expected = {
            "neuron_read_ui_tree",
            "neuron_take_screenshot",
            "neuron_tap",
            "neuron_type_text",
            "neuron_swipe",
            "neuron_launch_app",
            "neuron_navigate",
            "neuron_run_task",
            "neuron_list_apps",
            "neuron_get_device_info",
        }
        assert expected.issubset(tool_names), f"Missing tools: {expected - tool_names}"

    def test_should_have_at_least_10_tools(self):
        assert len(TOOLS) >= 10

    def test_neuron_run_task_should_require_goal(self):
        task_tool = next(t for t in TOOLS if t.name == "neuron_run_task")
        assert "goal" in task_tool.inputSchema.get("required", [])

    def test_neuron_type_text_should_require_text(self):
        type_tool = next(t for t in TOOLS if t.name == "neuron_type_text")
        assert "text" in type_tool.inputSchema.get("required", [])

    def test_neuron_launch_app_should_require_package_name(self):
        launch_tool = next(t for t in TOOLS if t.name == "neuron_launch_app")
        assert "package_name" in launch_tool.inputSchema.get("required", [])


class TestAdbBridge:
    """Test the ADB bridge helper."""

    @pytest.mark.asyncio
    async def test_should_return_stdout_on_success(self, mock_adb_response):
        mock_proc = mock_adb_response(stdout="device123\tdevice")
        with patch("asyncio.create_subprocess_exec", return_value=mock_proc):
            with patch("asyncio.wait_for", return_value=mock_proc):
                result = await adb(["devices"])
                assert "device123" in result

    @pytest.mark.asyncio
    async def test_should_raise_on_adb_error(self, mock_adb_response):
        mock_proc = mock_adb_response(returncode=1, stderr="error: no devices")
        with patch("asyncio.create_subprocess_exec", return_value=mock_proc):
            with patch("asyncio.wait_for", return_value=mock_proc):
                with pytest.raises(RuntimeError, match="ADB error"):
                    await adb(["shell", "invalid"])


class TestToolHandlers:
    """Test individual tool call handlers."""

    @pytest.mark.asyncio
    async def test_read_ui_tree_should_return_json(self):
        mock_rpc = AsyncMock(return_value={
            "result": {"packageName": "com.whatsapp", "nodes": [{"id": "chat"}]},
        })
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_read_ui_tree", {})
            assert result.content[0].text
            parsed = json.loads(result.content[0].text)
            assert parsed["packageName"] == "com.whatsapp"

    @pytest.mark.asyncio
    async def test_take_screenshot_should_return_image(self):
        mock_rpc = AsyncMock(return_value={
            "result": {"imageBase64": "aGVsbG8="},
        })
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_take_screenshot", {})
            assert len(result.content) == 2
            assert result.content[0].text == "Screenshot captured"

    @pytest.mark.asyncio
    async def test_tap_should_call_rpc_with_element_id(self):
        mock_rpc = AsyncMock(return_value={"result": {"success": True}})
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_tap", {"element_id": "btn_send"})
            assert "succeeded" in result.content[0].text
            mock_rpc.assert_called_once()
            call_params = mock_rpc.call_args[0][1]
            assert call_params["elementId"] == "btn_send"

    @pytest.mark.asyncio
    async def test_type_text_should_send_text(self):
        mock_rpc = AsyncMock(return_value={"result": {}})
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_type_text", {"text": "hello world"})
            assert "successfully" in result.content[0].text

    @pytest.mark.asyncio
    async def test_launch_app_should_resolve_package(self):
        mock_rpc = AsyncMock(return_value={
            "result": {"packageName": "com.whatsapp"},
        })
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_launch_app", {"package_name": "com.whatsapp"})
            assert "com.whatsapp" in result.content[0].text

    @pytest.mark.asyncio
    async def test_navigate_should_perform_action(self):
        mock_rpc = AsyncMock(return_value={"result": {}})
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_navigate", {"action": "home"})
            assert "home" in result.content[0].text

    @pytest.mark.asyncio
    async def test_run_task_should_return_status(self):
        mock_rpc = AsyncMock(return_value={
            "result": {"status": "completed", "summary": "Opened calculator", "stepsCompleted": 2},
        })
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_run_task", {"goal": "open calculator"})
            text = result.content[0].text
            assert "completed" in text
            assert "2" in text

    @pytest.mark.asyncio
    async def test_unknown_tool_should_return_error(self):
        result = await call_tool("nonexistent_tool", {})
        assert result.isError is True

    @pytest.mark.asyncio
    async def test_tool_exception_should_return_error(self):
        mock_rpc = AsyncMock(side_effect=RuntimeError("ADB disconnected"))
        with patch.object(_mod, "neuron_rpc", mock_rpc):
            result = await call_tool("neuron_read_ui_tree", {})
            assert result.isError is True
            assert "ADB disconnected" in result.content[0].text
