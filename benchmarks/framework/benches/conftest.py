"""Shared fixtures + the per-bench summary.json writer.

Every bench in this directory writes a `results/<metric>/summary.json`
that mirrors the engine bench's shape:

    {
      "n": ...,
      "min_us": ..., "p50_us": ..., "p95_us": ..., "p99_us": ...,
      "max_us": ...,
      "metric": "tool_dispatch",
      "shim_call":   "Toolkit.dispatch",
      "bench_shape_version": "0.1.x-shape",
      "headline_samples": ...,
      "run_at_unix_ms":  ..., "run_at_iso": "...",
      "env": { cpu_model, logical_cores, os_kernel, python_version, ... },
      "what_this_is":     "...",
      "what_this_is_not": "...",
      "reference_hardware": "..."
    }

The writer is exposed as a pytest fixture (`bench_writer`) so each
bench file calls it once with its own metric name + raw samples list.
"""

from __future__ import annotations

import json
import os
import platform
import subprocess
import sys
import time
from pathlib import Path
from typing import Any, Callable

import pytest

from melaya_bench_framework import BENCH_SHAPE_VERSION


def _results_root() -> Path:
    """Resolve to `benchmarks/framework/results/`. Stable regardless
    of cwd."""
    here = Path(__file__).resolve().parent
    root = here.parent / "results"
    root.mkdir(parents=True, exist_ok=True)
    return root


# ── Env probes — best-effort, fall to "unknown" on any failure ─────────


def _shell(cmd: list[str]) -> str | None:
    """Run a shell command, return trimmed stdout or None."""
    try:
        out = subprocess.run(
            cmd, capture_output=True, text=True, timeout=2,
        )
        if out.returncode != 0:
            return None
        s = out.stdout.strip()
        return s or None
    except Exception:
        return None


def _cpu_model() -> str:
    """Order: Linux /proc/cpuinfo → macOS sysctl → Windows env →
    powershell → unknown. Same probe order as the engine bench."""
    if sys.platform.startswith("linux"):
        try:
            with open("/proc/cpuinfo", "r", encoding="utf-8") as f:
                for line in f:
                    if line.startswith("model name"):
                        return line.split(":", 1)[1].strip()
        except Exception:
            pass
    if sys.platform == "darwin":
        s = _shell(["sysctl", "-n", "machdep.cpu.brand_string"])
        if s:
            return s
    if sys.platform.startswith("win"):
        # Prefer the friendly marketing name (matches the engine bench's
        # "Intel(R) Core(TM) i7-..." shape) so committed summaries stay
        # readable; fall back to the terse PROCESSOR_IDENTIFIER only if
        # WMI/powershell is unavailable.
        s = _shell([
            "powershell", "-NoProfile", "-Command",
            "(Get-CimInstance Win32_Processor).Name",
        ])
        if s:
            return s
        env_id = os.environ.get("PROCESSOR_IDENTIFIER")
        if env_id:
            return env_id
    return platform.processor() or "unknown"


def _logical_cores() -> int:
    return os.cpu_count() or 0


def _os_kernel() -> str:
    """OS + kernel/version. Same format the engine bench prints."""
    if sys.platform.startswith("linux") or sys.platform == "darwin":
        s = _shell(["uname", "-srm"])
        if s:
            return s
    if sys.platform.startswith("win"):
        return f"Windows {platform.release()} ({platform.version()})"
    return f"{platform.system()} {platform.release()}"


def _cpu_governor() -> str | None:
    """Linux only — reads `/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor`."""
    p = Path("/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor")
    try:
        return p.read_text().strip()
    except Exception:
        return None


def _turbo_state() -> str | None:
    p = Path("/sys/devices/system/cpu/intel_pstate/no_turbo")
    try:
        s = p.read_text().strip()
        return {
            "0": "intel_pstate: turbo ENABLED",
            "1": "intel_pstate: turbo DISABLED",
        }.get(s, f"intel_pstate: no_turbo={s}")
    except Exception:
        return None


def _env_block() -> dict[str, Any]:
    return {
        "cpu_model":      _cpu_model(),
        "logical_cores":  _logical_cores(),
        "os_kernel":      _os_kernel(),
        "python_version": sys.version.split()[0],
        "cpu_governor":   _cpu_governor(),
        "turbo_state":    _turbo_state(),
        "arch":           platform.machine(),
        "os":             sys.platform,
    }


# ── Percentile helpers ─────────────────────────────────────────────────


def _percentiles(samples_us: list[float]) -> dict[str, float]:
    """Return min / p50 / p90 / p95 / p99 / p999 / max in microseconds.
    Matches the engine bench's percentile suite."""
    if not samples_us:
        return {k: 0.0 for k in (
            "min_us", "p50_us", "p90_us", "p95_us", "p99_us", "p999_us", "max_us",
        )}
    s = sorted(samples_us)
    n = len(s)
    def pct(p: float) -> float:
        idx = min(n - 1, int(round((n - 1) * p)))
        return float(s[idx])
    return {
        "min_us":  float(s[0]),
        "p50_us":  pct(0.50),
        "p90_us":  pct(0.90),
        "p95_us":  pct(0.95),
        "p99_us":  pct(0.99),
        "p999_us": pct(0.999),
        "max_us":  float(s[-1]),
    }


# ── The fixture every bench uses ───────────────────────────────────────


@pytest.fixture(scope="session")
def bench_writer() -> Callable[..., None]:
    """Return a callable each bench invokes once with its samples +
    metadata. Writes `results/<metric>/summary.json` (and a CSV of raw
    samples for plotting).

    Args (kwargs only):
        metric:              str — e.g. "tool_dispatch"
        samples_us:          list[float] — per-iteration latency in µs
        shim_call:           str — the shim call this maps to
        what_this_is:        str — one-paragraph explainer
        what_this_is_not:    str — one-paragraph anti-explainer
        reference_hardware:  str — copy of the README reference line
        extra:               dict — bench-specific metadata (corpus
                                 size, pipeline length, …)
    """

    def _write(
        *,
        metric: str,
        samples_us: list[float],
        shim_call: str,
        what_this_is: str,
        what_this_is_not: str,
        reference_hardware: str = "",
        extra: dict[str, Any] | None = None,
    ) -> None:
        dest = _results_root() / metric
        dest.mkdir(parents=True, exist_ok=True)
        pcts = _percentiles(samples_us)
        summary: dict[str, Any] = {
            "n": len(samples_us),
            **pcts,
            "metric": metric,
            "shim_call": shim_call,
            "bench_shape_version": BENCH_SHAPE_VERSION,
            "headline_samples": len(samples_us),
            "run_at_unix_ms": int(time.time() * 1000),
            "run_at_iso": time.strftime(
                "%Y-%m-%dT%H:%M:%S+00:00", time.gmtime()
            ),
            "env": _env_block(),
            "what_this_is": what_this_is,
            "what_this_is_not": what_this_is_not,
            "reference_hardware": reference_hardware,
        }
        if extra:
            summary["extra"] = extra
        (dest / "summary.json").write_text(
            json.dumps(summary, indent=2) + "\n", encoding="utf-8"
        )
        # Raw CSV for plotting / debugging — same format as the engine
        # bench's CSV (`iteration,ns`), but in microseconds here.
        with (dest / f"{metric}_us.csv").open("w", encoding="utf-8") as f:
            f.write("iteration,us\n")
            for i, x in enumerate(samples_us):
                f.write(f"{i},{x:.6f}\n")
        # Echo headline to stderr so a `pytest -s` user gets a
        # one-screen result without opening the file.
        print(
            f"\n  {metric}  n={len(samples_us)}  "
            f"p50={pcts['p50_us']:.2f} µs  "
            f"p95={pcts['p95_us']:.2f} µs  "
            f"p99={pcts['p99_us']:.2f} µs  "
            f"max={pcts['max_us']:.2f} µs",
            file=sys.stderr,
        )
        print(
            f"  → wrote results/{metric}/summary.json", file=sys.stderr,
        )

    return _write
