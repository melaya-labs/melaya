"""bench_tracing_overhead.py - per-span observability tax.

The cost of emitting one OpenTelemetry-style span around an operation:
open, stamp attributes (gen_ai.*, tokens, cost, latency), close, export.
This is what 'observability on' adds per traced op. Reproduce: pytest benches/bench_tracing_overhead.py -s
"""
from __future__ import annotations
import time
from typing import Any
from melaya_bench_framework import Tracer

ITERATIONS = 10_000


def test_tracing_overhead(bench_writer: Any) -> None:
    tracer = Tracer()
    attrs = {"gen_ai.system": "provider_x", "gen_ai.request.model": "model_a",
             "gen_ai.usage.input_tokens": 1200, "gen_ai.usage.output_tokens": 400,
             "cost_usd": 0.0096, "latency_ms": 820}
    for _ in range(200):
        tracer.span("tool_call", attrs)
    s: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(ITERATIONS):
        t0 = perf(); tracer.span("tool_call", attrs); s.append((perf() - t0) / 1000.0)
    bench_writer(
        metric="tracing_overhead", samples_us=s,
        shim_call="Tracer.span (open + attrs + close + in-process export)",
        what_this_is=("Per-span observability tax: open an OpenTelemetry-style span, stamp the "
                      "gen_ai.* + cost + latency attributes, close it, hand to the exporter. "
                      "This is what enabling tracing adds per traced operation."),
        what_this_is_not=("Real OTLP network export (mocked to an in-process list) or the backend "
                          "span store. It is the runner-side emit cost only."),
        extra={"iterations": ITERATIONS, "attrs": 6},
    )
