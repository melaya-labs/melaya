"""bench_rag_retrieval.py — RAG retrieve latency at 10k and 100k chunks.

What this measures
------------------

End-to-end `RagIndex.retrieve(query, top_k=5)`:

    embed(query)  →  brute-force inner-product search  →  hydrate top-k

Two corpus sizes are benched, exposing the curve:

    • 10k  chunks at 384 dims — typical small RAG store
    • 100k chunks at 384 dims — typical medium RAG store

(1M chunks is intentionally NOT benched — at brute force that's ~50
ms per query on CPU, which would blow the 30 s per-bench cap with
the iteration counts we need for tight percentiles. For 1M+ you want
a real ANN index; production uses an embedded vector store.)

Embedder defaults to RandomProjectionEmbedder — fast, deterministic,
no model download. Pass `--use-real-embedder` to switch to MiniLM
(requires the `[real-embedder]` extra).

Reproduce
---------

    pytest benches/bench_rag_retrieval.py -s
    pytest benches/bench_rag_retrieval.py -s --use-real-embedder

Cap: 30 s wall time. Default config:
    - 10k corpus: 2000 iterations × ~100 µs/query  = ~50 ms timed work
    - 100k corpus: 2000 iterations × ~1 ms/query  = ~500 ms timed work
Both run in <10 s total including corpus seeding.
"""

from __future__ import annotations

import time
from typing import Any

import pytest

from melaya_bench_framework import (
    RagIndex,
    RandomProjectionEmbedder,
    make_synthetic_corpus,
)


# Configure pytest with a flag to switch embedders.
def pytest_addoption(parser):
    parser.addoption(
        "--use-real-embedder",
        action="store_true",
        default=False,
        help="Use sentence-transformers/all-MiniLM-L6-v2 instead of "
             "the deterministic RandomProjectionEmbedder (requires "
             "the [real-embedder] extra to be installed).",
    )


ITERATIONS = 2000


def _build_embedder(use_real: bool):
    if use_real:
        from melaya_bench_framework.rag import MiniLMEmbedder
        return MiniLMEmbedder()
    return RandomProjectionEmbedder()


def _measure(n_chunks: int, embedder) -> list[float]:
    """Time `RagIndex.retrieve` over ITERATIONS calls. Returns
    per-call latency in microseconds."""
    corpus = make_synthetic_corpus(n_chunks, embedder)
    index = RagIndex(corpus, embedder)

    # Cycle through a few different queries so the embedder's hash
    # cache (if any — MiniLM has no per-call cache) doesn't artificially
    # tighten the distribution.
    queries = [f"tok{i % 200} tok{(i * 7) % 200} tok{(i * 13) % 200}"
               for i in range(8)]

    # Warm-up
    for q in queries * 3:
        index.retrieve(q, top_k=5)

    samples_us: list[float] = []
    perf = time.perf_counter_ns
    for i in range(ITERATIONS):
        q = queries[i % len(queries)]
        t0 = perf()
        index.retrieve(q, top_k=5)
        samples_us.append((perf() - t0) / 1000.0)
    return samples_us


@pytest.fixture(scope="module")
def embedder(request):
    use_real = bool(request.config.getoption("--use-real-embedder", default=False))
    return _build_embedder(use_real), use_real


def test_rag_retrieval_10k(bench_writer: Any, embedder) -> None:
    """10k chunks × 5 top-k × 500 queries."""
    emb, use_real = embedder
    samples = _measure(10_000, emb)
    bench_writer(
        metric="rag_retrieval_10k",
        samples_us=samples,
        shim_call="RagIndex.retrieve(top_k=5) over 10k 384-dim chunks",
        what_this_is=(
            "Per-query cost of embed(query) + brute-force inner-product "
            "search + hydrate top-5 chunks against a 10k-chunk in-memory "
            "index. Mirrors `the production RAG retrieve path` for "
            "the 10k-chunk corpus case."
        ),
        what_this_is_not=(
            "A production ANN index — typically 1.5-3× faster than the "
            "brute-force baseline this bench reports. Production sees "
            "FASTER numbers than this for the same corpus size. The "
            "embedding step also varies by embedder: MiniLM ~5-15 ms, "
            "RandomProjection ~50 µs."
        ),
        extra={
            "n_chunks": 10_000,
            "dim": 384,
            "top_k": 5,
            "iterations": ITERATIONS,
            "embedder": ("MiniLM-L6-v2" if use_real
                         else "RandomProjection-deterministic"),
            "ann_backend": "numpy brute-force (cosine via inner product)",
        },
    )


def test_rag_retrieval_100k(bench_writer: Any, embedder) -> None:
    """100k chunks × 5 top-k × 500 queries."""
    emb, use_real = embedder
    samples = _measure(100_000, emb)
    bench_writer(
        metric="rag_retrieval_100k",
        samples_us=samples,
        shim_call="RagIndex.retrieve(top_k=5) over 100k 384-dim chunks",
        what_this_is=(
            "Same path as rag_retrieval_10k but with a 100k-chunk "
            "corpus. The curve from 10k → 100k shows how the search "
            "step scales: brute force is O(N·D), so expect ~10× "
            "growth in p50 between the two."
        ),
        what_this_is_not=(
            "Production-scale RAG. For corpora past 1M, switch to "
            "a production ANN index, which is sub-linear in N."
        ),
        extra={
            "n_chunks": 100_000,
            "dim": 384,
            "top_k": 5,
            "iterations": ITERATIONS,
            "embedder": ("MiniLM-L6-v2" if use_real
                         else "RandomProjection-deterministic"),
            "ann_backend": "numpy brute-force (cosine via inner product)",
        },
    )
