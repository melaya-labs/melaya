"""bench_model_wrapper_overhead.py — `ModelWrapper.call` cost
isolated from provider network.

What this measures
------------------

One full LLM turn through `ModelWrapper.call`, with the provider HTTP
boundary replaced by a `MockProvider` that returns in <100 ns. The
RUNNER overhead is:

    1. Pack message history into provider format     (`_pack`)
    2. Inject tools spec                              (in `_pack`)
    3. Await provider (mock: instant)                 (~80 ns)
    4. Unpack response + record cost                  (`_unpack`)

Mirrors `the production model wrapper` for the
steady-state case. The number this bench reports is the FLOOR — the
runner adds this overhead on top of every provider HTTP RTT a real
model call sees.

Reproduce
---------

    pytest benches/bench_model_wrapper_overhead.py -s

Cap: 30 s wall time. Default config (1k iterations)
finishes in <1 s on modern HW.
"""

from __future__ import annotations

import time
from typing import Any

import pytest

from melaya_bench_framework import ModelWrapper, MockProvider, build_message_history


ITERATIONS = 1_000


@pytest.mark.asyncio
async def test_model_wrapper_overhead(bench_writer: Any) -> None:
    """1k iterations of `ModelWrapper.call` with a 6-turn history +
    a small tools spec. Mirrors the median agentic-turn shape."""
    provider = MockProvider(canned_text="ok")
    wrapper = ModelWrapper(provider, model_name="mock-model-v1")

    # Realistic median: 6-turn history × 400-char average message.
    history = build_message_history(n_turns=6, avg_msg_chars=400)
    # Small tools spec (3 tools, typical research-crew turn).
    tools = [
        {"type": "function",
         "function": {
             "name": f"t_{i}",
             "description": "Test tool",
             "parameters": {},
         }}
        for i in range(3)
    ]

    # Warm-up
    for _ in range(100):
        await wrapper.call(history, tools)

    samples_us: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(ITERATIONS):
        t0 = perf()
        await wrapper.call(history, tools)
        samples_us.append((perf() - t0) / 1000.0)

    bench_writer(
        metric="model_wrapper_overhead",
        samples_us=samples_us,
        shim_call="ModelWrapper.call (6-turn history, 3-tool spec)",
        what_this_is=(
            "Pure runner overhead around one LLM turn: prompt "
            "assembly + message-history pack + tools-spec inject + "
            "(mocked) provider await + response unpack + cost track. "
            "Provider HTTP is mocked to 0 ms so the number isolates "
            "RUNNER overhead from network."
        ),
        what_this_is_not=(
            "End-to-end LLM turn latency. The real provider HTTP "
            "RTT is 200 ms - 5 s depending on the model + region. "
            "Add this number on TOP of the provider's network "
            "latency for the user-visible total."
        ),
        extra={
            "history_turns": 6,
            "avg_msg_chars": 400,
            "tools_in_spec": 3,
            "iterations": ITERATIONS,
        },
    )
