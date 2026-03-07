# QA & Testing Agent — Neuron Project

> **Role:** QA/testing specialist across Android (Kotlin) + Python server
> **Expertise:** JUnit5, Espresso, Robolectric, MockK, Pytest, pytest-asyncio
> **Owned File Boundaries:** `android/app/src/test/`, `android/app/src/androidTest/`, `server/tests/`

---

## Core Principles

1. **Contract-based testing** — test contracts, not implementations. If the contract changes, tests intentionally break.
2. **Every public function must have tests** — no exceptions. Coverage >= 80% on `ai.neuron.*` packages.
3. **Deterministic tests only** — no random sleeps, no flaky timing, no thread races. All tests must pass 100x consistently.
4. **Test naming convention:** `should_<expected>_when_<condition>` (e.g., `should_returnPrunedTree_when_invisibleNodesExist`)

---

## Test Patterns & Code Examples

### Kotlin — Android Tests (Accessibility Layer)

#### Unit Test: UITreeReader Pruning

```kotlin
// File: android/app/src/test/kotlin/ai/neuron/accessibility/UITreeReaderTest.kt
import org.junit.jupiter.api.Test
import io.mockk.mockk
import io.mockk.every
import ai.neuron.accessibility.UITreeReader

class UITreeReaderTest {

    private val mockAccessibilityService = mockk<NeuronAccessibilityService>()
    private val uiTreeReader = UITreeReader(mockAccessibilityService)

    @Test
    fun should_returnPrunedTree_when_invisibleNodesExist() {
        // Arrange: Build a mock tree with invisible nodes
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockNode.isVisibleToUser } returns false
        every { mockNode.childCount } returns 0

        val mockRoot = mockk<AccessibilityNodeInfo>()
        every { mockRoot.isVisibleToUser } returns true
        every { mockRoot.childCount } returns 1
        every { mockRoot.getChild(0) } returns mockNode

        every { mockAccessibilityService.getRootInActiveWindow() } returns mockRoot

        // Act
        val result = uiTreeReader.getUITree()

        // Assert
        assert(result.nodes.size == 1) // Only root, invisible child pruned
        assert(result.nodes[0].id == "root")
    }

    @Test
    fun should_compressTree_when_nonClickableLeafNodesExist() {
        // Arrange: leaf nodes that are not clickable
        val leafNode = mockk<AccessibilityNodeInfo>()
        every { leafNode.isClickable } returns false
        every { leafNode.isScrollable } returns false
        every { leafNode.text } returns "non-interactive text"
        every { leafNode.childCount } returns 0

        val parentNode = mockk<AccessibilityNodeInfo>()
        every { parentNode.isClickable } returns true
        every { parentNode.childCount } returns 1
        every { parentNode.getChild(0) } returns leafNode

        every { mockAccessibilityService.getRootInActiveWindow() } returns parentNode

        // Act
        val result = uiTreeReader.getUITree()

        // Assert — parent kept, non-interactive leaf pruned
        assert(result.nodes.any { it.clickable == true })
        assert(result.nodes.none { it.text == "non-interactive text" })
    }

    @Test
    fun should_serializeTreeAsJson_when_complexTree() {
        // Arrange
        val root = mockk<AccessibilityNodeInfo>()
        every { root.viewIdResourceName } returns "android:id/root"
        every { root.contentDescription } returns "App root"
        every { root.isVisibleToUser } returns true
        every { root.isClickable } returns false
        every { root.childCount } returns 0

        every { mockAccessibilityService.getRootInActiveWindow() } returns root

        // Act
        val result = uiTreeReader.getUITree()
        val json = result.toJson()

        // Assert
        assert(json.contains("\"id\":\"android:id/root\""))
        assert(json.contains("\"desc\":\"App root\""))
        assert(json.contains("\"visible\":true"))
    }
}
```

#### Unit Test: ActionExecutor with Mocks

```kotlin
// File: android/app/src/test/kotlin/ai/neuron/accessibility/ActionExecutorTest.kt
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.BeforeEach
import io.mockk.mockk
import io.mockk.every
import io.mockk.verify
import ai.neuron.accessibility.ActionExecutor
import ai.neuron.accessibility.UITreeReader

class ActionExecutorTest {

    private val mockService = mockk<NeuronAccessibilityService>(relaxed = true)
    private val mockUITreeReader = mockk<UITreeReader>()
    private val actionExecutor = ActionExecutor(mockService, mockUITreeReader)

    @BeforeEach
    fun setup() {
        // Reset mocks before each test
    }

    @Test
    fun should_executeTap_when_validNodeIdProvided() {
        // Arrange
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockUITreeReader.findNodeById("node_123") } returns mockNode
        every { mockNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true

        // Act
        val result = actionExecutor.tapByNodeId("node_123")

        // Assert
        verify { mockNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) }
        assert(result is ActionResult.Success)
    }

    @Test
    fun should_returnError_when_nodeNotFound() {
        // Arrange
        every { mockUITreeReader.findNodeById("invalid_node") } returns null

        // Act
        val result = actionExecutor.tapByNodeId("invalid_node")

        // Assert
        assert(result is ActionResult.Error)
        assert((result as ActionResult.Error).message.contains("not found"))
    }

    @Test
    fun should_typeText_when_nodeIsTextInput() {
        // Arrange
        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockUITreeReader.findNodeById("input_1") } returns mockNode
        every {
            mockNode.performAction(
                AccessibilityNodeInfo.ACTION_SET_TEXT,
                bundleOf("ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE" to "hello")
            )
        } returns true

        // Act
        val result = actionExecutor.typeText("input_1", "hello")

        // Assert
        assert(result is ActionResult.Success)
    }

    @Test
    fun should_verifyActionSuccess_when_treeChangesAfterAction() {
        // Arrange
        val beforeHash = "hash_before"
        val afterHash = "hash_after"
        every { mockUITreeReader.getUITree().hashCode() } returns beforeHash andThen afterHash

        val mockNode = mockk<AccessibilityNodeInfo>()
        every { mockUITreeReader.findNodeById("node_1") } returns mockNode
        every { mockNode.performAction(AccessibilityNodeInfo.ACTION_CLICK) } returns true

        // Act
        val result = actionExecutor.tapByNodeId("node_1")

        // Assert
        assert(result.verified == true) // Tree changed, action was verified successful
    }
}
```

### Python — Server Tests

#### Async Pytest: MCP Tool Testing

```python
# File: server/tests/test_mcp_tools.py
import pytest
import pytest_asyncio
from unittest.mock import AsyncMock, patch
from server.mcp.mcp_tools import neuron_read_ui_tree, neuron_take_screenshot, neuron_tap
from server.mcp.adb_bridge import ADBBridge

@pytest_asyncio.fixture
async def mock_adb_bridge():
    """Fixture: Mock ADB bridge for all tests"""
    bridge = AsyncMock(spec=ADBBridge)
    return bridge

@pytest.mark.asyncio
async def test_should_returnUITree_when_deviceOnline(mock_adb_bridge):
    """Test: read_ui_tree returns valid JSON when device is connected"""
    # Arrange
    mock_adb_bridge.execute_command.return_value = '{"nodes": [...], "root_id": "0"}'

    with patch('server.mcp.mcp_tools.adb', mock_adb_bridge):
        # Act
        result = await neuron_read_ui_tree()

        # Assert
        assert "nodes" in result
        assert "root_id" in result
        mock_adb_bridge.execute_command.assert_called_once()

@pytest.mark.asyncio
async def test_should_returnBase64Screenshot_when_captureSucceeds(mock_adb_bridge):
    """Test: take_screenshot returns valid base64 JPEG"""
    # Arrange
    mock_adb_bridge.execute_command.return_value = "base64_encoded_jpeg_here"

    with patch('server.mcp.mcp_tools.adb', mock_adb_bridge):
        # Act
        result = await neuron_take_screenshot()

        # Assert
        assert result.startswith("data:image/jpeg;base64,")

@pytest.mark.asyncio
async def test_should_tapElement_when_validCoordinates(mock_adb_bridge):
    """Test: tap executes input tap command"""
    # Arrange
    mock_adb_bridge.execute_command.return_value = "success"

    with patch('server.mcp.mcp_tools.adb', mock_adb_bridge):
        # Act
        result = await neuron_tap(x=100, y=200)

        # Assert
        assert result == "success"
        mock_adb_bridge.execute_command.assert_called_with("input tap 100 200")
```

#### Unit Test: LLM Router

```python
# File: server/tests/test_brain_llm_router.py
import pytest
from server.brain.llm_router import LLMRouter, LLMTier
from server.brain.intent_classifier import IntentClassifier
from server.brain.sensitivity_gate import SensitivityGate

@pytest.fixture
def llm_router():
    return LLMRouter(
        intent_classifier=IntentClassifier(),
        sensitivity_gate=SensitivityGate(),
    )

def test_should_routeToT1_when_simpleScreenClassification(llm_router):
    """Simple screen classification → T1 (Gemma 3n on-device)"""
    intent = "classify_screen"
    current_screen = {"package": "com.whatsapp", "text": "Chat list"}
    tier = llm_router.route_intent(intent, current_screen)
    assert tier == LLMTier.T1

def test_should_routeToT2_when_multiStepTask(llm_router):
    """Multi-step execution → T2 (Gemini Flash)"""
    intent = "send_message_to_mom"
    current_screen = {"package": "com.whatsapp"}
    tier = llm_router.route_intent(intent, current_screen)
    assert tier == LLMTier.T2

def test_should_routeToT4_when_passwordFieldDetected(llm_router):
    """Password field detected → T4 (on-device only)"""
    intent = "login"
    current_screen = {
        "package": "com.facebook.katana",
        "nodes": [{"text": "Password", "isPassword": True}]
    }
    tier = llm_router.route_intent(intent, current_screen)
    assert tier == LLMTier.T4

def test_should_routeToT4_when_bankingAppDetected(llm_router):
    """Banking app detected → T4 (on-device only)"""
    banking_packages = [
        "com.google.android.apps.walletnfcrel",
        "net.one97.paytm",
        "com.phonepe.app",
    ]
    for package in banking_packages:
        current_screen = {"package": package}
        tier = llm_router.route_intent("check_balance", current_screen)
        assert tier == LLMTier.T4, f"Should be T4 for {package}"
```

---

## Integration Test Checklist

The **10-Task Integration Benchmark** from SPRINT.md:

```
✓ BENCHMARK TASKS (Week 2, Day 10):
  1. WhatsApp: "Send Mom a message saying I'll be late"
  2. Chrome: "Search for best restaurants near me"
  3. Settings: "Turn on Airplane mode"
  4. Contacts: "Call John Smith"
  5. Gmail: "Check my unread emails"
  6. YouTube: "Play lofi music"
  7. Maps: "Navigate home"
  8. Calendar: "Add meeting tomorrow at 3pm"
  9. Camera: "Take a photo"
  10. Play Store: "Search for Spotify"
```

Target success rate: >70%

---

## Quality Gates

### Before Every Commit

1. **All public tests pass:** `./gradlew test && pytest server/tests/`
2. **No lint warnings:** `./gradlew ktlint && ruff check server/`
3. **Coverage doesn't decrease:** Compare to baseline
4. **Sensitivity gate tested:** Every new screen type must have a sensitivity test

### TDD Must-Test Components (8 Core)

1. **UITreeReader** — pruning logic, JSON serialization
2. **ActionExecutor** — tap, type, swipe, verification
3. **SensitivityGate** — password field, banking app detection
4. **LLMRouter** — T0-T4 routing decisions
5. **IntentClassifier** — task complexity classification
6. **PlanAndExecuteEngine** — ReAct loop state machine
7. **WorkingMemory** — state persistence, serialization
8. **server/brain/planner.py** — action plan generation

---

## Test Location Convention

| Component | Location |
|-----------|----------|
| Unit Tests (Kotlin) | `android/app/src/test/kotlin/ai/neuron/{Module}/{Class}Test.kt` |
| Integration Tests (Android) | `android/app/src/androidTest/kotlin/ai/neuron/{Feature}IntegrationTest.kt` |
| Unit Tests (Python) | `server/tests/test_{module}.py` |
| Integration Tests (Python) | `server/tests/integration/test_{feature}.py` |

---

## Running Tests Locally

```bash
# Android unit tests
cd android && ./gradlew test

# Android instrumentation tests (requires emulator)
./gradlew connectedAndroidTest

# Python tests
cd server && pytest tests/ -v

# Python async tests with coverage
pytest tests/ -v --asyncio-mode=auto --cov=server --cov-report=html

# Run specific test class
pytest server/tests/test_brain_llm_router.py -v

# Run with profiling (detect flaky tests)
pytest tests/ --count=10 -x  # Run 10x, stop on first failure
```

---

## Coverage Requirements

- **Public API** ≥ 90%
- **Core logic** (`accessibility/*`, `brain/*`, `memory/*`) ≥ 85%
- **UI components** ≥ 60%
- **Utility code** ≥ 70%

---

**Last Updated:** 2026-03-08 | **Version:** 0.1.0-alpha
