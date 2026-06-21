# Changelog - Melaya Benchmarks

Reproducible performance benchmarks for the Melaya platform. Two suites,
one per layer of the stack, each self-contained (clone, build, run - no
access to the private platform required). This document follows
[Semantic Versioning](https://semver.org).

| Suite | Path | Language | Measures | Unit |
|---|---|---|---|---|
| **Engine** | [`engine/`](./engine) | Rust | trading-engine state-cache writes | nanoseconds |
| **Framework** | [`framework/`](./framework) | Python | agentic-runner overhead | microseconds - low ms |

Both publish numbers that anyone can re-measure on their own hardware,
contribute back per hardware tier, and that the public benchmark pages
([melaya.org/benchmarks](https://melaya.org/benchmarks)) render as
machine-readable Datasets.

---

## [0.1.0] - Preview

Initial public release of both benchmark suites.

### Engine suite (`engine/`)

A criterion micro-bench over the trading engine's ticker hot path.

- **`state_ticker_ns`** - the cost of writing one ticker frame into the
  engine's in-memory state cache (hash the exchange + symbol keys, look up
  the snapshot slot, stamp in bid/ask/last + receive timestamp). Pure
  in-process Rust: no locks, no network, no syscalls beyond a single
  monotonic-clock read.
- **Production reference:** ~**310 ns p50** (89,033 live samples, Ice
  Lake-SP, pinned core). The crate ships a faithful, self-contained
  reproduction of the engine's hot-path data shape with the same release
  profile (opt-level 3, fat LTO, one codegen unit), so an external user
  can confirm the order of magnitude on their own box.
- Three-command reproduction (`rustup`, `git clone`, `cargo bench`), with
  pinned runners (`scripts/bench.sh` / `.ps1`) and a Docker runner that
  strips host noise.
- Carefully distinguishes `state_ticker_ns` (this bench) from
  end-to-end, dispatch, and per-venue lag numbers so the headline cannot
  be misread.

### Framework suite (`framework/`)

`pytest` micro-benches over the Python agentic runner. Each measures the
cost of ONE runner-side operation with everything that isn't the
operation mocked to zero cost. Self-contained shims reproduce the
production hot-path shapes, so the suite installs and runs in minutes
with no platform dependency.

**16 measured metrics:**

| Metric | What it measures |
|---|---|
| `tool_dispatch_{0,5,20}arg` | Runner-side cost of dispatching a scoped tool (lookup + kwargs merge + await + wrap), across input widths |
| `pipeline_orchestration_{linear,parallel}` | Per-step transition cost in a 10-step pipeline (sequential and `asyncio.gather` fan-out) |
| `registry_boot` | Walk + introspect + register 250 synthetic tools (register-only; excludes Python import time) |
| `rag_retrieval_{10k,100k}` | Embed + brute-force kNN + chunk hydration over a 10k / 100k chunk in-memory index |
| `model_wrapper_overhead` | Per-LLM-turn runner overhead (history pack + tools spec + unpack + cost-track), provider HTTP mocked |
| `hitl_gate_overhead` | Per-write human-in-the-loop enforcement gate (sidecar-state read + per-cycle write cap + per-tenant quota + cost cap) |
| `context_assembly` | Build the static per-turn context block (system prompt + granted knowledge docs + tool schemas) |
| `session_memory` | Cross-run working-memory persistence (serialize to store + load + restore a 50-turn crew memory) |
| `cost_tracking` | Per-call token/USD accounting against a price table + running aggregate (enables per-tenant billing + spend caps) |
| `tracing_overhead` | Per-span observability tax (open + stamp gen_ai/cost/latency attrs + close + export) |
| `crew_orchestration` | 4-persona crew (macro -> technical -> risk -> execution) hand-off with a risk veto that can halt mid-run |
| `prompt_injection_scan` | Per-input prompt-injection / jailbreak / exfiltration scan over untrusted content (RAG docs, tool outputs) before it reaches the model |

Plus two honestly-labelled non-latency rows: `hitl_round_trip`
(methodology-only; human-bound, awaiting production telemetry) and
`concurrent_agent_executions` (a deployment config knob, not a
measurement).

Every metric reproduces a real runner capability. Each writes a
`summary.json` with min/p50/p90/p95/p99/p999/max in microseconds plus an
environment block (CPU, cores, OS, Python version).

### Methodology + conventions (both suites)

- **Self-contained:** no private-platform clone, no external services, no
  LLM API calls. Heavy optional paths (real embedder, browser tooling)
  are mocked or opt-in so the suites run on air-gapped CI.
- **Honest by construction:** every bench documents what it is *not*
  (mocked-away cost), the page badges each row `measured` /
  `methodology_only` / `config` / `in_progress`, and no number is
  fabricated - a missing metric renders as `in progress`, never a
  placeholder value.
- **Hardware tiers:** per-tier measured submissions live in
  `results/contributed/<tier-slug>/`; per-run output
  (`results/<metric>/summary.json`, CSVs) is local-only and gitignored.
  The first contributor to PR a tier becomes its citable reference.
- **Page wiring:** a build-time generator turns the committed tier files
  into a compact JSON the benchmark pages import, emitting per-metric
  schema.org `Dataset` blocks (percentiles + hardware + Python
  provenance) so the numbers are citable by search and AI crawlers.
- **License:** Apache-2.0.

### Notes

- All benchmark code is a faithful *reproduction* of production hot-path
  shapes, not the production source. Anyone with platform access can
  verify equivalence on the same hardware; the suites exist so an
  open-source user can confirm the order of magnitude independently.
- Reference numbers (engine 310 ns; framework tier-D on an i9-13900H) are
  measured, not estimated. Other tiers start as estimates until a
  contributor submits measured data.
