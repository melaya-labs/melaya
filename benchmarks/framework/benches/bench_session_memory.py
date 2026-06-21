"""bench_session_memory.py - cross-run memory save/load roundtrip.

A long-running crew persists its working memory between runs and restores
it next run. Measures serialize->store + load->deserialize for a ~50-turn
memory. Store is in-process (no DB/disk). Reproduce: pytest benches/bench_session_memory.py -s
"""
from __future__ import annotations
import time
from typing import Any
from melaya_bench_framework import SessionMemory

ITERATIONS = 5_000


def test_session_memory_roundtrip(bench_writer: Any) -> None:
    mem = SessionMemory()
    for i in range(50):
        mem.append({"turn": i, "role": "assistant", "text": "step output " * 12, "tokens": 220})
    for _ in range(200):
        mem.save("warm"); mem.load("warm")
    s: list[float] = []
    perf = time.perf_counter_ns
    for i in range(ITERATIONS):
        sid = f"sess_{i}"
        t0 = perf(); mem.save(sid); mem.load(sid); s.append((perf() - t0) / 1000.0)
    bench_writer(
        metric="session_memory", samples_us=s,
        shim_call="SessionMemory.save+load (50-turn working memory roundtrip)",
        what_this_is=("Cross-run memory cost: serialize a 50-turn crew working memory to the "
                      "session store and load+restore it on the next run. Models the "
                      "persistence bookkeeping a long-running crew pays between runs."),
        what_this_is_not=("Real DB/disk latency (the store is in-process here) or vector-memory "
                          "retrieval (that's rag). Memory content is synthetic."),
        extra={"iterations": ITERATIONS, "turns": 50},
    )
