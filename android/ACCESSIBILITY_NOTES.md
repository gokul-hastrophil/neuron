# Neuron Accessibility Notes — OEM Quirks & Compatibility

Last tested: 2026-03-08

---

## Tested Devices

| Device | OS | Skin | API | Status |
|--------|----|------|-----|--------|
| Xiaomi Redmi Note 9 Pro | Android 12 | MIUI Global 14.0.3 (V140) | 31 | Working with workarounds |

---

## Xiaomi / MIUI

### Issues Found

1. **`uiautomator dump` throws FileNotFoundException**
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

*Updated: 2026-03-08 | Devices tested: 1*
