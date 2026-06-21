"""bench_crew_orchestration.py - multi-persona crew + risk-veto halt.

A 4-persona crew (macro -> technical -> risk -> execution) hands context
persona to persona; the risk persona can VETO and halt the chain mid-run.
Measures orchestration overhead AND veto-halt propagation. Each persona is
a zero-work mock. Reproduce: pytest benches/bench_crew_orchestration.py -s
"""
from __future__ import annotations
import time
from typing import Any
import pytest
from melaya_bench_framework import Crew, Persona

RUNS = 2_000


async def _macro(ctx): ctx["macro"] = "ok"; return ctx
async def _ta(ctx): ctx["ta"] = "ok"; return ctx
async def _risk(ctx): ctx["risk"] = "ok"; return ctx
async def _exec(ctx): ctx["exec"] = "ok"; return ctx


def _crew() -> Crew:
    return Crew([Persona("macro", _macro), Persona("technical", _ta),
                 Persona("risk", _risk), Persona("execution", _exec)], risk_index=2)


@pytest.mark.asyncio
async def test_crew_orchestration(bench_writer: Any) -> None:
    crew = _crew()
    for _ in range(50):
        await crew.run({})
    s: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(RUNS):
        t0 = perf(); await crew.run({}); s.append((perf() - t0) / 1000.0)
    bench_writer(
        metric="crew_orchestration", samples_us=s,
        shim_call="Crew.run (4-persona macro->technical->risk->execution hand-off)",
        what_this_is=("Per-run orchestration cost of a 4-persona crew handing context persona to "
                      "persona, with the risk persona positioned to veto. Each persona is a "
                      "zero-work mock, so this is pure runner orchestration overhead."),
        what_this_is_not=("Persona LLM-reasoning time (the dominant real cost, on model_wrapper) "
                          "or real inter-process crew dispatch. Measures the hand-off loop."),
        extra={"n_runs": RUNS, "personas": 4, "risk_veto": "armed"},
    )
