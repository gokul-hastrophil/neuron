# Neuron Installation Guide

## Prerequisites

- Android phone running Android 8.0+ (API 26+)
- USB debugging enabled (for sideloading)
- At least one LLM API key (Gemini recommended for free tier)

## Install via APK Sideload

1. Download the latest APK from [GitHub Releases](https://github.com/gokul-hastrophil/neuron/releases)
2. Transfer the APK to your phone (USB, ADB, or direct download)
3. Open the APK on your phone and allow installation from unknown sources when prompted
4. Alternatively, install via ADB:
   ```bash
   adb install neuron-v0.1.0-beta.apk
   ```

## First-Time Setup

1. **Open Neuron** — the onboarding wizard will start automatically
2. **Grant Accessibility Permission** — Neuron needs this to read UI elements and perform actions
   - Tap "Open Accessibility Settings"
   - Find "Neuron" in the list
   - Toggle it ON
   - Confirm the permission dialog
3. **Test a Command** — try "Open Settings" to verify everything works
4. **Configure API Keys** (optional for cloud LLM)
   - Open Settings in Neuron
   - Enter your Gemini API key (get one at [aistudio.google.com](https://aistudio.google.com/))
   - Or enter an OpenRouter API key for access to multiple models

## Grant Overlay Permission

Neuron displays a floating bubble overlay. If it doesn't appear:

```bash
# Via ADB (developer only)
adb shell appops set ai.neuron SYSTEM_ALERT_WINDOW allow
```

Or go to Android Settings → Apps → Neuron → Display over other apps → Allow.

## Usage

- **Text input**: Tap the floating bubble and type a command
- **Voice input**: Press and hold the bubble, speak your command, then release
- **Examples**:
  - "Open WhatsApp and message Mom"
  - "Set brightness to 50%"
  - "Open Chrome and search for weather"
  - "Take a screenshot"

## MCP Server (Desktop Integration)

To control your phone from Claude Desktop:

1. Connect your phone via USB with ADB debugging enabled
2. Start the MCP server on your computer:
   ```bash
   cd server && python -m mcp.neuron_mcp_server
   ```
3. Configure Claude Desktop — see [MCP Guide](../api/mcp_guide.md)

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Bubble doesn't appear | Grant overlay permission (see above) |
| "Accessibility service not active" | Re-enable in Settings → Accessibility → Neuron |
| Commands don't execute | Check that the target app is installed |
| Slow responses | Ensure internet connection for cloud LLM; or use on-device mode |
| Battery drain | Normal — accessibility services run continuously. Neuron is optimized for low idle usage |

## Uninstall

```bash
adb uninstall ai.neuron
```

Or go to Android Settings → Apps → Neuron → Uninstall.
