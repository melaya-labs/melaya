"""The measurement subject.

Run as a FRESH subprocess per (scenario, run) by the harness - that
fresh process IS the isolation. It self-measures (its own footprint +
any real children + named-sibling model server) through the phases:

    warm -> idle -> [N cycles, gc+trim+settle each] -> teardown settle

and writes a single-run result JSON. The harness aggregates N runs.

Usage:  python -m melaya_bench_framework.memory._child --scenario s1 \
            --cycles 20 --out run.json [--sample-ms 30]
"""

from __future__ import annotations

import argparse
import asyncio
import ctypes
import gc
import json
import sys
import time
import tracemalloc

from ._sampler import OS_KEY, MEM_METRIC, SelfSampler
from . import _scenarios


def _malloc_trim() -> bool:
    if OS_KEY == "linux":
        try:
            ctypes.CDLL("libc.so.6").malloc_trim(0)
            return True
        except Exception:  # noqa: BLE001
            return False
    return False


def _settle(sampler: SelfSampler) -> float:
    gc.collect()
    gc.collect()
    _malloc_trim()
    return sampler.snapshot_mb()


def _ols(xs: list[float], ys: list[float]) -> tuple[float, float]:
    """Pure-python OLS slope + 95% CI half-width (no numpy in the subject's
    own arithmetic so we don't measure regression scratch). Returns
    (slope, ci95_halfwidth). CI includes 0 => 'flat'."""
    n = len(xs)
    if n < 3:
        return 0.0, 0.0
    mx = sum(xs) / n
    my = sum(ys) / n
    sxx = sum((x - mx) ** 2 for x in xs)
    if sxx == 0:
        return 0.0, 0.0
    sxy = sum((x - mx) * (y - my) for x, y in zip(xs, ys))
    slope = sxy / sxx
    resid = [y - (my + slope * (x - mx)) for x, y in zip(xs, ys)]
    sse = sum(r * r for r in resid)
    if n > 2:
        se_slope = (sse / (n - 2) / sxx) ** 0.5
    else:
        se_slope = 0.0
    return slope, 1.96 * se_slope


def _pctile(vals: list[float], p: float) -> float:
    if not vals:
        return 0.0
    s = sorted(vals)
    idx = min(len(s) - 1, max(0, round((len(s) - 1) * p)))
    return s[idx]


async def _run(scn: _scenarios.Scenario, cycles: int, sample_ms: int) -> dict:
    tracemalloc.start()
    sampler = SelfSampler(interval_ms=sample_ms)

    # warm (the idle-floor basis)
    scn.warm()
    sampler.start()
    time.sleep(0.15)  # let the sampler establish a floor reading
    idle_mb = _settle(sampler)

    # cycles
    state: dict = {}
    t_steady_start = time.time()
    per_cycle_settle: list[float] = []
    python_heap_mb: list[float] = []
    for _ in range(cycles):
        await scn.cycle(state)
        per_cycle_settle.append(_settle(sampler))
        python_heap_mb.append(tracemalloc.get_traced_memory()[0] / (1024 * 1024))
    t_steady_end = time.time()

    peak_hwm = sampler.peak_hwm_mb()
    settle_mb = _settle(sampler)
    orphan = sampler.orphan_child_count()
    sampler.stop()
    tracemalloc.stop()

    # window the external samples by phase timestamps
    steady = [m for (t, m, _r, _n) in sampler.samples if t_steady_start <= t <= t_steady_end]
    steady_rss = [r for (t, _m, r, _n) in sampler.samples if t_steady_start <= t <= t_steady_end]
    pre_rss = [r for (t, _m, r, _n) in sampler.samples if t < t_steady_start]
    all_metric = [m for (_t, m, _r, _n) in sampler.samples]
    all_rss = [r for (_t, _m, r, _n) in sampler.samples]

    cyc_x = list(range(len(per_cycle_settle)))
    leak_slope, leak_ci = _ols([float(i) for i in cyc_x], per_cycle_settle)
    py_slope, _ = _ols([float(i) for i in cyc_x], python_heap_mb)
    rss_slope, _ = _ols([float(i) for i in cyc_x],
                        steady_rss[:len(cyc_x)] if len(steady_rss) >= len(cyc_x) else per_cycle_settle)

    peak_sample = max(all_metric, default=settle_mb)
    return {
        "scenario": scn.id,
        "capability": scn.capability,
        "os": OS_KEY,
        "mem_metric": MEM_METRIC,
        "idle_mb": round(idle_mb, 2),
        "steady_p50_mb": round(_pctile(steady, 0.50), 2),
        "steady_p95_mb": round(_pctile(steady, 0.95), 2),
        "peak_sample_mb": round(peak_sample, 2),
        "peak_hwm_mb": round(peak_hwm, 2),
        "peak_mb": round(max(peak_sample, peak_hwm), 2),
        "settle_mb": round(settle_mb, 2),
        # working-set (RSS) captured alongside the OS metric. On Windows the
        # OS metric is private_bytes (commit charge) which can run far above
        # the resident working set for native libs (numpy/scipy/torch reserve
        # large private VA); working_set is the honest RAM-resident number.
        "idle_ws_mb": round(_pctile(pre_rss, 0.50), 2) if pre_rss else round(steady_rss[0] if steady_rss else 0.0, 2),
        "steady_ws_p50_mb": round(_pctile(steady_rss, 0.50), 2),
        "peak_ws_mb": round(max(all_rss, default=0.0), 2),
        "leak_slope_mb_per_cycle": round(leak_slope, 4),
        "leak_ci95": round(leak_ci, 4),
        "python_heap_slope_mb_per_cycle": round(py_slope, 4),
        "rss_settle_slope_mb_per_cycle": round(rss_slope, 4),
        "n_cycles": cycles,
        "orphan_child_count": orphan,
        "n_samples": len(sampler.samples),
    }


def main() -> None:
    ap = argparse.ArgumentParser()
    ap.add_argument("--scenario", required=True)
    ap.add_argument("--cycles", type=int, default=20)
    ap.add_argument("--sample-ms", type=int, default=30)
    ap.add_argument("--out", required=True)
    args = ap.parse_args()
    scn = _scenarios.get(args.scenario)
    result = asyncio.run(_run(scn, args.cycles, args.sample_ms))
    with open(args.out, "w", encoding="utf-8") as fh:
        json.dump(result, fh)
    # one-line human echo
    print(
        f"[mem-child] {scn.id} idle={result['idle_mb']} "
        f"steady_p50={result['steady_p50_mb']} peak={result['peak_mb']} "
        f"settle={result['settle_mb']} leak={result['leak_slope_mb_per_cycle']}"
        f"+/-{result['leak_ci95']} {result['mem_metric']}MB",
        file=sys.stderr,
    )


if __name__ == "__main__":
    main()
