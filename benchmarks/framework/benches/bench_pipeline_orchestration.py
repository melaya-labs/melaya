"""bench_pipeline_orchestration.py — step-to-step transition cost.

What this measures
------------------

Two pipeline topologies:

    1. LINEAR  — 10 sequential awaits, each step is a zero-work mock.
                 The per-step transition cost reported is the floor of
                 what the runner can achieve between a tool completing
                 and the next being invoked.
    2. PARALLEL — 10 mock steps fanned out via `asyncio.gather` and
                 joined at the end. At this N the gather's setup +
                 scheduling cost dominates, so per-step runs HIGHER
                 than the linear chain; the amortisation only wins once
                 the steps block on real I/O (not modelled here). The
                 bench publishes both numbers so the curve is visible.

Per-step latency = `(total run time) / n_steps`. We sample 2,000 full
pipeline runs to get tight percentiles on each per-step value.

Reproduce
---------

    pytest benches/bench_pipeline_orchestration.py -s

Cap: 30 s wall time. Default config (2 topologies × 2k runs × 10
steps = 40k step transitions total) finishes in <10 s on modern HW.
"""

from __future__ import annotations

import time
from typing import Any

import pytest

from melaya_bench_framework import LinearPipeline, ParallelPipeline


PIPELINE_LEN = 10
RUNS = 2_000


@pytest.mark.asyncio
async def test_pipeline_linear(bench_writer: Any) -> None:
    """10-step linear pipeline, run RUNS times. Report per-step
    transition latency in microseconds."""
    pipeline = LinearPipeline.of_size(PIPELINE_LEN)

    # Warm-up
    for _ in range(50):
        await pipeline.run()

    samples_us: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(RUNS):
        t0 = perf()
        await pipeline.run()
        # Per-step transition = total / n_steps (gives the average
        # cost of stepping forward by one in the graph).
        per_step_us = (perf() - t0) / 1000.0 / PIPELINE_LEN
        samples_us.append(per_step_us)

    bench_writer(
        metric="pipeline_orchestration_linear",
        samples_us=samples_us,
        shim_call="LinearPipeline.run (10 steps, per-step transition)",
        what_this_is=(
            "Per-step transition cost in a linear pipeline: time "
            "from step N's await completing to step N+1's invocation. "
            "Pure runner overhead — every step is a zero-work mock."
        ),
        what_this_is_not=(
            "Tool latency. Model-call latency. Network. The full "
            "pipeline cost in production is dominated by the tools' "
            "own work (which lives on the per-tool benches), not by "
            "this transition cost."
        ),
        extra={"n_steps": PIPELINE_LEN, "n_runs": RUNS, "topology": "linear"},
    )


@pytest.mark.asyncio
async def test_pipeline_parallel(bench_writer: Any) -> None:
    """10-step parallel pipeline, run RUNS times. Report per-step
    transition latency in microseconds."""
    pipeline = ParallelPipeline.of_size(PIPELINE_LEN)

    # Warm-up
    for _ in range(50):
        await pipeline.run()

    samples_us: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(RUNS):
        t0 = perf()
        await pipeline.run()
        per_step_us = (perf() - t0) / 1000.0 / PIPELINE_LEN
        samples_us.append(per_step_us)

    bench_writer(
        metric="pipeline_orchestration_parallel",
        samples_us=samples_us,
        shim_call="ParallelPipeline.run (10 steps, per-step transition)",
        what_this_is=(
            "Per-step transition cost in a parallel pipeline: total "
            "asyncio.gather time divided by N. At this N the gather's "
            "setup + scheduling overhead dominates, so it runs HIGHER "
            "than the linear chain — the amortisation only pays off "
            "once the steps block on real I/O (not modelled here)."
        ),
        what_this_is_not=(
            "Real concurrency. Each mock step returns in ~50 ns of "
            "Python work, which the event loop runs serially. The "
            "scheduling-amortisation effect we capture here is real, "
            "but a real concurrency speedup requires the steps to "
            "block on I/O (which lives in the per-tool benches)."
        ),
        extra={"n_steps": PIPELINE_LEN, "n_runs": RUNS, "topology": "parallel"},
    )
