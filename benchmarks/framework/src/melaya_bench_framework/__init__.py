"""melaya_bench_framework — minimal shim of the Melaya pipeline runner.

This package is the **public, OSS-friendly surface** behind the
benchmarks in `benches/`. It reproduces — without forking — the
hot-path shapes that the production runtime exercises in production, so a
reader can run `pip install -e . && pytest` from a fresh clone with no
melaya-platform dependency.

Mapping (runtime → bench shim):

| Runtime file                               | What the runtime does                                                                                          | What this shim does                                                                                              |
| ------------------------------------------ | -------------------------------------------------------------------------------------------------------------- | ---------------------------------------------------------------------------------------------------------------- |
| `the runtime's toolkit call path` | dict-lookup tool by name → kwargs merge (preset + tool_call.input) → invoke async function → wrap ToolResponse | `Toolkit.dispatch` — same lookup, same merge, same invocation, same wrap. No middleware chain, no group gating. |
| `the production registry builder`    | Walk every `the production tool modules` (~200 modules), introspect every `async def`, register, attach postprocess.    | `Registry.boot` — walk N synthetic modules, introspect, register. Tuneable N matches prod's tool count.         |
| pipeline runner (per-step transition)      | `await tool_n(); plan_next(); await tool_n+1();` graph walk + variable binding.                                | `LinearPipeline.run`, `ParallelPipeline.run` — same `await` cadence, mocks every tool to 0-ms return.            |
| `the production RAG retrieve path`     | embed(query) → ANN search via the production vector store client → hydrate top-k chunks                                             | `RagIndex.retrieve` — embed via random-projection or MiniLM → numpy brute-force kNN → in-memory chunk hydration. |
| `the production model wrapper` | prompt assembly → message-history pack → provider HTTP → unpack response → cost-track                          | `ModelWrapper.call` — same assembly + pack + unpack, provider boundary is a 0-ms `MockProvider`.                 |

What this shim does **NOT** reproduce:

1. Network anywhere. No real LLM API calls, no the production vector store, no DB.
2. Middleware chains. The real Toolkit has a chain of optional
   middlewares (HITL gate, audit log, validation, postprocess). They're
   measurable independently in production telemetry; this bench is
   pure runner overhead so we omit them. The bench's `tool_dispatch`
   number is the floor of what the runner can do — production adds the
   middleware delta on top.
3. The tool's own work. Every benched tool returns `ToolResponse` in
   ~10 ns. This isolates RUNNER overhead from tool I/O.

Why those omissions are honest:

The `BENCH_SHAPE_VERSION` constant below pins the shim's contract.
If the runtime grows a fundamentally new hot path (e.g. JIT-compiled
graph walks), the version bumps and any pre-bump contributor
`summary.json` is flagged as referring to the old shape.

Anyone with platform access can verify equivalence by running the
production registry boot + dispatching N synthetic tools through the
real Toolkit, then comparing `summary.json` for `tool_dispatch` and
`registry_boot` here vs in the live runner. Bench and runtime should
agree within ~15-25 % (runtime adds the middleware chain delta, which
is a separately-measured production telemetry number).
"""

from __future__ import annotations

# Pin the contract. Bump when the shim's data shape diverges from the
# runtime — every committed summary.json should record this version.
BENCH_SHAPE_VERSION: str = "0.1.x-shape"

from .toolkit import (
    Toolkit,
    ToolResponse,
    ToolUseBlock,
    sync_tool,
    async_tool,
)
from .pipeline import LinearPipeline, ParallelPipeline, PipelineStep
from .registry import Registry, synthesize_tool_modules
from .rag import RagIndex, RandomProjectionEmbedder, make_synthetic_corpus
from .model_wrapper import ModelWrapper, MockProvider, build_message_history
from .hitl_gate import HitlGate
from .context import ContextAssembler
from .session_memory import SessionMemory
from .cost import CostTracker
from .tracing import Tracer
from .crew import Crew, Persona
from .security import InjectionGuard

__all__ = [
    "BENCH_SHAPE_VERSION",
    # Toolkit / dispatch
    "Toolkit",
    "ToolResponse",
    "ToolUseBlock",
    "sync_tool",
    "async_tool",
    # Pipeline orchestration
    "LinearPipeline",
    "ParallelPipeline",
    "PipelineStep",
    # Registry boot
    "Registry",
    "synthesize_tool_modules",
    # RAG
    "RagIndex",
    "RandomProjectionEmbedder",
    "make_synthetic_corpus",
    # Model wrapper
    "ModelWrapper",
    "MockProvider",
    "build_message_history",
    # HITL enforcement gate
    "HitlGate",
    # coverage: context / memory / cost / tracing / crews
    "ContextAssembler",
    "SessionMemory",
    "CostTracker",
    "Tracer",
    "Crew",
    "Persona",
    "InjectionGuard",
]
