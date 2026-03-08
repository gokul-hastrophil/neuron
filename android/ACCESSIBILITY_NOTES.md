# Neuron Accessibility Notes — OEM Quirks & Compatibility

Last tested: 2026-03-08

---

## Tested Devices

| Device | OS | Skin | API | Status |
|--------|----|------|-----|--------|
| Xiaomi Redmi Note 9 Pro | Android 12 | MIUI Global 14.0.3 (V140) | 31 | Working with workarounds |
| Honor ELI-NX9 | Android 15 | MagicOS 9.0 | 35 | Working — full WhatsApp E2E passed |

---

## Xiaomi / MIUI

### E2E Test Results

**Calculator test (42 + 8 = 50):** PASSED
- Launched `com.hihonor.calculator/.Calculator` via Intent
- Dumped UI tree — all button resource IDs detected (digit_0-9, op_add, op_clr, eq, etc.)
- Extracted bounds from UI nodes, computed center coordinates
- Tapped C, 4, 2, +, 8, = in sequence via `input tap`
- Result: **50** displayed correctly
- Note: MIUI Calculator text content not included in `uiautomator dump` XML text attribute (empty), but resource IDs and bounds are correct

**Contacts app:** PARTIAL
- MIUI Contacts does not expose resource IDs (only `android:id/content`)
- Content-descriptions also missing
- Must rely on coordinate-based taps or text search for MIUI system apps

### Issues Found

1. **MIUI system apps don't expose resource IDs**
   - Contacts, Messages, and other MIUI system apps have empty `resource-id` attributes
   - Third-party apps and some Honor/Huawei rebranded apps (Calculator) do expose IDs
   - Impact: Cannot use `findAccessibilityNodeInfosByViewId()` for MIUI system apps
   - Fix: Fall back to coordinate-based taps using bounds, or text/content-desc matching

2. **MIUI Calculator text content missing from UI dump**
   - `uiautomator dump` shows empty `text=""` for formula and result TextViews
   - But `AccessibilityNodeInfo.getText()` may still return values at runtime
   - Impact: Cannot verify action results via UI dump text comparison
   - Fix: Use screenshot + OCR, or AccessibilityNodeInfo direct text access

3. **`uiautomator dump` throws FileNotFoundException**
   - Error: `ThemeCompatibilityLoader` can't find `theme_compatibility.xml`
   - Impact: Cosmetic only — dump still succeeds
   - Fix: None needed, ignore the stacktrace

2. **Install via USB blocked by default**
   - MIUI requires "Install via USB" toggle in Developer Options
   - Also requires "USB debugging (Security settings)" on some MIUI versions
   - Fix: User must enable both toggles manually

3. **Battery optimization kills AccessibilityService**
   - MIUI aggressively kills background services
   - Fix: Whitelist via `adb shell dumpsys deviceidle whitelist +ai.neuron`
   - User-facing: Guide users to Settings → Apps → Neuron → Battery Saver → No restrictions
   - Also recommend enabling "Autostart" in MIUI Security app

4. **Autostart permission required**
   - MIUI blocks services from auto-starting unless explicitly allowed
   - Location: Security app → Manage apps → Neuron → Autostart toggle
   - Without this, service may not restart after device reboot

5. **Overlay permission (SYSTEM_ALERT_WINDOW)**
   - MIUI may require separate overlay permission grant
   - ADB: `adb shell appops set ai.neuron SYSTEM_ALERT_WINDOW allow`
   - User-facing: Settings → Apps → Neuron → Other permissions → Display pop-up windows

### MIUI-Specific ADB Commands

```bash
# Whitelist from battery optimization
adb shell dumpsys deviceidle whitelist +ai.neuron

# Grant overlay permission
adb shell appops set ai.neuron SYSTEM_ALERT_WINDOW allow

# Check if service is bound
adb shell dumpsys accessibility | grep ai.neuron

# Force enable accessibility service
adb shell settings put secure enabled_accessibility_services ai.neuron/ai.neuron.accessibility.NeuronAccessibilityService
adb shell settings put secure accessibility_enabled 1
```

---

## Honor / MagicOS

### E2E Test Results

**Calculator test (42 + 8 = 50):** PASSED
- Same Calculator app as MIUI (`com.hihonor.calculator`)
- All resource IDs detected, bounds correct
- No `ThemeCompatibilityLoader` error (unlike MIUI)

**WhatsApp full E2E test:** PASSED
- Launched WhatsApp → chat list displayed
- Tapped search bar (`com.whatsapp:id/my_search_bar`) → keyboard opened
- Typed "Monesh" → search results appeared with contact
- Tapped contact row (`com.whatsapp:id/contact_row_container`) → chat opened
- Tapped message input (`com.whatsapp:id/entry`) → focused
- Typed "Hello from Neuron AI Agent" → text appeared in input field
- Send button (`content-desc="Send"`) detected, clickable=true
- **Did NOT send** (safety rule: no irreversible actions without confirmation)

### Issues Found

1. **No `uiautomator dump` errors** — Honor MagicOS dumps cleanly without errors
2. **WhatsApp exposes full resource IDs** — `entry`, `send_container`, `conversations_row_contact_name`, etc.
3. **API 35: `recycle()` is deprecated** — no-op on this device, our code still calls it (safe)
4. **Install via USB works without extra toggle** (unlike MIUI)
5. **Service binds immediately** — no special permissions needed beyond standard accessibility

### MagicOS-Specific Notes
- Battery optimization behavior TBD (needs long-running test)
- Autostart permission may be needed — check Settings → Apps → Neuron → Startup management
- Overlay permission: may need `adb shell appops set ai.neuron SYSTEM_ALERT_WINDOW allow`

---

## Samsung / OneUI

> Not yet tested. Known issues to watch for:

- OneUI may restart accessibility services on its own schedule
- `performGlobalAction(GLOBAL_ACTION_RECENTS)` behavior differs
- Edge panels may interfere with swipe gestures
- Secure Folder apps have separate accessibility contexts

---

## Stock Android (Pixel)

> Not yet tested. Expected to have fewest issues.

---

## General Compatibility Notes

### AccessibilityNodeInfo.recycle()
- Deprecated in API 35 (Android 15) — becomes a no-op
- Still required for API 26-34 to prevent native memory leaks
- Our code calls recycle() and accepts the deprecation warning

### takeScreenshot() API
- Available from API 30+ (Android 11)
- Requires `canTakeScreenshot=true` in accessibility service config
- On API < 30, fall back to MediaProjection (requires user permission dialog)

### Display.DEFAULT_DISPLAY
- Multi-display devices (foldables, Dex) may have different display IDs
- Current implementation uses `DEFAULT_DISPLAY` only
- TODO: Support secondary displays in future

### Service Killing Prevention Checklist
1. Whitelist from battery optimization (Doze)
2. OEM-specific autostart permission
3. Set foreground notification (reduces kill priority)
4. Handle `onDestroy` gracefully with state persistence
5. Consider `START_STICKY` for service restart

---

*Updated: 2026-03-08 | Devices tested: 2 (Xiaomi MIUI, Honor MagicOS)*
