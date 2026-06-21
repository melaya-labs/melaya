"""bench_context_assembly.py - static per-turn context build cost.

Assembles the static context block a turn sends the model: system prompt
+ granted knowledge docs + tool schemas. Distinct from rolling history
(model_wrapper) and RAG retrieval. Reproduce: pytest benches/bench_context_assembly.py -s
"""
from __future__ import annotations
import time
from typing import Any
from melaya_bench_framework import ContextAssembler

ITERATIONS = 5_000


def test_context_assembly(bench_writer: Any) -> None:
    system = "You are a governed trading agent. " * 16
    docs = ["Knowledge doc paragraph. " * 40 for _ in range(5)]
    tools = [{"name": f"tool_{i}", "parameters": {"a": "int", "b": "str"}} for i in range(10)]
    asm = ContextAssembler(system, docs, tools)
    for _ in range(200):
        asm.assemble()
    s: list[float] = []
    perf = time.perf_counter_ns
    for _ in range(ITERATIONS):
        t0 = perf(); asm.assemble(); s.append((perf() - t0) / 1000.0)
    bench_writer(
        metric="context_assembly", samples_us=s,
        shim_call="ContextAssembler.assemble (system + 5 docs + 10 tools)",
        what_this_is=("Per-turn cost of building the static context block the model sees: "
                      "system prompt + granted knowledge docs + tool schemas, packed into "
                      "one payload. Pure string/dict assembly, no model call."),
        what_this_is_not=("The rolling message-history pack (model_wrapper) or RAG retrieval "
                          "(rag). Knowledge-doc content here is synthetic."),
        extra={"iterations": ITERATIONS, "n_docs": 5, "n_tools": 10},
    )
