"""RAG retrieval shim — reproduces `the production RAG retrieve path`.

The production path is:

    1. embed(query) via the configured embedder (MiniLM in default builds)
    2. ANN search over an embedded vector store for top_k chunk IDs
    3. Hydrate chunks (id → text) from the in-process cache

This shim does the same three steps in-process. The embedder is a
deterministic random-projection by default (no model download, runs
on air-gapped CI); pass `use_real_embedder=True` to switch to
sentence-transformers/all-MiniLM-L6-v2 (~80 MB one-time download). The
ANN backend is a numpy brute-force inner-product search — for the
corpus sizes we bench (10k / 100k chunks at 384 dims), brute force
gives a deterministic upper-bound on what a production ANN index achieves with default
parameters.

Why brute force is "good enough" as a comparable:

   • A production ANN index is typically 1.5-3x faster
     than brute force at 100k vectors. That means this bench's
     numbers are a SLOW-CASE bound for what production sees, not a
     ceiling. A user comparing their measured production ANN index to this
     bench should see the production index come in faster, not slower.
   • Bench reproducibility wins over absolute fidelity. Spinning up
     a vector-store server inside CI / Docker would add a 30-90 s startup cost +
     a network hop that swamps the actual retrieve latency we want
     to measure.
"""

from __future__ import annotations

import dataclasses
import hashlib
import random
from typing import Any, Callable

import numpy as np

DEFAULT_DIM = 384  # matches MiniLM-L6-v2 output dim
DEFAULT_TOP_K = 5


@dataclasses.dataclass
class _Chunk:
    """One indexed chunk. `text` is the hydrated payload returned to
    the caller; `embedding` is the search-time vector."""
    id: int
    text: str
    embedding: np.ndarray


# ── Embedders ───────────────────────────────────────────────────────────


class RandomProjectionEmbedder:
    """Deterministic random-projection embedder. Fast (~50 µs / query),
    no model download. Not a real semantic embedder — the bench
    measures the RETRIEVE path, not embedding quality.

    Why deterministic: same input → same output across runs, which
    keeps the bench reproducible.
    """

    def __init__(self, dim: int = DEFAULT_DIM, seed: int = 42) -> None:
        self.dim = dim
        rng = np.random.default_rng(seed)
        # The "projection matrix" — a fixed random basis we hash
        # tokens into. Same trick as the locality-sensitive hashing
        # family; cheap, deterministic, good enough as a stand-in for
        # an embedder that produces normalised dense vectors.
        self._basis = rng.standard_normal((1024, dim)).astype(np.float32)

    def __call__(self, text: str) -> np.ndarray:
        # Hash the text → a bag-of-tokens index → average the
        # corresponding basis rows. Pure numpy; ~50 µs on modern HW.
        tokens = text.lower().split()
        if not tokens:
            return np.zeros(self.dim, dtype=np.float32)
        idxs = np.array(
            [int(hashlib.blake2b(t.encode(),
                                  digest_size=4).hexdigest(), 16) % 1024
             for t in tokens],
            dtype=np.int64,
        )
        vec = self._basis[idxs].mean(axis=0)
        # Normalise so inner product == cosine similarity.
        norm = np.linalg.norm(vec)
        return vec / norm if norm > 0 else vec


class MiniLMEmbedder:
    """Wraps sentence-transformers/all-MiniLM-L6-v2 for a "real"
    embedder. Opt-in via the `[real-embedder]` extra.

    First call triggers the model download (~80 MB) which is cached
    locally. Subsequent calls take ~5-15 ms on CPU.
    """

    def __init__(self) -> None:
        try:
            from sentence_transformers import SentenceTransformer
        except ImportError as e:
            raise ImportError(
                "MiniLMEmbedder requires the [real-embedder] extra:\n"
                "    pip install 'melaya-bench-framework[real-embedder]'\n"
                f"Underlying error: {e}"
            )
        self.model = SentenceTransformer(
            "sentence-transformers/all-MiniLM-L6-v2",
        )
        self.dim = self.model.get_sentence_embedding_dimension()

    def __call__(self, text: str) -> np.ndarray:
        vec = self.model.encode(
            text,
            normalize_embeddings=True,
            convert_to_numpy=True,
        )
        return vec.astype(np.float32)


# ── Corpus + Index ──────────────────────────────────────────────────────


def make_synthetic_corpus(
    n_chunks: int,
    embedder: Callable[[str], np.ndarray],
    seed: int = 0,
) -> list[_Chunk]:
    """Build N synthetic chunks of pseudo-text + their embeddings.

    Each chunk is a 30-token block of pronounceable token IDs, which
    is enough variety that the embedder produces distinct vectors but
    keeps the per-chunk text small (~200 bytes) so memory pressure
    isn't the dominant cost.
    """
    rng = random.Random(seed)
    vocab = [f"tok{i}" for i in range(2000)]
    chunks: list[_Chunk] = []
    for i in range(n_chunks):
        text = " ".join(rng.choice(vocab) for _ in range(30))
        chunks.append(_Chunk(id=i, text=text, embedding=embedder(text)))
    return chunks


class RagIndex:
    """In-memory brute-force vector index. O(N·D) per query, which
    swamps any structural overhead — keeping the bench's numbers
    dominated by the actual vector math, not Python control flow.

    Use `retrieve(query, top_k)` to mirror the production
    the production retrieve path contract.
    """

    def __init__(
        self,
        chunks: list[_Chunk],
        embedder: Callable[[str], np.ndarray],
    ) -> None:
        self.chunks = chunks
        self.embedder = embedder
        # Stack embeddings into one contiguous matrix for SIMD-friendly
        # inner-product search. Matches a production in-memory layout for
        # the brute-force fallback path.
        self._matrix = np.stack([c.embedding for c in chunks]).astype(np.float32)

    def retrieve(
        self,
        query: str,
        top_k: int = DEFAULT_TOP_K,
    ) -> list[dict[str, Any]]:
        """Embed the query, score against every chunk, return top_k
        hydrated chunks as {id, text, score} dicts.

        Production returns the same shape from the production retrieve path.
        """
        # Step 1: embed
        q = self.embedder(query)
        # Step 2: inner-product search (cosine since both sides
        # are L2-normalised). `argpartition` is O(N) for top_k; the
        # final sort is O(K log K).
        scores = self._matrix @ q
        if top_k >= len(scores):
            top_idx = np.argsort(-scores)
        else:
            top_idx = np.argpartition(-scores, top_k)[:top_k]
            top_idx = top_idx[np.argsort(-scores[top_idx])]
        # Step 3: hydrate
        return [
            {
                "id": int(self.chunks[i].id),
                "text": self.chunks[i].text,
                "score": float(scores[i]),
            }
            for i in top_idx
        ]
