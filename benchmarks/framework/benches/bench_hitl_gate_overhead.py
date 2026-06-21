"""bench_hitl_gate_overhead.py — per-write HITL enforcement-path cost.

What this measures
------------------

The synchronous safety checks the runner runs on EVERY write tool before
it is queued for human approval: sidecar-state read, per-cycle write
floor, per-tenant daily quota incr, and running cost-cap check. This is
the "trading-grade discipline" machinery — the reason the platform can be
trusted to run governed agents for clients.

It is explicitly NOT the human approval round-trip (that's human-bound and
minutes-to-hours — see results/hitl_round_trip/methodology_only.json). The
runner enters and exits this gate in microseconds; the wait that follows
is wall-clock human attention, measured separately.

Reproduce
---------

    pytest benches/bench_hitl_gate_overhead.py -s

Cap: 30 s wall time. Default config (10k iterations) finishes in <1 s.
"""

from __future__ import annotations

import time
from typing import Any

from melaya_bench_framework import HitlGate


ITERATIONS = 10_000


def test_hitl_gate_overhead(bench_writer: Any) -> None:
    """10k iterations of the per-write enforcement path (all checks pass,
    so we measure the full chain, not an early-reject short-circuit)."""
    # Generous caps so every iteration runs all four checks to completion.
    gate = HitlGate(max_writes_per_cycle=10**12, daily_cap=10**15, cost_cap_usd=1e15)

    # Warm-up
    for _ in range(200):
        gate.check_write("tenant_a", est_cost_usd=0.001)

    samples_us: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(ITERATIONS):
        t0 = perf()
        gate.check_write("tenant_a", est_cost_usd=0.001)
        samples_us.append((perf() - t0) / 1000.0)

    bench_writer(
        metric="hitl_gate_overhead",
        samples_us=samples_us,
        shim_call="HitlGate.check_write (sidecar + write-floor + quota + cost-cap)",
        what_this_is=(
            "Per-write cost of the synchronous HITL enforcement path: "
            "sidecar-state ContextVar read, per-cycle write floor, "
            "per-tenant daily quota incr, and running cost-cap check. This "
            "is the safety machinery the runner runs before every write is "
            "queued for human approval — pure in-process control flow, no "
            "I/O."
        ),
        what_this_is_not=(
            "The human approval round-trip (minutes-to-hours, human-bound, "
            "measured separately as hitl_round_trip). Nor the production "
            "policy thresholds or the real per-tenant store (mocked to an "
            "in-process counter here so the bench needs no network)."
        ),
        extra={"iterations": ITERATIONS, "checks": 4},
    )
