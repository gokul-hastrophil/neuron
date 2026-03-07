# Skill: MCP Integration

## Overview

MCP (Model Context Protocol) is the bridge that lets external AI clients (Claude Desktop, custom agents)
control an Android device through Neuron. The MCP server exposes Android accessibility actions as tools
that any MCP-compatible client can call.

---

## Architecture

```
Claude Desktop / External AI Client
        |
        | JSON-RPC over stdio (local) or WebSocket (remote)
        v
Neuron MCP Server (Python, stdio or ws://localhost:7384)
        |
        | ADB port forwarding (tcp:7385 -> device:7385)
        v
Neuron Android App (NeuronAccessibilityService)
        |
        | AccessibilityService API
        v
Target Android App (WhatsApp, Chrome, Settings, etc.)
```

### Key Design Decisions

- **Transport: stdio for Claude Desktop.** Claude Desktop launches the MCP server as a subprocess
  and communicates via stdin/stdout. This is the simplest and most reliable transport.
- **ADB bridge for device communication.** The Python server sends commands to the Android device
  via ADB port forwarding. The Neuron app runs an HTTP RPC server on port 7385.
- **JSON-RPC 2.0 protocol.** Standard MCP protocol. Tools are registered with schemas, called
  by name with typed arguments, and return structured results.

### Connection Flow

```
1. Claude Desktop starts the MCP server process:
   python -m mcp.neuron_mcp_server

2. MCP server initializes:
   a. Verify ADB device is connected (adb devices)
   b. Set up port forwarding (adb forward tcp:7385 tcp:7385)
   c. Ping the Neuron app RPC endpoint to confirm it's running
   d. Register all available tools

3. Client calls a tool (e.g., neuron_tap):
   a. MCP server receives JSON-RPC request via stdio
   b. Server translates to Neuron RPC call via ADB bridge
   c. NeuronAccessibilityService executes the action
   d. Result is returned through the chain

4. Error handling:
   - Device disconnected: ADBConnectionError with recovery instructions
   - App not running: DeviceNotFoundError, suggest launching Neuron
   - Action timeout: ActionTimeoutError after 10s, suggest screen check
```

---

## Tool Registration Pattern

### Using the @server.tool() Decorator

The MCP Python SDK provides a `@server.tool()` decorator that automatically registers tools
with their name, description, and input schema derived from type hints and docstrings.

```python
# server/mcp/neuron_mcp_server.py

import json
import asyncio
from mcp.server import Server
from mcp.server.stdio import stdio_server

from .adb_bridge import ADBBridge, ADBConnectionError

server = Server("neuron-mcp")
adb_bridge = ADBBridge()


@server.tool()
async def neuron_tap(target_id: str = "", x: int = 0, y: int = 0) -> str:
    """Tap on a UI element by resource ID or screen coordinates.

    If target_id is provided, taps the element with that resource ID.
    If x and y are provided, taps at those screen coordinates.
    At least one of target_id or (x, y) must be specified.

    Args:
        target_id: The Android resource ID of the element to tap (e.g. "com.whatsapp:id/send_btn")
        x: X coordinate for coordinate-based tap
        y: Y coordinate for coordinate-based tap

    Returns:
        JSON string with success status and optional error message.
    """
    if target_id:
        result = await adb_bridge.send_command("tap_by_id", {"id": target_id})
    elif x > 0 and y > 0:
        result = await adb_bridge.send_command("tap_xy", {"x": x, "y": y})
    else:
        return json.dumps({"success": False, "error": "Provide target_id or (x, y) coordinates"})

    return json.dumps(result)


@server.tool()
async def neuron_type_text(text: str, target_id: str = "") -> str:
    """Type text into a UI element or the currently focused field.

    If target_id is provided, focuses that element first then types.
    If no target_id, types into whatever field is currently focused.

    Args:
        text: The text to type
        target_id: Optional resource ID of the text field to type into

    Returns:
        JSON string with success status.
    """
    params = {"text": text}
    if target_id:
        params["id"] = target_id

    result = await adb_bridge.send_command("type_text", params)
    return json.dumps(result)


@server.tool()
async def neuron_read_ui_tree(max_depth: int = 10, include_invisible: bool = False) -> str:
    """Read the current Android screen UI tree as structured JSON.

    Returns a tree of UI elements with their properties: text, resource IDs,
    clickability, bounds, etc. This is the primary way to understand what's
    on screen.

    Args:
        max_depth: Maximum tree traversal depth (default 10)
        include_invisible: Whether to include non-visible elements (default False)

    Returns:
        JSON string containing the UI tree.
    """
    result = await adb_bridge.send_command("read_ui_tree", {
        "max_depth": max_depth,
        "include_invisible": include_invisible,
    })
    return json.dumps(result)


@server.tool()
async def neuron_take_screenshot() -> str:
    """Capture the current screen as a base64-encoded JPEG image.

    Requires API 30+ on the Android device. The screenshot is compressed
    to 80% JPEG quality to reduce size.

    Returns:
        JSON string with base64-encoded image data and metadata.
    """
    result = await adb_bridge.send_command("take_screenshot", {})
    return json.dumps(result)


@server.tool()
async def neuron_swipe(
    from_x: int,
    from_y: int,
    to_x: int,
    to_y: int,
    duration_ms: int = 300,
) -> str:
    """Perform a swipe gesture on the screen.

    Args:
        from_x: Starting X coordinate
        from_y: Starting Y coordinate
        to_x: Ending X coordinate
        to_y: Ending Y coordinate
        duration_ms: Swipe duration in milliseconds (default 300)

    Returns:
        JSON string with success status.
    """
    result = await adb_bridge.send_command("swipe", {
        "from_x": from_x,
        "from_y": from_y,
        "to_x": to_x,
        "to_y": to_y,
        "duration_ms": duration_ms,
    })
    return json.dumps(result)


@server.tool()
async def neuron_launch_app(package_name: str) -> str:
    """Launch an Android app by its package name.

    Args:
        package_name: The app's package name (e.g. "com.whatsapp", "com.android.chrome")

    Returns:
        JSON string with success status and the launched package.
    """
    result = await adb_bridge.send_command("launch_app", {"package": package_name})
    return json.dumps(result)


@server.tool()
async def neuron_go_home() -> str:
    """Press the Home button to return to the home screen.

    Returns:
        JSON string with success status.
    """
    result = await adb_bridge.send_command("global_action", {"action": "home"})
    return json.dumps(result)


@server.tool()
async def neuron_go_back() -> str:
    """Press the Back button.

    Returns:
        JSON string with success status.
    """
    result = await adb_bridge.send_command("global_action", {"action": "back"})
    return json.dumps(result)


@server.tool()
async def neuron_run_task(goal: str) -> str:
    """Run an autonomous task. Neuron will plan and execute the steps to achieve the goal.

    This is the high-level "do this for me" command. Neuron's Brain layer will:
    1. Classify the task complexity
    2. Route to the appropriate LLM tier
    3. Generate an action plan
    4. Execute the plan step by step
    5. Verify success after each step

    Args:
        goal: Natural language description of what to do (e.g. "send hi to Mom on WhatsApp")

    Returns:
        JSON string with task execution result and step-by-step trace.
    """
    result = await adb_bridge.send_command("run_task", {"goal": goal})
    return json.dumps(result)


@server.tool()
async def neuron_get_memory(query: str, memory_type: str = "all") -> str:
    """Query Neuron's memory system.

    Args:
        query: What to search for in memory
        memory_type: Type of memory to query: "preferences", "workflows", "traces", or "all"

    Returns:
        JSON string with matching memory entries.
    """
    result = await adb_bridge.send_command("get_memory", {
        "query": query,
        "type": memory_type,
    })
    return json.dumps(result)


@server.tool()
async def neuron_list_apps() -> str:
    """List all installed apps on the device with their package names.

    Returns:
        JSON string with array of {name, package_name, is_system} objects.
    """
    result = await adb_bridge.send_command("list_apps", {})
    return json.dumps(result)


# --- Server entry point ---

async def main():
    """Run the MCP server with stdio transport for Claude Desktop."""
    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
```

---

## ADB Bridge Pattern

The ADB bridge handles all communication between the Python MCP server and the Android device.

```python
# server/mcp/adb_bridge.py

import asyncio
import json
from typing import Any

import httpx


class ADBConnectionError(Exception):
    """Raised when ADB cannot connect to the device."""
    pass


class DeviceNotFoundError(Exception):
    """Raised when no Android device is connected."""
    pass


class ActionTimeoutError(Exception):
    """Raised when a device action exceeds its timeout."""
    pass


class ADBBridge:
    """Bridge between the MCP server and the Android device via ADB.

    Communication flow:
    1. ADB port forwarding: local:7385 -> device:7385
    2. HTTP POST to localhost:7385/rpc with JSON-RPC payload
    3. NeuronAccessibilityService handles the RPC on the device
    4. Response returned through the same chain
    """

    def __init__(
        self,
        device_port: int = 7385,
        local_port: int = 7385,
        command_timeout: float = 10.0,
    ):
        self.device_port = device_port
        self.local_port = local_port
        self.command_timeout = command_timeout
        self._client = httpx.AsyncClient(timeout=command_timeout)
        self._port_forwarded = False

    async def ensure_connection(self):
        """Verify device is connected and set up port forwarding."""
        # Check device is connected
        try:
            result = await self._run_adb("devices")
            lines = result.strip().split("\n")
            # First line is "List of devices attached", actual devices follow
            devices = [l for l in lines[1:] if l.strip() and "device" in l]
            if not devices:
                raise DeviceNotFoundError(
                    "No Android device connected. Run 'adb devices' to check."
                )
        except FileNotFoundError:
            raise ADBConnectionError(
                "ADB not found. Install Android SDK Platform Tools."
            )

        # Set up port forwarding
        if not self._port_forwarded:
            await self._run_adb(f"forward tcp:{self.local_port} tcp:{self.device_port}")
            self._port_forwarded = True

        # Ping the Neuron app
        try:
            response = await self._client.get(
                f"http://localhost:{self.local_port}/health",
                timeout=3.0,
            )
            if response.status_code != 200:
                raise ADBConnectionError(
                    "Neuron app is not responding. Make sure NeuronAccessibilityService is enabled."
                )
        except httpx.ConnectError:
            raise ADBConnectionError(
                "Cannot connect to Neuron app on device. "
                "Check that the Accessibility Service is enabled in Settings."
            )

    async def send_command(self, method: str, params: dict[str, Any]) -> dict:
        """Send a command to the Neuron app via ADB port forwarding.

        Args:
            method: The RPC method name (e.g. "tap_by_id", "read_ui_tree")
            params: Method parameters as a dictionary

        Returns:
            Response dictionary from the device.

        Raises:
            ADBConnectionError: Device not reachable
            ActionTimeoutError: Command timed out
        """
        await self.ensure_connection()

        payload = {
            "jsonrpc": "2.0",
            "method": method,
            "params": params,
            "id": 1,
        }

        try:
            response = await self._client.post(
                f"http://localhost:{self.local_port}/rpc",
                json=payload,
                timeout=self.command_timeout,
            )
            response.raise_for_status()

            data = response.json()

            # JSON-RPC error handling
            if "error" in data:
                return {
                    "success": False,
                    "error": data["error"].get("message", "Unknown RPC error"),
                    "code": data["error"].get("code", -1),
                }

            return data.get("result", {"success": True})

        except httpx.TimeoutException:
            raise ActionTimeoutError(
                f"Command '{method}' timed out after {self.command_timeout}s. "
                "The device may be unresponsive."
            )
        except httpx.ConnectError:
            self._port_forwarded = False  # Reset so we retry forwarding
            raise ADBConnectionError(
                "Lost connection to device. Attempting to reconnect on next call."
            )

    async def _run_adb(self, args: str) -> str:
        """Execute an ADB command and return stdout."""
        proc = await asyncio.create_subprocess_exec(
            "adb", *args.split(),
            stdout=asyncio.subprocess.PIPE,
            stderr=asyncio.subprocess.PIPE,
        )
        stdout, stderr = await asyncio.wait_for(proc.communicate(), timeout=10.0)

        if proc.returncode != 0:
            error_msg = stderr.decode().strip()
            raise ADBConnectionError(f"ADB command failed: adb {args}\n{error_msg}")

        return stdout.decode()

    async def close(self):
        """Clean up resources."""
        await self._client.aclose()
        # Remove port forwarding
        try:
            await self._run_adb(f"forward --remove tcp:{self.local_port}")
        except Exception:
            pass  # Best effort cleanup
```

---

## Claude Desktop Configuration

### Linux Configuration

Save to `~/.config/claude/claude_desktop_config.json`:

```json
{
    "mcpServers": {
        "neuron": {
            "command": "python",
            "args": ["-m", "mcp.neuron_mcp_server"],
            "cwd": "/home/user/neuron/server",
            "env": {
                "NEURON_SECRET_TOKEN": "your-generated-token-here",
                "ANDROID_SDK_ROOT": "/home/user/Android/Sdk"
            }
        }
    }
}
```

### macOS Configuration

Save to `~/Library/Application Support/Claude/claude_desktop_config.json`:

```json
{
    "mcpServers": {
        "neuron": {
            "command": "python3",
            "args": ["-m", "mcp.neuron_mcp_server"],
            "cwd": "/Users/username/neuron/server",
            "env": {
                "NEURON_SECRET_TOKEN": "your-generated-token-here",
                "ANDROID_HOME": "/Users/username/Library/Android/sdk"
            }
        }
    }
}
```

### Verifying the Connection

After configuring Claude Desktop:

1. Restart Claude Desktop
2. Look for "neuron" in the MCP tools panel (hammer icon)
3. Test with: "Use the neuron_list_apps tool to show installed apps"
4. If tools do not appear, check Claude Desktop logs:
   - Linux: `~/.config/claude/logs/`
   - macOS: `~/Library/Logs/Claude/`

---

## Adding New Tools -- Step by Step

### Step 1: Define the Tool Function

Add a new function with `@server.tool()` decorator in `server/mcp/neuron_mcp_server.py`:

```python
@server.tool()
async def neuron_scroll_to_element(
    target_text: str,
    direction: str = "down",
    max_scrolls: int = 5,
) -> str:
    """Scroll the screen until an element with the given text is visible.

    Args:
        target_text: Text of the element to find
        direction: Scroll direction - "up" or "down" (default "down")
        max_scrolls: Maximum number of scroll attempts (default 5)

    Returns:
        JSON with success status and whether the element was found.
    """
    result = await adb_bridge.send_command("scroll_to_element", {
        "target_text": target_text,
        "direction": direction,
        "max_scrolls": max_scrolls,
    })
    return json.dumps(result)
```

### Step 2: Implement the Android-Side Handler

In `NeuronAccessibilityService.kt`, add the RPC handler:

```kotlin
// In the RPC handler switch/when block:
"scroll_to_element" -> {
    val targetText = params.getString("target_text")
    val direction = params.optString("direction", "down")
    val maxScrolls = params.optInt("max_scrolls", 5)

    var found = false
    repeat(maxScrolls) { attempt ->
        // Check if element is visible
        val node = uiTreeReader.getUITree()
        if (node != null && findNodeByText(node, targetText) != null) {
            found = true
            return@repeat
        }
        // Scroll
        val scrollResult = if (direction == "down") {
            actionExecutor.swipe(540f, 1500f, 540f, 500f, 300)
        } else {
            actionExecutor.swipe(540f, 500f, 540f, 1500f, 300)
        }
        delay(500) // Wait for scroll to settle
    }

    mapOf("success" to found, "scrolls_performed" to if (found) 0 else maxScrolls)
}
```

### Step 3: Write Tests

```python
# server/tests/test_scroll_to_element.py

import pytest
import json
from unittest.mock import AsyncMock, patch


@pytest.fixture
def mock_adb_bridge():
    """Mock ADB bridge that simulates device responses."""
    bridge = AsyncMock()
    bridge.send_command = AsyncMock(return_value={
        "success": True,
        "scrolls_performed": 2,
    })
    return bridge


@pytest.mark.asyncio
async def test_scroll_to_element_found(mock_adb_bridge):
    """Test that scroll_to_element returns success when element is found."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_scroll_to_element

        result_json = await neuron_scroll_to_element(
            target_text="Settings",
            direction="down",
            max_scrolls=5,
        )
        result = json.loads(result_json)

        assert result["success"] is True
        mock_adb_bridge.send_command.assert_called_once_with(
            "scroll_to_element",
            {"target_text": "Settings", "direction": "down", "max_scrolls": 5},
        )


@pytest.mark.asyncio
async def test_scroll_to_element_not_found(mock_adb_bridge):
    """Test that scroll_to_element returns failure after max scrolls."""
    mock_adb_bridge.send_command.return_value = {
        "success": False,
        "scrolls_performed": 5,
    }

    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_scroll_to_element

        result_json = await neuron_scroll_to_element(
            target_text="NonExistent",
            direction="down",
            max_scrolls=5,
        )
        result = json.loads(result_json)

        assert result["success"] is False
        assert result["scrolls_performed"] == 5
```

### Step 4: Test with Claude Desktop

After implementing the tool:

1. Restart Claude Desktop (or reload MCP servers)
2. Verify the tool appears in the tools panel
3. Test: "Use neuron_scroll_to_element to find 'Bluetooth' in the Settings app"
4. Check the response includes scroll count and success status

### Step 5: Update Documentation

Add the new tool to the tool list in `CLAUDE.md`:

```json
{
  "tools": [
    "neuron_take_screenshot",
    "neuron_read_ui_tree",
    "neuron_tap",
    "neuron_type_text",
    "neuron_swipe",
    "neuron_launch_app",
    "neuron_go_home",
    "neuron_go_back",
    "neuron_run_task",
    "neuron_get_memory",
    "neuron_list_apps",
    "neuron_scroll_to_element"
  ]
}
```

---

## Error Handling Patterns

### Custom Exception Classes

```python
# server/mcp/errors.py

class NeuronMCPError(Exception):
    """Base exception for all Neuron MCP errors."""
    def __init__(self, message: str, suggestion: str = ""):
        super().__init__(message)
        self.suggestion = suggestion

    def to_dict(self) -> dict:
        result = {"error": self.__class__.__name__, "message": str(self)}
        if self.suggestion:
            result["suggestion"] = self.suggestion
        return result


class ADBConnectionError(NeuronMCPError):
    """ADB cannot reach the device."""
    def __init__(self, message: str = "Cannot connect to device via ADB"):
        super().__init__(
            message,
            suggestion="Check that: 1) USB debugging is enabled, "
                       "2) Device is connected (adb devices), "
                       "3) ADB is authorized on device",
        )


class DeviceNotFoundError(NeuronMCPError):
    """No Android device is connected."""
    def __init__(self):
        super().__init__(
            "No Android device found",
            suggestion="Connect an Android device with USB debugging enabled, "
                       "or start an emulator with: emulator -avd <name>",
        )


class ActionTimeoutError(NeuronMCPError):
    """A device action exceeded its timeout."""
    def __init__(self, action: str, timeout_s: float):
        super().__init__(
            f"Action '{action}' timed out after {timeout_s}s",
            suggestion="The device may be frozen or the app crashed. "
                       "Try: neuron_go_home to recover, or check the device screen manually.",
        )


class ServiceNotEnabledError(NeuronMCPError):
    """Neuron's AccessibilityService is not enabled."""
    def __init__(self):
        super().__init__(
            "NeuronAccessibilityService is not enabled",
            suggestion="Go to Settings > Accessibility > Neuron and enable the service.",
        )
```

### Error-Safe Tool Wrapper

```python
# server/mcp/neuron_mcp_server.py

import functools
import json
import traceback


def safe_tool(fn):
    """Decorator that catches all exceptions and returns structured error JSON.

    This ensures MCP tool calls never raise unhandled exceptions,
    which would crash the server or confuse the client.
    """
    @functools.wraps(fn)
    async def wrapper(*args, **kwargs):
        try:
            return await fn(*args, **kwargs)
        except NeuronMCPError as e:
            return json.dumps(e.to_dict())
        except asyncio.TimeoutError:
            return json.dumps({
                "error": "timeout",
                "message": f"Tool '{fn.__name__}' timed out",
                "suggestion": "Device may be unresponsive. "
                              "Try: adb shell input keyevent KEYCODE_WAKEUP",
            })
        except Exception as e:
            return json.dumps({
                "error": "internal_error",
                "message": str(e),
                "traceback": traceback.format_exc() if __debug__ else None,
            })
    return wrapper


# Apply to all tools:
@server.tool()
@safe_tool
async def neuron_tap(target_id: str = "", x: int = 0, y: int = 0) -> str:
    """Tap on a UI element by resource ID or screen coordinates."""
    # ... implementation ...
```

---

## Testing MCP Tools

### Test Setup with pytest-asyncio

```python
# server/tests/conftest.py

import pytest
import json
from unittest.mock import AsyncMock


@pytest.fixture
def mock_adb_bridge():
    """Provides a mock ADB bridge for testing without a real device."""
    bridge = AsyncMock()

    # Default responses for common commands
    bridge.send_command = AsyncMock(side_effect=_mock_command_handler)

    return bridge


async def _mock_command_handler(method: str, params: dict) -> dict:
    """Simulates device responses for testing."""
    responses = {
        "read_ui_tree": {
            "success": True,
            "tree": {
                "id": "root",
                "text": None,
                "className": "FrameLayout",
                "children": [
                    {
                        "id": "com.whatsapp:id/toolbar",
                        "text": "WhatsApp",
                        "className": "TextView",
                        "isClickable": False,
                        "children": [],
                    },
                    {
                        "id": "com.whatsapp:id/fab",
                        "text": None,
                        "contentDesc": "New chat",
                        "className": "FloatingActionButton",
                        "isClickable": True,
                        "children": [],
                    },
                ],
            },
        },
        "tap_by_id": {"success": True, "action": "tap", "target": params.get("id", "")},
        "tap_xy": {"success": True, "action": "tap", "x": params.get("x"), "y": params.get("y")},
        "type_text": {"success": True, "action": "type", "text": params.get("text", "")},
        "take_screenshot": {"success": True, "image_base64": "iVBOR...", "width": 1080, "height": 2400},
        "launch_app": {"success": True, "package": params.get("package", "")},
        "global_action": {"success": True, "action": params.get("action", "")},
        "list_apps": {
            "success": True,
            "apps": [
                {"name": "WhatsApp", "package_name": "com.whatsapp", "is_system": False},
                {"name": "Chrome", "package_name": "com.android.chrome", "is_system": False},
                {"name": "Settings", "package_name": "com.android.settings", "is_system": True},
            ],
        },
    }

    return responses.get(method, {"success": False, "error": f"Unknown method: {method}"})
```

### Integration Test Examples

```python
# server/tests/test_mcp_tools.py

import pytest
import json
from unittest.mock import patch


@pytest.mark.asyncio
async def test_tap_by_id(mock_adb_bridge):
    """Tapping by resource ID sends correct command to device."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_tap

        result_json = await neuron_tap(target_id="com.whatsapp:id/send_btn")
        result = json.loads(result_json)

        assert result["success"] is True
        mock_adb_bridge.send_command.assert_called_once_with(
            "tap_by_id", {"id": "com.whatsapp:id/send_btn"}
        )


@pytest.mark.asyncio
async def test_tap_by_coordinates(mock_adb_bridge):
    """Tapping by coordinates sends correct x,y to device."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_tap

        result_json = await neuron_tap(x=540, y=1200)
        result = json.loads(result_json)

        assert result["success"] is True
        mock_adb_bridge.send_command.assert_called_once_with(
            "tap_xy", {"x": 540, "y": 1200}
        )


@pytest.mark.asyncio
async def test_tap_without_params(mock_adb_bridge):
    """Tapping without target_id or coordinates returns an error."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_tap

        result_json = await neuron_tap()
        result = json.loads(result_json)

        assert result["success"] is False
        assert "error" in result


@pytest.mark.asyncio
async def test_read_ui_tree(mock_adb_bridge):
    """Reading UI tree returns structured tree data."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_read_ui_tree

        result_json = await neuron_read_ui_tree(max_depth=5)
        result = json.loads(result_json)

        assert result["success"] is True
        assert "tree" in result
        assert result["tree"]["id"] == "root"


@pytest.mark.asyncio
async def test_type_text(mock_adb_bridge):
    """Typing text sends the correct text to the device."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_type_text

        result_json = await neuron_type_text(text="Hello, Mom!", target_id="com.whatsapp:id/entry")
        result = json.loads(result_json)

        assert result["success"] is True
        mock_adb_bridge.send_command.assert_called_once_with(
            "type_text", {"text": "Hello, Mom!", "id": "com.whatsapp:id/entry"}
        )


@pytest.mark.asyncio
async def test_launch_app(mock_adb_bridge):
    """Launching an app sends the correct package name."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_launch_app

        result_json = await neuron_launch_app(package_name="com.whatsapp")
        result = json.loads(result_json)

        assert result["success"] is True


@pytest.mark.asyncio
async def test_list_apps(mock_adb_bridge):
    """Listing apps returns array of installed applications."""
    with patch("mcp.neuron_mcp_server.adb_bridge", mock_adb_bridge):
        from mcp.neuron_mcp_server import neuron_list_apps

        result_json = await neuron_list_apps()
        result = json.loads(result_json)

        assert result["success"] is True
        assert len(result["apps"]) == 3
        assert any(app["package_name"] == "com.whatsapp" for app in result["apps"])


@pytest.mark.asyncio
async def test_device_disconnected_error():
    """Tools return structured error when device is disconnected."""
    from mcp.adb_bridge import ADBBridge, ADBConnectionError

    bridge = ADBBridge()
    # Do not mock -- let it fail against a non-existent device

    with pytest.raises(ADBConnectionError):
        await bridge.send_command("read_ui_tree", {})
```

### Running Tests

```bash
# Run all MCP tests
cd /path/to/neuron/server
python -m pytest tests/test_mcp_tools.py -v

# Run with coverage
python -m pytest tests/ --cov=mcp --cov-report=term-missing

# Run only async tests
python -m pytest tests/ -m asyncio -v
```

---

## Common Pitfalls

- **ADB port forwarding is not persistent.** It resets when the device disconnects or ADB server restarts. The bridge should re-establish forwarding on each connection.
- **stdio transport must not print to stdout.** Any `print()` statement in the MCP server will corrupt the JSON-RPC stream. Use `stderr` for logging: `print("debug", file=sys.stderr)`.
- **Tool docstrings become descriptions.** Claude Desktop shows the docstring to the AI model. Write clear, actionable descriptions.
- **Type hints define the input schema.** The `@server.tool()` decorator generates the JSON schema from Python type hints. Use `str`, `int`, `bool` with defaults for optional params.
- **Never block the event loop.** All ADB calls must be async. Use `asyncio.create_subprocess_exec`, not `subprocess.run`.
- **Timeout every device call.** Devices can freeze. Every `send_command` must have a timeout (default 10s).
- **Test without a device.** Mock the ADB bridge for unit tests. Only use a real device for integration tests.
- **Claude Desktop caches tool lists.** After adding a new tool, restart Claude Desktop completely to refresh.
