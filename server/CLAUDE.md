# Neuron Server Module — Claude Code Context

> Read the root CLAUDE.md at `/home/disk/Personal/projects/neuron/neuron/CLAUDE.md` before working in this module.
> That file is the single source of truth for architecture, LLM routing rules, hard rules, and tech stack versions.

---

## Commands

```bash
# Install all dependencies
pip install -r requirements.txt

# Run the MCP server (WebSocket on ws://localhost:7384/neuron-mcp)
python -m mcp.neuron_mcp_server

# Run the Brain REST API (hot reload enabled for development)
uvicorn brain.brain_api:app --reload --port 8384

# Run all tests
pytest

# Run tests with verbose output
pytest tests/ -v

# Run a specific test or test pattern
pytest tests/test_planner.py -k "test_action_validation"

# Run with coverage report
pytest --cov=. --cov-report=html

# Lint (check only)
ruff check .

# Lint and auto-fix + format
ruff format .

# Static type checking
mypy .
```

---

## Module Structure

```
server/
├── mcp/
│   ├── neuron_mcp_server.py    ← MCP server (WebSocket, JSON-RPC 2.0)
│   └── mcp_tools.py            ← Tool definitions and handlers
│
├── brain/
│   ├── brain_api.py            ← FastAPI REST API for LLM routing
│   ├── planner.py              ← Plan-and-Execute engine
│   └── models.py               ← LLM client wrappers (Claude, Gemini, Gemma)
│
├── memory/
│   ├── memory_service.py       ← Memory query and write API
│   └── vector_store.py         ← sqlite-vec vector DB operations
│
├── tests/
│   ├── test_mcp_server.py
│   ├── test_planner.py
│   ├── test_models.py
│   └── conftest.py             ← Shared fixtures
│
└── requirements.txt
```

---

## Python Coding Rules

### Async-First

All functions that do any I/O — network, filesystem, DB, LLM calls — must be `async def`. Synchronous blocking calls are not permitted inside the async event loop.

```python
# Correct
async def route_to_llm(prompt: str, tier: LLMTier) -> str:
    async with httpx.AsyncClient(timeout=30.0) as client:
        response = await client.post(...)
    return response.text

# Never
def route_to_llm(prompt: str, tier: LLMTier) -> str:
    return requests.post(...).text  # blocks the event loop
```

### Pydantic Models

All request bodies, response bodies, and LLM output schemas are defined as Pydantic models. No raw `dict` passing across module boundaries.

```python
from pydantic import BaseModel

class RouteRequest(BaseModel):
    prompt: str
    tier: int
    context_window: list[str] = []

class RouteResponse(BaseModel):
    action: NeuronAction
    tier_used: int
    latency_ms: float
    model_id: str
```

### Timeouts on Every LLM Call

Every LLM call and outbound HTTP request must have an explicit timeout. Default is 30 seconds. Override only when there is a documented reason.

```python
# Correct
async with asyncio.timeout(30):
    result = await llm_client.generate(prompt)

# Also correct (httpx)
async with httpx.AsyncClient(timeout=30.0) as client:
    resp = await client.post(url, json=payload)

# Never
result = await llm_client.generate(prompt)  # no timeout, can hang indefinitely
```

### Logging with structlog

Use `structlog` for all logging. Never use `print()` or `logging.info()` directly. Structured logs make filtering in production straightforward.

```python
import structlog

log = structlog.get_logger(__name__)

async def handle_tool_call(name: str, args: dict) -> ToolResult:
    log.info("tool_call_received", tool=name, arg_keys=list(args.keys()))
    result = await execute_tool(name, args)
    log.info("tool_call_complete", tool=name, success=result.is_success)
    return result
```

### Type Hints

All function signatures must have complete type annotations. The codebase must pass `mypy --strict`.

```python
# Correct
async def plan_task(goal: str, context: list[UINode]) -> ExecutionPlan:
    ...

# Never
async def plan_task(goal, context):  # untyped, will fail mypy --strict
    ...
```

### Ruff Formatting

Line length is 100 characters. All code is formatted with `ruff format .` before committing. CI will reject unformatted code.

### Never Trust Raw LLM Output

Always validate LLM-generated JSON against the expected Pydantic schema before returning it to any caller.

```python
async def parse_action_plan(raw: str) -> ExecutionPlan:
    try:
        data = json.loads(raw)
        return ExecutionPlan.model_validate(data)
    except (json.JSONDecodeError, ValidationError) as e:
        log.error("llm_parse_failed", raw_output=raw[:200], error=str(e))
        raise LLMParseError(f"LLM returned invalid action plan: {e}") from e
```

---

## Adding a New MCP Tool

Follow these steps in order. Do not skip the test step.

**Step 1 — Define the tool schema in `mcp/mcp_tools.py`:**

```python
Tool(
    name="neuron_scroll_to_element",
    description="Scroll the current view until the target element is visible.",
    inputSchema={
        "type": "object",
        "properties": {
            "element_text": {"type": "string", "description": "Visible text of the target element"},
            "direction": {"type": "string", "enum": ["up", "down"], "default": "down"},
        },
        "required": ["element_text"],
    },
)
```

**Step 2 — Register the handler in `mcp/neuron_mcp_server.py`:**

```python
@server.call_tool()
async def handle_call_tool(name: str, arguments: dict) -> list[TextContent]:
    match name:
        case "neuron_scroll_to_element":
            return await handle_scroll_to_element(arguments)
        # ... existing cases
        case _:
            raise ValueError(f"Unknown tool: {name}")
```

**Step 3 — Write the test in `tests/test_mcp_server.py`:**

```python
async def test_scroll_to_element_scrolls_down(mock_android_client):
    result = await handle_scroll_to_element({"element_text": "Send", "direction": "down"})
    assert result[0].text == "scrolled_down"
```

**Step 4 — Add the tool name to the MCP tools list in the root `CLAUDE.md`.**

---

## FastAPI Patterns

```python
# Always use dependency injection for shared resources
@app.post("/api/route")
async def route_request(
    request: RouteRequest,
    router: LLMRouter = Depends(get_router),
    memory: MemoryService = Depends(get_memory),
) -> RouteResponse:
    plan = await router.route(request.prompt, request.tier)
    return RouteResponse(action=plan.first_action, tier_used=plan.tier, latency_ms=plan.latency_ms)

# Always add exception handlers — never let raw exceptions reach the client
@app.exception_handler(LLMParseError)
async def llm_parse_error_handler(request: Request, exc: LLMParseError) -> JSONResponse:
    log.error("llm_parse_error", error=str(exc), path=request.url.path)
    return JSONResponse(status_code=422, content={"error": "LLM returned unparseable output"})

# Use lifespan for startup/shutdown (not deprecated on_event)
@asynccontextmanager
async def lifespan(app: FastAPI):
    await startup()
    yield
    await shutdown()

app = FastAPI(lifespan=lifespan)
```

---

## Docker Commands

```bash
# Start the full server stack (MCP + Brain API + memory services)
docker compose -f docker/docker-compose.yml up -d

# Follow combined logs
docker compose -f docker/docker-compose.yml logs -f

# Start in dev mode with hot reload and mounted source
docker compose -f docker/docker-compose.dev.yml up

# Rebuild images after dependency changes
docker compose -f docker/docker-compose.yml build --no-cache

# Tear down and remove volumes
docker compose -f docker/docker-compose.yml down -v
```

---

## Hard Rules (reminders for this module)

- Never return raw LLM output to the Android client without Pydantic validation.
- Never make an LLM call without a timeout. Hanging calls will block the async event loop.
- Never log sensitive user data (message content, credentials, personal information).
- Environment variables for all secrets — `ANTHROPIC_API_KEY`, `GEMINI_API_KEY` etc. See root `CLAUDE.md` for the full list.
- T4 routing decisions originate on-device. The server must never override a T4 classification sent by the Android client.

---

*Module: server | Project: Neuron 0.1.0-alpha | Last updated: March 2026*
