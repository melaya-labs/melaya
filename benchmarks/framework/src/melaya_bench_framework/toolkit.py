"""Toolkit shim — reproduces `the runtime's toolkit call path`
hot path for the steady-state same-name case.

Mapping (runtime → this shim):

    Toolkit.call_tool_function(tool_call: ToolUseBlock)
      └─ name lookup in self.tools         → Toolkit.dispatch step 1
      └─ kwargs merge (preset + input)     → Toolkit.dispatch step 2
      └─ await tool_func(**kwargs)         → Toolkit.dispatch step 3
      └─ wrap into ToolResponse            → Toolkit.dispatch step 4

What we DON'T include:

    • Group activation check (`tool_func.group != "basic" and not active`)
      — adds ~30 ns per call, branch-predicted away once warm.
    • postprocess_func chain — measured separately in production.
    • Middleware chain (HITL gate, audit log, validation) — same.
    • Background-task path (`async_execution=True`) — niche; the
      production hot path is the foreground `await` we model here.
    • Streaming accumulation (`AsyncGenerator[ToolResponse, None]`) —
      the production calls overwhelmingly yield exactly one chunk
      and accumulate trivially. We measure single-yield steady state.

The bench is the **floor** of what the runner can do. Production adds
middleware overhead on top, measured independently. See README for the
production telemetry comparison procedure.
"""

from __future__ import annotations

import asyncio
import inspect
from dataclasses import dataclass, field
from typing import Any, Awaitable, Callable, TypedDict


class ToolResponse:
    """Minimal stand-in for the runtime's tool-response object.

    Real impl is a pydantic model with content blocks + metadata. The
    hot-path cost is allocating the object + storing the content list,
    not the validation pass (Pydantic V2 validates lazily on access).
    """

    __slots__ = ("content", "metadata")

    def __init__(
        self,
        content: list[dict[str, Any]] | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> None:
        self.content = content if content is not None else []
        self.metadata = metadata if metadata is not None else {}


class ToolUseBlock(TypedDict, total=False):
    """Mirrors the runtime's tool-use block exactly."""

    type: str   # "tool_use"
    id: str
    name: str
    input: dict[str, Any]


# Sentinel for "no preset kwargs" — avoids a per-call `or {}` allocation
# in the dispatch hot path.
_EMPTY_KWARGS: dict[str, Any] = {}


@dataclass
class _RegisteredTool:
    """Internal Toolkit slot. Same shape as the runtime's registered-tool slot
    for the fields we touch on the dispatch hot path. Other fields
    (group_name, json_schema, namesake_strategy) are tracked in the
    runtime but irrelevant to per-call dispatch cost.
    """

    func: Callable[..., Awaitable[Any]]
    preset_kwargs: dict[str, Any] = field(default_factory=dict)
    is_async: bool = True


class Toolkit:
    """Tool registry + dispatcher. Mirrors the runtime's `Toolkit`
    interface for the two methods the bench cares about:
    `register_tool_function` and `dispatch` (≡ `call_tool_function`).

    Usage:
        toolkit = Toolkit()
        toolkit.register_tool_function(my_tool)
        resp = await toolkit.dispatch({"type": "tool_use",
                                       "id": "1",
                                       "name": "my_tool",
                                       "input": {"x": 1}})
    """

    __slots__ = ("tools",)

    def __init__(self) -> None:
        self.tools: dict[str, _RegisteredTool] = {}

    # ── Registration ────────────────────────────────────────────────────

    def register_tool_function(
        self,
        tool_func: Callable[..., Any],
        func_name: str | None = None,
        preset_kwargs: dict[str, Any] | None = None,
    ) -> None:
        """Register a tool. Mirrors `Toolkit.register_tool_function`'s
        hot-path behaviour: the func is stored under its name (or the
        override), preset kwargs are merged in on every call.

        The real impl also extracts the JSON schema from the function
        signature + docstring. That cost is incurred at REGISTRATION
        time, not dispatch time, so we don't model it here — it's
        captured in the `registry_boot` bench instead.
        """
        name = func_name or tool_func.__name__
        is_async = inspect.iscoroutinefunction(tool_func)
        self.tools[name] = _RegisteredTool(
            func=tool_func,
            preset_kwargs=preset_kwargs or {},
            is_async=is_async,
        )

    # ── Dispatch (the hot path the bench measures) ──────────────────────

    async def dispatch(self, tool_call: ToolUseBlock) -> ToolResponse:
        """Steady-state dispatch.

        Mirrors the runtime's toolkit hot path:
            1. Name lookup
            2. kwargs merge
            3. Await the tool func
            4. Wrap into a ToolResponse if the func didn't already

        Returns the single ToolResponse (real impl is a 1-chunk
        AsyncGenerator; accumulation cost for the steady-state
        single-yield case is dominated by the await + wrap).
        """
        name = tool_call["name"]
        tool = self.tools.get(name)
        if tool is None:
            return ToolResponse(
                content=[{"type": "text",
                          "text": f"FunctionNotFoundError: {name}"}],
            )

        # Same merge order as the runtime (preset_kwargs first, then
        # tool_call.input overrides) — preset_kwargs is a dict, input
        # may be omitted by the model, so the `or {}` is the same.
        preset = tool.preset_kwargs
        input_kwargs = tool_call.get("input") or _EMPTY_KWARGS
        if preset:
            kwargs = {**preset, **input_kwargs}
        else:
            # Hot fast-path: no preset kwargs (the overwhelmingly common
            # case for the operator-registered tools that show up in
            # the dispatch p50). Avoids the dict-merge alloc.
            kwargs = input_kwargs

        if tool.is_async:
            result = await tool.func(**kwargs)
        else:
            # Sync tools: the runtime offloads to a default executor.
            # That cost is dominated by the executor's context switch
            # (~10-20 µs) and is the reason the production runtime
            # discourages sync tool registration; we model the sync
            # path by running the func inline so the bench measures
            # the dispatch wrapper alone, not the executor.
            result = tool.func(**kwargs)

        if isinstance(result, ToolResponse):
            return result
        # Real runtime auto-wraps non-ToolResponse returns in a text
        # block — matches the runtime's tool-response helper.
        return ToolResponse(
            content=[{"type": "text", "text": str(result)}],
        )


# ── Pre-built mock tools for the dispatch bench ────────────────────────


async def async_tool(**kwargs: Any) -> ToolResponse:
    """Zero-cost mock tool: returns immediately with a fixed response.

    Used by the dispatch bench so we measure ONLY runner overhead.
    Any work added here would muddle the "pure dispatch" signal.
    """
    return ToolResponse(content=[{"type": "text", "text": "ok"}])


def sync_tool(**kwargs: Any) -> ToolResponse:
    """Sync variant of `async_tool` — same zero-cost return, no
    executor offload modelled. Used by the dispatch bench to isolate
    the sync-vs-async dispatch delta."""
    return ToolResponse(content=[{"type": "text", "text": "ok"}])
