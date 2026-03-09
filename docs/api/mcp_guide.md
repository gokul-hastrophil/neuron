# Neuron MCP Server Setup Guide

## Overview

Neuron exposes Android phone control as MCP (Model Context Protocol) tools. Any MCP-compatible client (Claude Desktop, Claude Code, Cursor) can control your Android device through natural language.

## Prerequisites

- Python 3.12+
- ADB installed and in PATH
- Android device connected via USB with ADB debugging enabled
- Neuron APK installed on the device

## Installation

```bash
cd server
pip install -r requirements.txt
```

## Running the Server

```bash
# stdio mode (for Claude Desktop / Claude Code)
python -m mcp.neuron_mcp_server

# Set auth token (recommended)
NEURON_SECRET_TOKEN=your_secret python -m mcp.neuron_mcp_server

# Specify device (multi-device setups)
ADB_DEVICE_ID=device_serial python -m mcp.neuron_mcp_server
```

## Claude Desktop Configuration

Add to `~/.config/claude/claude_desktop_config.json`:

```json
{
  "mcpServers": {
    "neuron": {
      "command": "python",
      "args": ["-m", "mcp.neuron_mcp_server"],
      "cwd": "/path/to/neuron/server",
      "env": {
        "NEURON_SECRET_TOKEN": "your_secret"
      }
    }
  }
}
```

## Available Tools

| Tool | Description |
|------|-------------|
| `neuron_read_ui_tree` | Read current screen UI elements as JSON |
| `neuron_take_screenshot` | Capture screen as base64 JPEG |
| `neuron_tap` | Tap an element by ID, text, or coordinates |
| `neuron_type_text` | Type text into a field |
| `neuron_swipe` | Swipe in a direction |
| `neuron_launch_app` | Launch an app by package or name |
| `neuron_navigate` | Go home, back, recents, notifications |
| `neuron_run_task` | Execute a full task autonomously |
| `neuron_list_apps` | List installed apps |
| `neuron_get_device_info` | Get device model and Android version |

## Authentication

Set `NEURON_SECRET_TOKEN` environment variable. The server sends it as `X-Neuron-Token` header to the on-device Neuron service. If unset, auth is disabled.

## Troubleshooting

- **"ADB error: no devices"** — Run `adb devices` to verify connection
- **"Connection refused on port 7385"** — Ensure Neuron APK is running on device
- **Timeout on `neuron_run_task`** — Complex tasks may need >30s; the tool uses 120s timeout
