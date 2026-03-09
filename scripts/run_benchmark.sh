#!/bin/bash
# Neuron Integration Benchmark — 16-test automated runner
# Sends each command via ADB broadcast, waits for completion, logs results.
# Usage: ./scripts/run_benchmark.sh [--timeout 60]
#
# Prerequisites:
#   - Device connected via ADB
#   - Neuron installed and accessibility service enabled
#   - At least one LLM provider configured (Gemini/Ollama/OpenRouter)

set -euo pipefail

TIMEOUT=${1:-60}
REPORT_FILE="benchmark-results-$(date +%Y%m%d_%H%M%S).md"
PASS=0
FAIL=0
PARTIAL=0
SKIP=0
TOTAL=0

# Colors
GREEN='\033[0;32m'
RED='\033[0;31m'
YELLOW='\033[0;33m'
CYAN='\033[0;36m'
NC='\033[0m'

echo "=============================================="
echo "  Neuron Integration Benchmark"
echo "  Date: $(date)"
echo "  Timeout: ${TIMEOUT}s per test"
echo "=============================================="
echo ""

# Verify device connected
if ! adb get-state >/dev/null 2>&1; then
    echo -e "${RED}ERROR: No device connected. Connect via USB and retry.${NC}"
    exit 1
fi

DEVICE_MODEL=$(adb shell getprop ro.product.model 2>/dev/null | tr -d '\r')
ANDROID_VER=$(adb shell getprop ro.build.version.release 2>/dev/null | tr -d '\r')
echo "Device: ${DEVICE_MODEL} (Android ${ANDROID_VER})"
echo ""

# Verify Neuron accessibility service is active
if ! adb shell dumpsys accessibility 2>/dev/null | grep -q "ai.neuron"; then
    echo -e "${RED}ERROR: Neuron accessibility service is not enabled.${NC}"
    echo "Enable it in Settings > Accessibility > Neuron"
    exit 1
fi

# Start report
cat > "$REPORT_FILE" <<EOF
# Neuron Integration Benchmark Results

**Date:** $(date)
**Device:** ${DEVICE_MODEL} (Android ${ANDROID_VER})
**Neuron Version:** 0.1.0-alpha
**Timeout:** ${TIMEOUT}s per test

## Results

| # | Command | Result | Time | Notes |
|---|---------|--------|------|-------|
EOF

# Test definitions: ID|Command|Category
TESTS=(
    "T01|open the calculator|single-step"
    "T02|open the camera|single-step"
    "T03|go to the home screen|navigation"
    "T04|open YouTube|single-step"
    "T05|open WhatsApp and show me my chats|multi-step"
    "T06|open contacts and show my contact list|single-step"
    "T07|open Gmail and check my inbox|single-step"
    "T08|open Google Maps|single-step"
    "T09|open Chrome and search for weather forecast|multi-step"
    "T10|open Settings and go to Wi-Fi settings|multi-step"
    "T11|open the phone dialer|single-step"
    "T12|open the clock app|single-step"
    "T13|go back|navigation"
    "T14|open the Play Store and search for Instagram|multi-step"
    "T15|show recent apps|navigation"
    "T16|pull down the notification shade|navigation"
)

run_test() {
    local test_id="$1"
    local command="$2"
    local category="$3"

    TOTAL=$((TOTAL + 1))

    echo -ne "${CYAN}[$test_id]${NC} \"$command\" ... "

    # Go home first to reset state
    adb shell input keyevent KEYCODE_HOME 2>/dev/null
    sleep 1

    # Send command via broadcast
    local start_time=$(date +%s)
    adb shell am broadcast \
        -a ai.neuron.ACTION_TEXT_COMMAND \
        --es command "$command" \
        2>/dev/null

    # Wait for completion — poll logcat for DONE/ERROR/TIMEOUT
    local result="TIMEOUT"
    local notes=""
    local elapsed=0

    # Clear logcat and monitor for engine state changes
    adb logcat -c 2>/dev/null

    while [ $elapsed -lt "$TIMEOUT" ]; do
        sleep 2
        elapsed=$(( $(date +%s) - start_time ))

        # Check for DONE state
        if adb logcat -d -s NeuronBrain NeuronAS 2>/dev/null | grep -q "EngineState.Done\|state=DONE\|Task completed"; then
            result="PASS"
            break
        fi

        # Check for ERROR state
        if adb logcat -d -s NeuronBrain NeuronAS 2>/dev/null | grep -q "EngineState.Error\|state=ERROR"; then
            result="FAIL"
            notes=$(adb logcat -d -s NeuronBrain 2>/dev/null | grep -i "error" | tail -1 | sed 's/.*: //')
            break
        fi
    done

    local end_time=$(date +%s)
    local duration=$((end_time - start_time))

    # Log result
    case "$result" in
        PASS)
            echo -e "${GREEN}PASS${NC} (${duration}s)"
            PASS=$((PASS + 1))
            ;;
        FAIL)
            echo -e "${RED}FAIL${NC} (${duration}s) — $notes"
            FAIL=$((FAIL + 1))
            ;;
        TIMEOUT)
            echo -e "${YELLOW}TIMEOUT${NC} (${TIMEOUT}s)"
            # Check if partial progress was made
            if adb logcat -d -s NeuronBrain 2>/dev/null | grep -q "Executing\|EngineState.Executing"; then
                result="PARTIAL"
                PARTIAL=$((PARTIAL + 1))
                notes="Partial progress before timeout"
                echo -e "  ${YELLOW}^ Partial progress detected${NC}"
            else
                FAIL=$((FAIL + 1))
            fi
            ;;
    esac

    echo "| $test_id | $command | **$result** | ${duration}s | $notes |" >> "$REPORT_FILE"
}

# Run all tests
for test_def in "${TESTS[@]}"; do
    IFS='|' read -r test_id command category <<< "$test_def"
    run_test "$test_id" "$command" "$category"
done

# Summary
PASS_RATE=0
EFFECTIVE_TOTAL=$((TOTAL - SKIP))
if [ $EFFECTIVE_TOTAL -gt 0 ]; then
    PASS_RATE=$(( (PASS * 100) / EFFECTIVE_TOTAL ))
fi

echo ""
echo "=============================================="
echo "  RESULTS SUMMARY"
echo "=============================================="
echo -e "  Pass:    ${GREEN}${PASS}${NC}"
echo -e "  Fail:    ${RED}${FAIL}${NC}"
echo -e "  Partial: ${YELLOW}${PARTIAL}${NC}"
echo -e "  Skip:    ${SKIP}"
echo -e "  Total:   ${TOTAL}"
echo ""
echo -e "  Pass Rate: ${PASS_RATE}% (${PASS}/${EFFECTIVE_TOTAL} excl. skips)"
echo ""

if [ $PASS_RATE -ge 70 ]; then
    echo -e "  ${GREEN}TARGET MET (>=70%)${NC}"
else
    echo -e "  ${RED}TARGET NOT MET (<70%)${NC}"
fi

echo "=============================================="
echo "Report saved to: $REPORT_FILE"

# Append summary to report
cat >> "$REPORT_FILE" <<EOF

## Summary

| Metric | Value |
|--------|-------|
| Passed | $PASS |
| Failed | $FAIL |
| Partial | $PARTIAL |
| Skipped | $SKIP |
| Total | $TOTAL |
| **Pass Rate** | **${PASS_RATE}%** (${PASS}/${EFFECTIVE_TOTAL}) |
| Target (>=70%) | $([ $PASS_RATE -ge 70 ] && echo "MET" || echo "NOT MET") |
EOF
