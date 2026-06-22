"""Memory scenarios.

P0 ships the two scenarios that GATE the capacity arithmetic:

  S0  idle      - the runner floor after heavy-module warm-up, no workload.
  S1  baseline  - LLM-agnostic orchestration: the real Toolkit.dispatch
                  hot path, no model, no tools-with-side-effects, no
                  browser/RAG. Proves the pipeline footprint is identical
                  cloud/local/enterprise (the model never lives here).

Heavier scenarios (RAG/Qdrant, browser/Scrapling, aiml-torch, ws-watcher,
code-exec, model-server rows) land in P1-P3 and import their real deps
only when their scenario id is selected - so S0/S1 stay lightweight.

Each scenario exposes:
  warm()             -> import + module warm-up (timed as the idle floor)
  async cycle(state) -> one unit of steady-state work (repeated n_cycles)
"""

from __future__ import annotations

import atexit
import os
import subprocess
import sys
import tempfile
from dataclasses import dataclass, field
from typing import Any, Awaitable, Callable

# Child processes (MCP stdio servers, etc.) we spawn and must reap so the
# orphan gate stays at 0 - terminated when the measurement subject exits.
_TRACKED_PROCS: list[subprocess.Popen] = []


def _track(p: subprocess.Popen) -> subprocess.Popen:
    _TRACKED_PROCS.append(p)
    return p


@atexit.register
def _reap_tracked() -> None:
    for p in _TRACKED_PROCS:
        try:
            p.terminate()
            p.wait(timeout=2)
        except Exception:  # noqa: BLE001
            try:
                p.kill()
            except Exception:  # noqa: BLE001
                pass


@dataclass
class Scenario:
    id: str
    capability: str
    placement: str
    description: str
    warm: Callable[[], None]
    cycle: Callable[[dict], Awaitable[None]]
    n_cycles: int = 20
    components: list[str] = field(default_factory=lambda: ["orchestration"])


# ── S0: idle floor ──────────────────────────────────────────────────────

def _warm_runtime() -> None:
    # Importing the package warms every shim module (toolkit, pipeline,
    # registry, rag, model_wrapper, ...) + numpy - the same module set the
    # latency suite pays for. This is the honest "idle floor" basis.
    import melaya_bench_framework  # noqa: F401
    import asyncio  # noqa: F401
    import json  # noqa: F401


async def _cycle_idle(state: dict) -> None:
    # No work - S0 measures the floor + confirms it doesn't drift.
    import asyncio
    await asyncio.sleep(0.02)


# ── S1: LLM-agnostic orchestration baseline ─────────────────────────────

def _warm_baseline() -> None:
    _warm_runtime()


async def _cycle_baseline(state: dict) -> None:
    # One orchestration cycle on the REAL dispatch path: build a Toolkit,
    # register a 5-arg async tool, dispatch a batch. No model call, no
    # network, no side-effecting tool - pure runner overhead, the shape a
    # pipeline pays per step regardless of which LLM it talks to.
    from melaya_bench_framework import Toolkit, async_tool

    toolkit = Toolkit()
    name = "mem_baseline_tool"
    toolkit.register_tool_function(async_tool, func_name=name)
    shapes = (lambda i: i, lambda i: f"val_{i}", lambda i: i * 1.5,
              lambda i: bool(i % 2), lambda i: {"k": i, "s": f"x{i}"})
    call = {
        "type": "tool_use",
        "id": "mem-1",
        "name": name,
        "input": {f"arg_{i}": shapes[i % len(shapes)](i) for i in range(5)},
    }
    for _ in range(200):
        await toolkit.dispatch(call)
    # toolkit goes out of scope -> a healthy cycle reclaims it; the leak
    # gauge confirms steady-state RSS does not climb across cycles.


# ── S2: RAG (Qdrant) - resident in-runner index ─────────────────────────

def _warm_qdrant() -> None:
    _warm_runtime()
    import qdrant_client  # noqa: F401
    import numpy  # noqa: F401


async def _cycle_rag_qdrant(state: dict) -> None:
    # Build the in-runner Qdrant index ONCE (resident), then query each
    # cycle - steady measures base + the resident 10k x 768 index. The
    # embedder is REMOTE (Ollama/cloud) in production, so it is NOT here;
    # this isolates the index footprint the runner actually carries.
    import numpy as np
    from qdrant_client import QdrantClient, models

    if "client" not in state:
        dim, n = 768, 10000
        client = QdrantClient(location=":memory:")
        client.create_collection(
            "c", vectors_config=models.VectorParams(size=dim, distance=models.Distance.COSINE))
        rng = np.random.default_rng(7)
        vecs = rng.random((n, dim), dtype=np.float32)
        client.upload_collection(collection_name="c", vectors=vecs,
                                 ids=list(range(n)), batch_size=2000)
        state["client"], state["dim"] = client, dim
    q = np.random.default_rng().random(state["dim"], dtype=np.float32)
    state["client"].query_points("c", query=q.tolist(), limit=5)


# ── S3a: web_search fast path (curl_cffi, no browser) ───────────────────

def _warm_curl() -> None:
    _warm_runtime()
    import curl_cffi  # noqa: F401


async def _cycle_websearch_fast(state: dict) -> None:
    from curl_cffi import requests as creq
    try:
        creq.get("https://example.com", impersonate="chrome", timeout=8)
    except Exception:
        pass  # import + TLS stack is the resident cost; the fetch is transient


# ── S3b: web_search RESCUE - system Chrome (real_chrome=True) ────────────
# Mirrors the production stealth-fetch rescue path, which prefers the user's
# INSTALLED Chrome (real_chrome=True) over bundled Chromium, and is only the
# RESCUE for the small fraction of fetches the curl_cffi fast path (S3a)
# can't serve. Most web_search hits never launch a browser at all.

def _warm_browser() -> None:
    _warm_runtime()


async def _cycle_websearch_browser(state: dict) -> None:
    import sys
    # Async fetcher (we are inside an asyncio loop). real_chrome=True =
    # system Chrome, the production rescue engine; bundled is the fallback.
    try:
        from scrapling.fetchers import StealthyFetcher
        try:
            await StealthyFetcher.async_fetch(
                "https://example.com", headless=True, network_idle=False, real_chrome=True)
        except TypeError:
            # older scrapling without real_chrome kw -> bundled (record as such)
            await StealthyFetcher.async_fetch(
                "https://example.com", headless=True, network_idle=False)
    except Exception as e:  # noqa: BLE001
        print(f"[scrape] {type(e).__name__}: {e}", file=sys.stderr)


# ── S-AIML: in-process HF inference (transformers + torch) ──────────────

def _warm_aiml() -> None:
    _warm_runtime()


async def _cycle_aiml(state: dict) -> None:
    # The ONE in-process model load (distinct from the external reasoning
    # LLM). CPU device => measures the RAM footprint; on GPU the weights
    # offload to VRAM instead.
    if "pipe" not in state:
        from transformers import pipeline
        state["pipe"] = pipeline(
            "sentiment-analysis",
            model="distilbert-base-uncased-finetuned-sst-2-english",
            device=-1,
        )
    state["pipe"]("Memory benchmarking is going well.")


# ── S2b: RAG (Qdrant) 100k + doc-ingest transient ───────────────────────

def _warm_rag100k() -> None:
    _warm_runtime()
    import qdrant_client  # noqa: F401
    import numpy  # noqa: F401
    import pypdf  # noqa: F401


async def _cycle_rag_100k(state: dict) -> None:
    # Resident: a 100k x 768 in-runner index (built once, batched so the
    # build transient stays bounded). Each cycle also does a doc-ingest
    # transient - a multi-MB text buffer chunked into an embedding-shaped
    # batch (the to-be-embedded array) - which is what doc extraction costs
    # before the REMOTE embedder is called. Embedder weights are NOT here.
    import numpy as np
    from qdrant_client import QdrantClient, models

    if "client" not in state:
        dim, n = 768, 100000
        client = QdrantClient(location=":memory:")
        client.create_collection(
            "c", vectors_config=models.VectorParams(size=dim, distance=models.Distance.COSINE))
        rng = np.random.default_rng(7)
        for off in range(0, n, 10000):
            vecs = rng.random((10000, dim), dtype=np.float32)
            client.upload_collection(collection_name="c", vectors=vecs,
                                     ids=list(range(off, off + 10000)), batch_size=5000)
            del vecs
        state["client"], state["dim"] = client, dim
    # doc-ingest transient: ~12 MB of text -> ~1.5k chunks -> float32 batch
    doc = ("Melaya local-runner memory characterization. " * 6000)
    chunks = [doc[i:i + 800] for i in range(0, len(doc), 700)]
    batch = np.zeros((len(chunks), state["dim"]), dtype=np.float32)  # embed-shaped transient
    q = np.random.default_rng().random(state["dim"], dtype=np.float32)
    state["client"].query_points("c", query=q.tolist(), limit=5)
    del batch, chunks, doc


# ── S11: WSS ingress watcher - bounded in-runner ring buffers ────────────

def _warm_wss() -> None:
    _warm_runtime()
    import websockets  # noqa: F401  (the ws client stack the watcher uses)


async def _cycle_wss(state: dict) -> None:
    # Mirrors ws_ingress_watcher: bounded deques per symbol. Bounded =>
    # steady RSS must NOT climb (the leak gate is the real assertion here).
    import collections
    import time
    if "bars" not in state:
        syms = [f"SYM{i}" for i in range(12)]
        state["bars"] = {s: collections.deque(maxlen=2000) for s in syms}
        state["trades"] = {s: collections.deque(maxlen=5000) for s in syms}
    t = time.time()
    for dq in state["bars"].values():
        dq.append((t, 100.0, 101.0, 99.0, 100.5, 1234.0))
    for dq in state["trades"].values():
        for _ in range(40):
            dq.append((t, 100.5, 0.1, True))


# ── S12: python_repl code-exec - child interpreter + sci-stack (peak) ────

def _warm_repl() -> None:
    _warm_runtime()


async def _cycle_coderepl(state: dict) -> None:
    # The code-exec tool spawns a child interpreter. The sampler walks
    # recursive children, so the child's sci-stack RSS lands in the peak.
    code = (
        "import numpy as np, pandas as pd, scipy.signal as sg, time;"
        "df=pd.DataFrame(np.random.rand(60000,8));"
        "x=df.values@np.random.rand(8,8);"
        "sg.welch(x[:,0]);"
        "time.sleep(0.6)"
    )
    p = await asyncio_create(sys.executable, "-c", code)
    await p.wait()


async def asyncio_create(*cmd: str):
    import asyncio
    return await asyncio.create_subprocess_exec(*cmd)


# ── S15: MCP stdio server - one resident child process per server ────────

_MCP_SRC = (
    "import sys\n"
    "from mcp.server.fastmcp import FastMCP\n"
    "m = FastMCP('bench')\n"
    "@m.tool()\n"
    "def ping() -> str:\n"
    "    return 'ok'\n"
    "m.run()\n"
)


def _warm_mcp() -> None:
    _warm_runtime()


async def _cycle_mcp(state: dict) -> None:
    # Spawn ONE stdio MCP server child (FastMCP, blocking on stdin) and keep
    # it resident; the sampler attributes its RSS as a child of the subject.
    # Reaped by the atexit hook so the orphan gate stays 0.
    if "proc" not in state:
        fd, path = tempfile.mkstemp(suffix=".py", prefix="mcp_srv_")
        with os.fdopen(fd, "w", encoding="utf-8") as fh:
            fh.write(_MCP_SRC)
        state["path"] = path
        state["proc"] = _track(subprocess.Popen(
            [sys.executable, path],
            stdin=subprocess.PIPE, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL))
        import time
        time.sleep(0.4)  # let the server import mcp + initialize
    import asyncio
    await asyncio.sleep(0.02)


# ── S16: long-context compaction - saw-tooth history, reclaim proof ──────

def _warm_compaction() -> None:
    _warm_runtime()


async def _cycle_compaction(state: dict) -> None:
    # Grow a large message history, compact to a short summary, drop the
    # rest. Transient peak per cycle; steady must reclaim (leak gate).
    hist = [{"role": "assistant", "content": "x" * 2000, "i": i} for i in range(4000)]
    summary = "".join(m["content"][:1] for m in hist[-64:])
    state["summary"] = summary  # tiny retained; ~8 MB hist freed at scope exit


# ── S17: streaming assembly - SSE / token accumulation ──────────────────

def _warm_streaming() -> None:
    _warm_runtime()


async def _cycle_streaming(state: dict) -> None:
    buf = []
    for _ in range(40000):
        buf.append("tok ")  # accumulate streamed tokens
    text = "".join(buf)      # assemble
    state["len"] = len(text)  # retain only the length; buffers freed


# ── S18: huge tool output - multi-MB transient through dispatch ─────────

def _warm_tooloutput() -> None:
    _warm_baseline()


async def _cycle_tooloutput(state: dict) -> None:
    # A tool returns a multi-MB payload that flows through the REAL dispatch
    # path (serialize + hand back). Transient peak; must not accumulate.
    from melaya_bench_framework import Toolkit, async_tool

    toolkit = Toolkit()
    name = "mem_bigout_tool"
    toolkit.register_tool_function(async_tool, func_name=name)
    payload = "D" * (6 * 1024 * 1024)  # 6 MB
    call = {"type": "tool_use", "id": "mem-big", "name": name,
            "input": {"arg_0": payload, "arg_1": len(payload), "arg_2": 1.0,
                      "arg_3": True, "arg_4": {"n": len(payload)}}}
    await toolkit.dispatch(call)
    state["n"] = len(payload)


# ── S13: remotion video render - Node + esbuild + render-Chromium (peak) ─
# Self-contained ONLY when a remotion composer dir is provided via
# MEL_MEM_REMOTION_DIR; the OSS bench skips it otherwise (Node + a remotion
# project are not Python-bench deps). The render runs as a child process
# tree, so the sampler captures the Node + Chromium footprint as the peak.

def _warm_render() -> None:
    _warm_runtime()


async def _cycle_render(state: dict) -> None:
    import asyncio
    from pathlib import Path
    composer = os.environ.get("MEL_MEM_REMOTION_DIR")
    if not composer or state.get("done"):
        await asyncio.sleep(0.05)
        return
    demos = list(Path(composer, "public", "demo-props").glob("*.json"))
    if not demos:
        state["done"] = True
        await asyncio.sleep(0.05)
        return
    npx = "npx.cmd" if os.name == "nt" else "npx"
    out = os.path.join(tempfile.gettempdir(), "mem_render_probe.mp4")
    try:
        p = await asyncio.create_subprocess_exec(
            npx, "remotion", "render", "src/index.tsx", "Explainer", out,
            "--props", str(demos[0]), "--codec", "h264", "--frames", "0-20",
            "--concurrency", "1",
            cwd=composer, stdout=asyncio.subprocess.DEVNULL, stderr=asyncio.subprocess.DEVNULL)
        await p.wait()
    except Exception as e:  # noqa: BLE001
        print(f"[render] {type(e).__name__}: {e}", file=sys.stderr)
    state["done"] = True


# ── S_conc: concurrency validation - N pipelines at once (linearity) ─────
# Spawns N-1 sibling baseline pipelines; the sampler measures self + the
# children = N concurrent pipelines, so the aggregate footprint validates
# that N pipelines cost ~N x a single one (the linear capacity fit). N is
# set via MEL_MEM_CONC_N (default 4).

_CONC_CHILD = (
    "import melaya_bench_framework, time, os\n"
    "from melaya_bench_framework import Toolkit, async_tool\n"
    "tk = Toolkit(); tk.register_tool_function(async_tool, func_name='t')\n"
    "time.sleep(float(os.environ.get('MEL_MEM_CONC_HOLD', '25')))\n"
)


def _warm_conc() -> None:
    _warm_runtime()


async def _cycle_conc(state: dict) -> None:
    import asyncio
    import time
    if "procs" not in state:
        n = max(1, int(os.environ.get("MEL_MEM_CONC_N", "4")))
        os.environ["MEL_MEM_CONC_HOLD"] = "25"
        state["procs"] = [_track(subprocess.Popen([sys.executable, "-c", _CONC_CHILD]))
                          for _ in range(n - 1)]
        state["n"] = n
        time.sleep(3.0)  # let the siblings warm + load their baseline
    await asyncio.sleep(0.05)


SCENARIOS: dict[str, Scenario] = {
    "s0": Scenario(
        id="s0",
        capability="idle",
        placement="none",
        description="Runner idle floor after heavy-module warm-up; teardown reap + drift gate.",
        warm=_warm_runtime,
        cycle=_cycle_idle,
        n_cycles=20,
        components=["orchestration"],
    ),
    "s1": Scenario(
        id="s1",
        capability="plain",
        placement="agnostic",
        description="LLM-agnostic orchestration baseline on the real Toolkit.dispatch hot path; no model, no side-effecting tools.",
        warm=_warm_baseline,
        cycle=_cycle_baseline,
        n_cycles=20,
        components=["orchestration", "registry_after_activation"],
    ),
    "s2": Scenario(
        id="s2", capability="rag10k", placement="agnostic",
        description="RAG (Qdrant) - resident in-runner 10k x 768 index; embedder remote, not counted here.",
        warm=_warm_qdrant, cycle=_cycle_rag_qdrant, n_cycles=10,
        components=["orchestration", "qdrant_index"],
    ),
    "s3a": Scenario(
        id="s3a", capability="webfast", placement="agnostic",
        description="web_search fast path (curl_cffi TLS client) - no browser.",
        warm=_warm_curl, cycle=_cycle_websearch_fast, n_cycles=10,
        components=["orchestration", "native_unattributed"],
    ),
    "s3b": Scenario(
        id="s3b", capability="webbrowser", placement="agnostic",
        description="web_search stealth rescue - Scrapling -> Chromium headless-shell tree (bimodal vs s3a).",
        warm=_warm_browser, cycle=_cycle_websearch_browser, n_cycles=6,
        components=["orchestration", "browser"],
    ),
    "s_aiml": Scenario(
        id="s_aiml", capability="aiml", placement="agnostic",
        description="aiml HF tool - transformers + torch loaded IN the runner (the one in-process model load).",
        warm=_warm_aiml, cycle=_cycle_aiml, n_cycles=6,
        components=["orchestration", "aiml_inproc_torch", "native_unattributed"],
    ),
    "s2b": Scenario(
        id="s2b", capability="rag100k", placement="agnostic",
        description="RAG (Qdrant) 100k - resident in-runner 100k x 768 index + doc-ingest transient (chunk buffer + embed-shaped batch); embedder remote.",
        warm=_warm_rag100k, cycle=_cycle_rag_100k, n_cycles=6,
        components=["orchestration", "qdrant_index", "doc_ingest_transient"],
    ),
    "s11": Scenario(
        id="s11", capability="wss", placement="agnostic",
        description="WSS ingress watcher - bounded in-runner ring buffers (the leak gate asserts steady reclaim).",
        warm=_warm_wss, cycle=_cycle_wss, n_cycles=20,
        components=["orchestration", "ws_ring_buffers"],
    ),
    "s12": Scenario(
        id="s12", capability="coderepl", placement="agnostic",
        description="python_repl code-exec - child interpreter + sci-stack (numpy/pandas/scipy) captured as a recursive child; peak.",
        warm=_warm_repl, cycle=_cycle_coderepl, n_cycles=8,
        components=["orchestration", "coderepl_child"],
    ),
    "s15": Scenario(
        id="s15", capability="mcp", placement="agnostic",
        description="MCP stdio server - one resident FastMCP child process per server, attributed as a child of the subject.",
        warm=_warm_mcp, cycle=_cycle_mcp, n_cycles=12,
        components=["orchestration", "mcp_child"],
    ),
    "s16": Scenario(
        id="s16", capability="compaction", placement="agnostic",
        description="Long-context compaction - saw-tooth history grow/compact/drop; transient peak with steady reclaim proof.",
        warm=_warm_compaction, cycle=_cycle_compaction, n_cycles=20,
        components=["orchestration", "history_sawtooth"],
    ),
    "s17": Scenario(
        id="s17", capability="streaming", placement="agnostic",
        description="Streaming assembly - SSE/token accumulation into a buffer then assemble; transient.",
        warm=_warm_streaming, cycle=_cycle_streaming, n_cycles=20,
        components=["orchestration", "stream_buffer"],
    ),
    "s18": Scenario(
        id="s18", capability="tooloutput", placement="agnostic",
        description="Huge tool output - multi-MB payload through the real dispatch path; transient peak, must not accumulate.",
        warm=_warm_tooloutput, cycle=_cycle_tooloutput, n_cycles=12,
        components=["orchestration", "tool_output_transient"],
    ),
    "s13": Scenario(
        id="s13", capability="render", placement="agnostic",
        description="remotion video render - Node + esbuild bundler + Rust compositor + render-Chromium tree (peak). Requires MEL_MEM_REMOTION_DIR.",
        warm=_warm_render, cycle=_cycle_render, n_cycles=1,
        components=["orchestration", "render_node_chromium"],
    ),
    "s_conc": Scenario(
        id="s_conc", capability="conc", placement="agnostic",
        description="Concurrency validation - N pipelines at once (MEL_MEM_CONC_N); aggregate footprint gates the linear capacity fit.",
        warm=_warm_conc, cycle=_cycle_conc, n_cycles=10,
        components=["orchestration", "concurrency_aggregate"],
    ),
}


def get(scenario_id: str) -> Scenario:
    sid = scenario_id.lower().strip()
    if sid not in SCENARIOS:
        raise SystemExit(
            f"[mem] unknown scenario {sid!r}. Available: {', '.join(SCENARIOS)}"
        )
    return SCENARIOS[sid]
