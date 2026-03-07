# Python Style Guide - Neuron

## Formatting

- **Formatter/Linter**: Ruff (replaces Black, isort, flake8)
- **Type Checker**: mypy (strict mode)
- **Max line length**: 100 characters
- **Indentation**: 4 spaces
- **Quotes**: Double quotes for strings

## Naming Conventions

| Element | Convention | Example |
|---------|-----------|---------|
| Modules | snake_case | `llm_router.py`, `memory_service.py` |
| Classes | PascalCase | `LLMRouter`, `SensitivityGate` |
| Functions | snake_case | `route_intent()`, `call_claude()` |
| Variables | snake_case | `current_tier`, `is_sensitive` |
| Constants | SCREAMING_SNAKE | `MAX_TOKENS`, `DEFAULT_TIMEOUT` |
| Type aliases | PascalCase | `ActionResult`, `UITree` |
| Test functions | test_should_X_when_Y | `test_should_routeToT4_when_passwordFieldDetected` |

## Type Hints

Required on all public functions. Use modern syntax (Python 3.12+):

```python
# Good
async def route_intent(intent: str, screen: dict[str, Any]) -> LLMTier:
    ...

def parse_action(raw: str) -> NeuronAction:
    ...

# Use Optional only when None is a valid return
async def call_llm(prompt: str, timeout: float = 10.0) -> str | None:
    ...
```

## Async Patterns

All I/O operations must be async:

```python
# Good — async with timeout
async def call_claude(prompt: str, timeout: float = 10.0) -> str:
    async with asyncio.timeout(timeout):
        response = await client.messages.create(...)
        return response.content[0].text

# Bad — sync I/O in async context
def call_claude(prompt: str) -> str:
    response = client.messages.create(...)  # Blocks event loop!
```

## Pydantic Models

Use for all data contracts and validation:

```python
from pydantic import BaseModel, Field
from typing import Literal

class NeuronAction(BaseModel):
    action_type: Literal["tap", "type", "swipe", "launch", "navigate", "wait", "done", "error", "confirm"]
    target_id: str | None = None
    target_text: str | None = None
    value: str | None = None
    confidence: float = Field(ge=0.0, le=1.0)
    reasoning: str
    requires_confirmation: bool = False
```

## Error Handling

- Define custom exceptions per domain
- Never catch bare `Exception` — catch specific types
- Always log errors with structlog

```python
class LLMTimeoutError(Exception):
    pass

class LLMParseError(Exception):
    pass

async def call_llm(prompt: str) -> str:
    try:
        return await asyncio.wait_for(client.call(prompt), timeout=10.0)
    except asyncio.TimeoutError:
        raise LLMTimeoutError(f"LLM call timed out after 10s")
    except ValidationError as e:
        raise LLMParseError(f"Invalid response: {e}")
```

## Logging

Use structlog for structured, JSON-compatible logging:

```python
import structlog

logger = structlog.get_logger()

logger.info("llm_call", model="claude-sonnet-4-5", tokens=150, latency_ms=1200)
logger.error("llm_timeout", model="gemini-flash", timeout_s=10.0)
```

## FastAPI Patterns

```python
from fastapi import FastAPI, HTTPException

app = FastAPI(title="Neuron Brain API")

@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}

@app.post("/plan")
async def create_plan(request: PlanRequest) -> PlanResponse:
    ...
```

## MCP Tool Pattern

```python
@server.tool()
async def neuron_take_screenshot() -> list[TextContent]:
    """Take a screenshot of the current Android screen."""
    screenshot_b64 = await android_bridge.take_screenshot()
    return [TextContent(type="text", text=screenshot_b64)]
```

## File Organization

```
server/
├── mcp/
│   ├── neuron_mcp_server.py   # Server definition
│   └── mcp_tools.py           # Tool implementations
├── brain/
│   ├── brain_api.py           # FastAPI routes
│   ├── planner.py             # Plan-and-Execute engine
│   └── models.py              # LLM client wrappers
├── memory/
│   ├── memory_service.py      # Memory API
│   └── vector_store.py        # sqlite-vec operations
└── tests/
    ├── test_mcp_tools.py
    ├── test_brain_llm_router.py
    └── conftest.py            # Shared fixtures
```

## Anti-Patterns (Never Do)

- Sync I/O in async functions (blocks the event loop)
- `time.sleep()` — use `await asyncio.sleep()`
- Trusting raw LLM JSON without Pydantic validation
- LLM calls without timeouts
- Hardcoded API keys — always use environment variables
- `import *` — always explicit imports
- Mutable default arguments (`def f(x=[])`)
