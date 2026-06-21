"""bench_cost_tracking.py - per-call cost/token accounting overhead.

Records token usage against a price table and aggregates running USD spend
+ per-model breakdown - the accounting that lets you bill/cap per tenant.
Prices are illustrative. Reproduce: pytest benches/bench_cost_tracking.py -s
"""
from __future__ import annotations
import time
from typing import Any
from melaya_bench_framework import CostTracker

ITERATIONS = 10_000


def test_cost_tracking(bench_writer: Any) -> None:
    tracker = CostTracker()
    models = ["model_a", "model_b", "model_c"]
    for i in range(200):
        tracker.record(models[i % 3], 1200, 400)
    s: list[float] = []
    perf = time.perf_counter_ns
    for i in range(ITERATIONS):
        m = models[i % 3]
        t0 = perf(); tracker.record(m, 1200, 400); s.append((perf() - t0) / 1000.0)
    bench_writer(
        metric="cost_tracking", samples_us=s,
        shim_call="CostTracker.record (price-table lookup + USD aggregate + per-model breakdown)",
        what_this_is=("Per-call cost of recording one model invocation's token usage against a "
                      "price table and updating the running USD total + per-model breakdown. "
                      "The accounting that enables per-tenant billing and spend caps."),
        what_this_is_not=("Real provider prices (illustrative here) or the summary() aggregation "
                          "(O(models), called once per run, not per call)."),
        extra={"iterations": ITERATIONS, "models": 3},
    )
