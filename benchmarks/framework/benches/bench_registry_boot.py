"""bench_registry_boot.py — Registry cold-boot time.

What this measures
------------------

The cost of walking N synthetic tool modules, introspecting every
async tool, building a JSON-schema-shaped params dict, and
registering on a fresh Toolkit. Mirrors what
`the production registry builder` does at production cold start.

Production boots in single-digit seconds with ~600 tools across ~200
modules. This bench's synthetic surface defaults to 250 tools (50
modules × 5 tools/module), which gives a per-tool boot cost that
scales linearly with the production surface.

Cold = fresh `Registry.boot()` call, no module-import cache, no
warmed dicts. We run 30 cold boots and report median + IQR (mirrors
the engine bench's contributor format).

Reproduce
---------

    pytest benches/bench_registry_boot.py -s

Cap: 30 s wall time. Default config (250 tools × 30 cold boots)
finishes in <5 s on modern HW.

Note: this bench measures the REGISTRY walk + register cost. It does
NOT measure module IMPORT time, which dominates production cold boot
(~80 % of registry boot in prod is import-of-each-tool-module-style
filesystem walks + pyc compilation). Production-equivalence requires
adding ~3-5 s of import time on top of this bench's number on a
warmed-cache run, ~10-15 s on a cold-cache first-boot.
"""

from __future__ import annotations

import time
from typing import Any

import pytest

from melaya_bench_framework import Registry, synthesize_tool_modules


N_MODULES = 50         # mirrors a fraction of prod's ~200 modules
TOOLS_PER_MODULE = 5   # → 250 total tools, scales linearly to prod
N_BOOTS = 30           # 30 cold runs for stable median + percentiles


def _measure_one_boot() -> float:
    """Build a fresh module list + register everything on a fresh
    toolkit. Returns boot time in seconds.

    The module synthesis happens OUTSIDE the timed section because in
    production it's the FS-walk + import that's slow, not the
    in-memory bind we're modelling. Bench's `synthesize_tool_modules`
    is ~50 ms for 250 tools; we don't want that swamping the actual
    boot signal.
    """
    modules = synthesize_tool_modules(
        n_modules=N_MODULES,
        tools_per_module=TOOLS_PER_MODULE,
    )
    t0 = time.perf_counter()
    Registry.boot(modules)
    return time.perf_counter() - t0


def _iqr(samples_s: list[float]) -> float:
    """Q3 − Q1 in seconds. Same definition the engine bench uses for
    its tier-row spread."""
    s = sorted(samples_s)
    n = len(s)
    q1 = s[max(0, n // 4)]
    q3 = s[min(n - 1, (3 * n) // 4)]
    return q3 - q1


def test_registry_boot(bench_writer: Any) -> None:
    """10 cold boots → median + IQR + percentile suite."""
    # Warm-up: one untimed boot so import-time-init of the synthesis
    # helpers doesn't pollute the first sample.
    _measure_one_boot()

    samples_s = [_measure_one_boot() for _ in range(N_BOOTS)]
    # The fixture writes µs; convert s → µs once.
    samples_us = [x * 1_000_000.0 for x in samples_s]

    median_s = sorted(samples_s)[len(samples_s) // 2]
    iqr_s = _iqr(samples_s)

    bench_writer(
        metric="registry_boot",
        samples_us=samples_us,
        shim_call="Registry.boot (250 synthetic tools)",
        what_this_is=(
            "Cold-boot cost of walking 50 synthetic tool modules "
            "(250 tools total), introspecting every async tool, "
            "building per-tool JSON-schema-shaped params dict, "
            "registering on a fresh Toolkit. Mirrors "
            "`the production registry builder`."
        ),
        what_this_is_not=(
            "The full production cold boot — that ADDS ~3-15 s of "
            "Python import time for ~200 real `the production tool modules` "
            "modules (FS walk, pyc compile). That import cost is "
            "production-specific and scales with the filesystem, "
            "not with the registry logic we model here."
        ),
        extra={
            "n_modules":      N_MODULES,
            "tools_per_module": TOOLS_PER_MODULE,
            "total_tools":    N_MODULES * TOOLS_PER_MODULE,
            "n_boots":        N_BOOTS,
            "median_s":       median_s,
            "iqr_s":          iqr_s,
        },
    )
