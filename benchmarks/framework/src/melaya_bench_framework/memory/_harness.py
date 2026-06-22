"""Harness: spawn N fresh subjects per scenario, aggregate, write the
0.1.0-mem-shape summary.json + the rss time-series CSV.

Each run is a fresh `_child` subprocess - that is the isolation. Memory
is skewed/bimodal, so across-run dispersion is reported as median + IQR
(never mean/std).
"""

from __future__ import annotations

import json
import os
import platform
import subprocess
import sys
import tempfile
import time
from pathlib import Path

from ._env import env_block
from ._sampler import OS_KEY, MEM_METRIC
from . import _scenarios

SHAPE = "0.1.0-mem-shape"


def _results_root() -> Path:
    override = os.environ.get("MEL_MEM_RESULTS")
    if override:
        return Path(override)
    here = Path(__file__).resolve()
    # src/melaya_bench_framework/memory/_harness.py -> framework/results
    return here.parents[3] / "results"


def _median(xs: list[float]) -> float:
    s = sorted(xs)
    n = len(s)
    if n == 0:
        return 0.0
    return s[n // 2] if n % 2 else (s[n // 2 - 1] + s[n // 2]) / 2


def _pct(xs: list[float], p: float) -> float:
    if not xs:
        return 0.0
    s = sorted(xs)
    return s[min(len(s) - 1, max(0, round((len(s) - 1) * p)))]


def _iqr(xs: list[float]) -> float:
    return round(_pct(xs, 0.75) - _pct(xs, 0.25), 2)


def _slug(scn: _scenarios.Scenario, concurrency: int) -> str:
    placement = scn.placement if scn.placement not in ("none", "agnostic") else (
        "idle" if scn.id == "s0" else "agnostic")
    return f"mem_{placement}_{scn.capability}_c{concurrency}"


def _run_child(scn_id: str, cycles: int, sample_ms: int) -> dict:
    fd, path = tempfile.mkstemp(suffix=".json", prefix="memrun_")
    os.close(fd)
    try:
        cmd = [sys.executable, "-m", "melaya_bench_framework.memory._child",
               "--scenario", scn_id, "--cycles", str(cycles),
               "--sample-ms", str(sample_ms), "--out", path]
        subprocess.run(cmd, check=True)
        with open(path, "r", encoding="utf-8") as fh:
            return json.load(fh)
    finally:
        try:
            os.unlink(path)
        except OSError:
            pass


def run_scenario(scn: _scenarios.Scenario, n_runs: int, cycles: int,
                 sample_ms: int, placement: str) -> dict:
    runs = [_run_child(scn.id, cycles, sample_ms) for _ in range(n_runs)]

    def col(k: str) -> list[float]:
        return [r[k] for r in runs]

    idle = [r["idle_mb"] for r in runs]
    steady = [r["steady_p50_mb"] for r in runs]
    peak = [r["peak_mb"] for r in runs]
    settle = [r["settle_mb"] for r in runs]
    idle_ws = [r.get("idle_ws_mb", 0.0) for r in runs]
    steady_ws = [r.get("steady_ws_p50_mb", 0.0) for r in runs]
    peak_ws = [r.get("peak_ws_mb", 0.0) for r in runs]
    leak = [r["leak_slope_mb_per_cycle"] for r in runs]
    leak_ci = _median([r["leak_ci95"] for r in runs])
    leak_med = _median(leak)
    py_slope = _median([r["python_heap_slope_mb_per_cycle"] for r in runs])
    rss_slope = _median([r["rss_settle_slope_mb_per_cycle"] for r in runs])

    # leak verdict: flat if the slope CI brackets 0.
    if abs(leak_med) <= leak_ci or abs(leak_med) < 0.05:
        verdict = "flat"
    elif abs(leak_med) < 0.5:
        verdict = "slow"
    else:
        verdict = "leaking"

    # Subject B (model server) - resolved by port only when local. S0/S1 do
    # NOT touch a model, so it stays null here (the decoupling, made explicit).
    model_server = None

    summary = {
        "schema": "memory",
        "bench_shape_version": SHAPE,
        "scenario": scn.id,
        "scenario_slug": _slug(scn, 1),
        "deployment_model": os.environ.get("MEL_MEM_DEPLOYMENT", "isolated_container"),
        "llm_placement": placement,
        "axes": {"placement": placement, "capability": scn.capability, "concurrency": 1},
        "os": OS_KEY,
        "mem_metric": MEM_METRIC,
        "mem_metric_secondary": "working_set",
        "unit": "MB",
        "cross_os_comparable": False,
        "across_runs": {
            "n_runs": n_runs,
            "idle_med_mb": round(_median(idle), 2),
            "idle_iqr_mb": _iqr(idle),
            "steady_p50_med_mb": round(_median(steady), 2),
            "steady_p50_iqr_mb": _iqr(steady),
            "peak_p95_mb": round(_pct(peak, 0.95), 2),
            "peak_med_mb": round(_median(peak), 2),
            "settle_med_mb": round(_median(settle), 2),
            "leak_slope_mb_per_cycle": round(leak_med, 4),
            "leak_ci95": round(leak_ci, 4),
            "leak_verdict": verdict,
            "python_heap_slope_mb_per_cycle": round(py_slope, 4),
            "rss_settle_slope_mb_per_cycle": round(rss_slope, 4),
            "n_cycles": cycles,
            "orphan_child_count": max(r["orphan_child_count"] for r in runs),
        },
        "working_set": {
            "idle_med_mb": round(_median(idle_ws), 2),
            "steady_p50_med_mb": round(_median(steady_ws), 2),
            "peak_med_mb": round(_median(peak_ws), 2),
            "note": "RAM-resident working set (RSS). On Windows this is <= private_bytes; for native-heavy children (sci-stack, torch) private_bytes overstates RAM use, so working_set is the faithful capacity number.",
        },
        "components": {c: (round(_median(steady), 2) if c == "orchestration" else None)
                       for c in scn.components},
        "model_server": model_server,
        "fit": None,  # populated in P2 (capacity) from the N-sweep
        "capacity_example": None,  # P2
        "method": {
            "sample_ms": sample_ms,
            "settle_def": "gc.collect x2 -> malloc_trim(0) -> sample",
            "sampler": "self-thread + recursive children; model-server by port (Subject B)",
            "attribution": f"per-process {MEM_METRIC}",
            "isolation": "fresh subprocess per run",
        },
        "env": env_block(),
        "run_at_unix_ms": int(time.time() * 1000),
        "run_at_iso": time.strftime("%Y-%m-%dT%H:%M:%S+00:00", time.gmtime()),
        "what_this_is": scn.description,
        "what_this_is_not": (
            "NOT the reasoning-LLM weights - the model is an external HTTP "
            "server (cloud/local/enterprise) and its weights are never in "
            "this process. NOT cross-OS comparable (this row is "
            f"{MEM_METRIC}). NOT a leak when RSS settle drifts while the "
            "python-heap slope is flat (that is glibc page retention)."
        ),
    }
    return summary


def main(argv: list[str] | None = None) -> int:
    import argparse
    ap = argparse.ArgumentParser(prog="melaya_bench_framework.memory")
    ap.add_argument("--scenarios", default="s0,s1",
                    help="comma list, e.g. s0,s1")
    ap.add_argument("--runs", type=int, default=5)
    ap.add_argument("--cycles", type=int, default=20)
    ap.add_argument("--sample-ms", type=int, default=30)
    ap.add_argument("--placement", default="cloud",
                    choices=["cloud", "local", "enterprise"])
    args = ap.parse_args(argv)

    root = _results_root() / "memory"
    root.mkdir(parents=True, exist_ok=True)
    ids = [s.strip() for s in args.scenarios.split(",") if s.strip()]
    print(f"[mem] os={OS_KEY} metric={MEM_METRIC} runs={args.runs} "
          f"cycles={args.cycles} scenarios={ids}")
    for sid in ids:
        scn = _scenarios.get(sid)
        summary = run_scenario(scn, args.runs, args.cycles, args.sample_ms, args.placement)
        out_dir = root / summary["scenario_slug"]
        out_dir.mkdir(parents=True, exist_ok=True)
        with open(out_dir / "summary.json", "w", encoding="utf-8", newline="\n") as fh:
            json.dump(summary, fh, indent=2)
        a = summary["across_runs"]
        print(f"[mem] {scn.id:3s} {summary['scenario_slug']:24s} "
              f"idle={a['idle_med_mb']} steady_p50={a['steady_p50_med_mb']} "
              f"peak_p95={a['peak_p95_mb']} settle={a['settle_med_mb']} "
              f"leak={a['leak_slope_mb_per_cycle']}+/-{a['leak_ci95']} "
              f"({a['leak_verdict']}) {summary['mem_metric']}MB "
              f"-> {out_dir / 'summary.json'}")
    return 0
