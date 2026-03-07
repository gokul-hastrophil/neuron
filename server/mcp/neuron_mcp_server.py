#!/usr/bin/env python3
"""
Neuron MCP Server
Exposes Android phone control as MCP tools.
Connects via ADB bridge to phone running Neuron Portal APK.

Usage:
    python neuron_mcp_server.py

Compatible with: Claude Desktop, Claude Code, Cursor, any MCP client
"""

import asyncio
import base64
import json
import logging
import os
import subprocess
from typing import Any

from mcp.server import Server
from mcp.server.stdio import stdio_server
from mcp.types import (
    CallToolResult,
    ListToolsResult,
    TextContent,
    Tool,
    ImageContent,
)

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("neuron-mcp")

# Configuration
NEURON_SECRET = os.getenv("NEURON_SECRET_TOKEN", "")
ADB_DEVICE = os.getenv("ADB_DEVICE_ID", "")  # empty = auto-detect

server = Server("neuron-android-mcp")


# ── ADB Bridge ────────────────────────────────────────────────────────────────

async def adb(cmd: list[str], timeout: float = 10.0) -> str:
    """Run ADB command and return stdout."""
    device_args = ["-s", ADB_DEVICE] if ADB_DEVICE else []
    full_cmd = ["adb"] + device_args + cmd
    
    try:
        result = await asyncio.wait_for(
            asyncio.create_subprocess_exec(
                *full_cmd,
                stdout=asyncio.subprocess.PIPE,
                stderr=asyncio.subprocess.PIPE,
            ),
            timeout=timeout
        )
        stdout, stderr = await result.communicate()
        if result.returncode != 0:
            raise RuntimeError(f"ADB error: {stderr.decode()}")
        return stdout.decode().strip()
    except asyncio.TimeoutError:
        raise RuntimeError(f"ADB command timed out: {' '.join(cmd)}")


async def neuron_rpc(method: str, params: dict = None, timeout: float = 15.0) -> dict:
    """Call Neuron Portal APK via ADB port forward."""
    # Forward local port to Neuron's internal server on device
    await adb(["forward", "tcp:7385", "tcp:7385"])
    
    import httpx
    payload = {
        "jsonrpc": "2.0",
        "id": 1,
        "method": method,
        "params": params or {}
    }
    headers = {"X-Neuron-Token": NEURON_SECRET} if NEURON_SECRET else {}
    
    async with httpx.AsyncClient(timeout=timeout) as client:
        response = await client.post(
            "http://localhost:7385/rpc",
            json=payload,
            headers=headers
        )
        return response.json()


# ── Tool Definitions ──────────────────────────────────────────────────────────

TOOLS = [
    Tool(
        name="neuron_read_ui_tree",
        description="Read the current Android screen's UI element tree as structured JSON. Returns all interactive elements, text, and their positions. Use this to understand what's on screen before acting.",
        inputSchema={
            "type": "object",
            "properties": {
                "include_non_interactive": {
                    "type": "boolean",
                    "description": "Include non-interactive elements like labels. Default: false",
                    "default": False
                }
            }
        }
    ),
    Tool(
        name="neuron_take_screenshot",
        description="Take a screenshot of the current Android screen. Returns base64-encoded JPEG image.",
        inputSchema={"type": "object", "properties": {}}
    ),
    Tool(
        name="neuron_tap",
        description="Tap a UI element on the Android screen.",
        inputSchema={
            "type": "object",
            "properties": {
                "element_id": {
                    "type": "string",
                    "description": "Resource ID of element to tap (from UI tree)"
                },
                "element_text": {
                    "type": "string",
                    "description": "Visible text of element to tap (fallback if no ID)"
                },
                "x": {"type": "number", "description": "X coordinate to tap"},
                "y": {"type": "number", "description": "Y coordinate to tap"}
            }
        }
    ),
    Tool(
        name="neuron_type_text",
        description="Type text into the currently focused or specified text input on Android.",
        inputSchema={
            "type": "object",
            "properties": {
                "text": {"type": "string", "description": "Text to type"},
                "element_id": {"type": "string", "description": "Optional: resource ID of input field"}
            },
            "required": ["text"]
        }
    ),
    Tool(
        name="neuron_swipe",
        description="Perform a swipe gesture on the Android screen.",
        inputSchema={
            "type": "object",
            "properties": {
                "direction": {
                    "type": "string",
                    "enum": ["up", "down", "left", "right"],
                    "description": "Direction to swipe"
                },
                "from_x": {"type": "number"},
                "from_y": {"type": "number"},
                "to_x": {"type": "number"},
                "to_y": {"type": "number"},
                "duration_ms": {"type": "number", "default": 300}
            }
        }
    ),
    Tool(
        name="neuron_launch_app",
        description="Launch an Android app by package name or common name.",
        inputSchema={
            "type": "object",
            "properties": {
                "package_name": {
                    "type": "string",
                    "description": "Package name (e.g. 'com.whatsapp') or common name (e.g. 'WhatsApp')"
                }
            },
            "required": ["package_name"]
        }
    ),
    Tool(
        name="neuron_navigate",
        description="Perform a navigation action on Android.",
        inputSchema={
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["home", "back", "recents", "notifications"],
                    "description": "Navigation action to perform"
                }
            },
            "required": ["action"]
        }
    ),
    Tool(
        name="neuron_run_task",
        description="Execute a complete task on Android using natural language. Neuron will autonomously navigate apps and complete the task. Use this for multi-step tasks.",
        inputSchema={
            "type": "object",
            "properties": {
                "goal": {
                    "type": "string",
                    "description": "Natural language task description, e.g. 'Send a WhatsApp message to John saying hello'"
                },
                "require_confirmation": {
                    "type": "boolean",
                    "description": "Pause for user confirmation before irreversible actions. Default: true",
                    "default": True
                }
            },
            "required": ["goal"]
        }
    ),
    Tool(
        name="neuron_list_apps",
        description="List all installed apps on the Android device.",
        inputSchema={"type": "object", "properties": {}}
    ),
    Tool(
        name="neuron_get_device_info",
        description="Get information about the connected Android device.",
        inputSchema={"type": "object", "properties": {}}
    ),
]


# ── Tool Handlers ─────────────────────────────────────────────────────────────

@server.list_tools()
async def list_tools() -> ListToolsResult:
    return ListToolsResult(tools=TOOLS)


@server.call_tool()
async def call_tool(name: str, arguments: dict[str, Any]) -> CallToolResult:
    logger.info(f"MCP tool called: {name} with {arguments}")
    
    try:
        if name == "neuron_read_ui_tree":
            result = await neuron_rpc("ui.getTree", {
                "includeNonInteractive": arguments.get("include_non_interactive", False)
            })
            tree_json = json.dumps(result.get("result", {}), indent=2)
            return CallToolResult(content=[TextContent(type="text", text=tree_json)])
        
        elif name == "neuron_take_screenshot":
            result = await neuron_rpc("screen.capture")
            b64 = result.get("result", {}).get("imageBase64", "")
            return CallToolResult(content=[
                TextContent(type="text", text="Screenshot captured"),
                ImageContent(type="image", data=b64, mimeType="image/jpeg")
            ])
        
        elif name == "neuron_tap":
            params = {}
            if "element_id" in arguments:
                params["elementId"] = arguments["element_id"]
            elif "element_text" in arguments:
                params["elementText"] = arguments["element_text"]
            elif "x" in arguments and "y" in arguments:
                params["x"] = arguments["x"]
                params["y"] = arguments["y"]
            result = await neuron_rpc("action.tap", params)
            success = result.get("result", {}).get("success", False)
            return CallToolResult(content=[TextContent(type="text", text=f"Tap {'succeeded' if success else 'failed'}")])
        
        elif name == "neuron_type_text":
            result = await neuron_rpc("action.typeText", {
                "text": arguments["text"],
                "elementId": arguments.get("element_id")
            })
            return CallToolResult(content=[TextContent(type="text", text="Text typed successfully")])
        
        elif name == "neuron_swipe":
            result = await neuron_rpc("action.swipe", arguments)
            return CallToolResult(content=[TextContent(type="text", text="Swipe performed")])
        
        elif name == "neuron_launch_app":
            result = await neuron_rpc("app.launch", {"packageOrName": arguments["package_name"]})
            launched = result.get("result", {}).get("packageName", "unknown")
            return CallToolResult(content=[TextContent(type="text", text=f"Launched: {launched}")])
        
        elif name == "neuron_navigate":
            result = await neuron_rpc("action.navigate", {"action": arguments["action"]})
            return CallToolResult(content=[TextContent(type="text", text=f"Navigated: {arguments['action']}")])
        
        elif name == "neuron_run_task":
            result = await neuron_rpc("task.execute", {
                "goal": arguments["goal"],
                "requireConfirmation": arguments.get("require_confirmation", True)
            }, timeout=120.0)  # Extended timeout for full tasks
            
            task_result = result.get("result", {})
            status = task_result.get("status", "unknown")
            summary = task_result.get("summary", "No summary available")
            steps = task_result.get("stepsCompleted", 0)
            
            return CallToolResult(content=[TextContent(
                type="text",
                text=f"Task {status}\nSteps completed: {steps}\nSummary: {summary}"
            )])
        
        elif name == "neuron_list_apps":
            result = await neuron_rpc("apps.list")
            apps = result.get("result", {}).get("apps", [])
            apps_text = "\n".join([f"{a['label']}: {a['packageName']}" for a in apps[:50]])
            return CallToolResult(content=[TextContent(type="text", text=f"Installed apps:\n{apps_text}")])
        
        elif name == "neuron_get_device_info":
            info = await adb(["shell", "getprop", "ro.build.version.release"])
            model = await adb(["shell", "getprop", "ro.product.model"])
            return CallToolResult(content=[TextContent(
                type="text",
                text=f"Device: {model.strip()}\nAndroid: {info.strip()}"
            )])
        
        else:
            return CallToolResult(
                content=[TextContent(type="text", text=f"Unknown tool: {name}")],
                isError=True
            )
    
    except Exception as e:
        logger.error(f"Tool error {name}: {e}")
        return CallToolResult(
            content=[TextContent(type="text", text=f"Error: {str(e)}")],
            isError=True
        )


# ── Main ──────────────────────────────────────────────────────────────────────

async def main():
    logger.info("Neuron MCP Server starting...")
    logger.info(f"ADB device: {ADB_DEVICE or 'auto-detect'}")
    logger.info(f"Auth: {'enabled' if NEURON_SECRET else 'disabled (set NEURON_SECRET_TOKEN)'}")
    
    # Verify ADB connection
    try:
        devices = await adb(["devices"])
        logger.info(f"ADB devices:\n{devices}")
    except Exception as e:
        logger.warning(f"ADB check failed: {e} — start ADB daemon or connect device")
    
    async with stdio_server() as (read_stream, write_stream):
        await server.run(read_stream, write_stream, server.create_initialization_options())


if __name__ == "__main__":
    asyncio.run(main())
