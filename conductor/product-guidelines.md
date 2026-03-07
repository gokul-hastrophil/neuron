# Product Guidelines - Neuron

## Voice and Tone

**Friendly and approachable.** Neuron should feel like a helpful assistant, not a cold tool. Use plain language, avoid jargon in user-facing text, and be encouraging when tasks succeed or fail.

- Use active voice: "Neuron sent your message" not "The message was sent by Neuron"
- Be brief in status updates: "Done!" not "The requested operation has been completed successfully"
- Be honest about failures: "I couldn't find the send button. Want me to try again?" not "An error occurred"

## Design Principles

### 1. Privacy First
Sensitive data (passwords, PINs, CVVs, banking screens, health data) never leaves the device. The SensitivityGate enforces T4 (on-device only) routing. When in doubt, keep it local.

### 2. Simplicity Over Features
Do fewer things well. A reliable tap is worth more than a flaky multi-app workflow. Ship the simplest version that works, then iterate.

### 3. Performance First
Every operation has a latency budget. UI tree capture < 50ms, actions < 200ms, on-device LLM < 500ms. If it's slow, users won't trust it.

### 4. Developer Experience Focused
Every capability is accessible via MCP, SDK, or REST. Clear APIs, good error messages, documented contracts. Developers should be productive within 10 minutes.

### 5. User Safety and Reliability
Never execute irreversible actions (send, pay, delete) without user confirmation. Always verify actions by re-reading the UI tree. Log everything to the audit trail.

### 6. Developer Extensibility
Third-party developers can register custom tools via the Neuron SDK. The MCP server exposes all Android control capabilities to external AI clients.

## UI/UX Standards

- **Overlay UI**: Minimal, non-intrusive floating bubble. Expands on tap for input/status.
- **Confirmation dialogs**: Required for irreversible actions. Clear description of what will happen.
- **Error states**: Always suggest a next step. Never show raw stack traces.
- **Accessibility**: The app itself must be fully accessible (ironic but important).
