"""bench_tool_dispatch.py — measures `Toolkit.dispatch` cost.

What this measures
------------------

Pure RUNNER overhead per tool call: dict lookup of tool by name,
merge preset_kwargs + tool_call.input, await the tool func, wrap into
a ToolResponse. The tool itself is `async_tool`, which returns
immediately — so any latency we see is the dispatch wrapper, not the
tool's work.

Three input shapes are measured because real production calls span
the spectrum:
    • 0-arg  — e.g. `git_status()`, `melaya_list_accounts()`
    • 5-arg  — the median operator-registered tool
    • 20-arg — long-tail wide-arg tools like `melaya_create_order(...)`
              with all the optional risk params filled in

Reproduce
---------

    pytest benches/bench_tool_dispatch.py -s

Cap: 30 s wall time. Default config (3 shapes × 10k iterations)
finishes in 1-3 s on modern HW.
"""

from __future__ import annotations

import asyncio
import time
from typing import Any

import pytest

from melaya_bench_framework import Toolkit, ToolUseBlock, async_tool


ITERATIONS = 10_000


def _build_tool_call(name: str, n_args: int) -> ToolUseBlock:
    # Realistic mixed-type args (real tool calls aren't all-int): the
    # kwargs-merge cost depends on value types, so cycle int / str /
    # float / bool / nested-dict the way a production order/payload does.
    _shapes = (lambda i: i, lambda i: f"val_{i}", lambda i: i * 1.5,
               lambda i: bool(i % 2), lambda i: {"k": i, "s": f"x{i}"})
    return {
        "type": "tool_use",
        "id": "bench-1",
        "name": name,
        "input": {f"arg_{i}": _shapes[i % len(_shapes)](i) for i in range(n_args)},
    }


async def _measure(shape: str, n_args: int) -> list[float]:
    """Time `Toolkit.dispatch` over ITERATIONS calls. Returns per-call
    latency in microseconds."""
    toolkit = Toolkit()
    name = f"bench_tool_{shape}"
    toolkit.register_tool_function(async_tool, func_name=name)
    tool_call = _build_tool_call(name, n_args)

    # Warm-up: branch predictor + event-loop caches.
    for _ in range(200):
        await toolkit.dispatch(tool_call)

    samples_us: list[float] = []
    perf = time.perf_counter_ns
    # NOTE: `Instant::now()` / `time.perf_counter_ns` resolution on
    # Linux x86 is ~25 ns via clock_gettime(CLOCK_MONOTONIC) vDSO; on
    # Windows it's ~25-100 ns via QueryPerformanceCounter. For
    # sub-microsecond dispatch the clock noise is meaningful — that's
    # why we sample 10k iterations and read percentiles. Same caveat
    # as the engine bench's state_ticker timing.
    for _ in range(ITERATIONS):
        t0 = perf()
        await toolkit.dispatch(tool_call)
        dt = perf() - t0
        samples_us.append(dt / 1000.0)
    return samples_us


@pytest.mark.asyncio
async def test_tool_dispatch_0arg(bench_writer: Any) -> None:
    """Dispatch a zero-arg tool 10k times."""
    samples = await _measure(shape="0arg", n_args=0)
    bench_writer(
        metric="tool_dispatch_0arg",
        samples_us=samples,
        shim_call="Toolkit.dispatch (0-arg)",
        what_this_is=(
            "Per-call cost of one Toolkit.dispatch on a zero-arg async "
            "tool whose body returns immediately. Measures pure runner "
            "overhead: name lookup, kwargs merge, await, ToolResponse "
            "wrap. NO tool work, NO middleware, NO network."
        ),
        what_this_is_not=(
            "End-to-end tool latency (which includes the tool's own "
            "HTTP / FS / DB work). Middleware-augmented dispatch (HITL "
            "gate, audit log, postprocess add their own deltas, "
            "measured separately in production telemetry)."
        ),
        reference_hardware=(
            "Production reference: see results/contributed/ for "
            "tier submissions. First contributor PR becomes the "
            "citable reference for each tier."
        ),
        extra={"shape": "0arg", "n_args": 0, "iterations": ITERATIONS},
    )


@pytest.mark.asyncio
async def test_tool_dispatch_5arg(bench_writer: Any) -> None:
    """Dispatch a 5-arg tool 10k times (median operator-registered shape)."""
    samples = await _measure(shape="5arg", n_args=5)
    bench_writer(
        metric="tool_dispatch_5arg",
        samples_us=samples,
        shim_call="Toolkit.dispatch (5-arg)",
        what_this_is=(
            "Same as tool_dispatch_0arg but with a 5-element input "
            "dict (the median shape for operator-registered tools in "
            "the production runner)."
        ),
        what_this_is_not=(
            "Schema validation cost — the runtime validates input "
            "against the JSON schema in the middleware chain (not "
            "modelled here). That delta is separately measured."
        ),
        extra={"shape": "5arg", "n_args": 5, "iterations": ITERATIONS},
    )


@pytest.mark.asyncio
async def test_tool_dispatch_20arg(bench_writer: Any) -> None:
    """Dispatch a 20-arg tool 10k times (long-tail wide-arg tools)."""
    samples = await _measure(shape="20arg", n_args=20)
    bench_writer(
        metric="tool_dispatch_20arg",
        samples_us=samples,
        shim_call="Toolkit.dispatch (20-arg)",
        what_this_is=(
            "Wide-arg dispatch — 20-element input dict, modelling "
            "long-tail tools like `melaya_create_order` with every "
            "optional risk param filled in."
        ),
        what_this_is_not=(
            "Network or DB latency — the tool returns in <100 ns."
        ),
        extra={"shape": "20arg", "n_args": 20, "iterations": ITERATIONS},
    )
