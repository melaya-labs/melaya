# Framework bench - reproducible micro-benchmarks for the Melaya Python agentic runner

This package exists for one reason: **anyone can clone the public
Melaya repo and re-measure the headline numbers we publish for the
Python agentic runner** - tool dispatch, pipeline orchestration,
registry boot, RAG retrieval, the model-wrapper, the HITL enforcement
gate, context assembly, session memory, cost + token accounting,
tracing, prompt-injection scanning, and crew orchestration.

Companion to [`../engine/`](../engine/) (the Rust trading-engine
micro-bench). Engine vs framework is a load-bearing distinction:

| Bench | Crate / pkg | Numbers in… | Measures |
|---|---|---|---|
| **Engine** | `melaya-bench-engine` (Rust) | nanoseconds (300 ns p50) | Rust trading-engine state-cache writes |
| **Framework** (this) | `melaya-bench-framework` (Python) | microseconds + low ms | Python agentic-runner overhead |

Numbers on different scales because they're different layers. The
agentic runner orchestrates tool calls + RAG + LLM turns; the trading
engine ingests websocket frames into a state cache. Both are real,
both are measured the same way, neither is a substitute for the other.

---

## 1. What this measures (and what it doesn't)

> [!important] Twelve benches (sixteen measured metrics), each one operation.
> Every bench in this suite measures the cost of ONE runner-side
> operation, with everything that isn't the operation mocked to
> zero-cost (no real LLM API calls, no real vector store, no real network).
> The numbers are pure RUNNER overhead - production end-to-end
> latency adds the tool's own work + provider HTTP RTT + middleware
> deltas on top.

| Bench | Operation measured | Mocked away |
|---|---|---|
| `tool_dispatch_{0,5,20}arg` | `Toolkit.dispatch` name lookup + kwargs merge + await + wrap | Tool's own work (returns in ~10 ns); middleware chain; postprocess |
| `pipeline_orchestration_{linear,parallel}` | Step-to-step transition cost in a 10-step pipeline | Every tool + model call is a 0-ms mock |
| `registry_boot` | Walk 250 synthetic tool modules → introspect → register | Python `import` time (production cold-boot adds 3-15 s on top) |
| `rag_retrieval_{10k,100k}` | embed(query) + brute-force kNN + chunk hydration | embedded ANN index (we use numpy brute force - a production ANN index is 1.5-3× faster) |
| `model_wrapper_overhead` | Pack history + tools spec + (mock) provider + unpack + cost-track | Provider HTTP RTT (200 ms - 5 s in production, lives on /benchmarks/ai-providers) |
| `hitl_gate_overhead` | Per-write enforcement gate: sidecar read + write floor + per-tenant quota + cost cap | The human approval *wait* (human-bound, see HITL round-trip below) |
| `context_assembly` | Build the static per-turn context block: system prompt + granted knowledge docs + tool schemas | The doc fetch (pre-loaded in memory) |
| `session_memory` | Cross-run working memory: serialize + load + restore a 50-turn crew memory | Real store I/O (in-memory round-trip) |
| `cost_tracking` | Per-call token/USD accounting against a price table + a running aggregate | Nothing external (pure arithmetic on the hot path) |
| `tracing_overhead` | Per-span observability tax: open + stamp gen_ai/cost/latency attrs + close + export | The exporter network flush (mocked sink) |
| `crew_orchestration` | 4-persona crew hand-off (macro -> technical -> risk -> execution) with a risk veto that can halt mid-run | Each persona's model call (0-ms mock) |
| `prompt_injection_scan` | Per-input injection / jailbreak / exfiltration scan over untrusted content before it reaches the model | Nothing - pure scan over the input |

The remaining conceptual metric - **HITL round-trip** (the human *wait*,
distinct from the enforcement gate above) - is intentionally **not**
benched here because it's human-bound (minutes-to-hours,
dominated by operator attention, not compute). See
[`results/hitl_round_trip/methodology_only.json`](./results/hitl_round_trip/methodology_only.json)
for how we plan to publish it from 30 days of production telemetry.

---

## 2. Three-command reproduction (verified)

```bash
# 1. Clone + cd. No melaya-platform clone required.
git clone https://github.com/melaya-labs/melaya
cd melaya/benchmarks/framework

# 2. Install the bench package (editable, so the same `pytest` works
#    inside and outside a virtualenv).
pip install -e .

# 3. Run the suite. Total wall time <5 min on modern HW.
pytest benches/ -s
```

That's it. The package has **no path dependencies** on
melaya-platform and no external services. Results land in
`./results/<metric>/{summary.json, *_us.csv}`.

### Why no `melaya-platform` clone?

The previous version of these benches imported directly from
the production runtime. That broke for every external user because
`melaya-platform` is private. To make the bench universally
reproducible we ship **a self-contained reproduction of the runner's
hot-path shapes** in `src/melaya_bench_framework/`. The mapping
(which runtime lines reproduce as which shim lines, and which are
deliberately elided as no-ops on the steady-state hot path) lives in
the package docstring at
[`src/melaya_bench_framework/__init__.py`](./src/melaya_bench_framework/__init__.py).

Anyone with platform access can verify equivalence by running the
production registry boot + dispatching N synthetic tools through the
real Toolkit, then comparing `summary.json` for `tool_dispatch` and
`registry_boot` here vs in the live runner. Bench vs runtime should
agree within ~15-25 % (the runtime adds a middleware delta on top,
measured independently in production telemetry).

The long-term fix is publishing `melaya-runtime-core` to PyPI. When
that ships, this package switches the shim for a direct dependency
and `src/melaya_bench_framework/__init__.py` shrinks to a 5-line
re-export.

---

## 3. Pinned & opinionated runners

`pytest benches/` works on any machine, but several easy-to-forget
knobs swing the headline numbers by 2-5×. The scripts below set them
for you:

| Platform | Command | What it does |
|---|---|---|
| Linux / macOS | `./scripts/bench.sh` | Pins to core 3 via `taskset`, attempts `performance` governor (Linux), prints turbo state. |
| Windows | `.\scripts\bench.ps1` | Switches power plan to *High performance* for the duration, pins child process via `ProcessorAffinity`, restores the plan after. |
| Containerized (gold standard) | `docker build -t melaya-framework-bench . && docker run --rm --cpuset-cpus 3 -v "$PWD/results:/bench/results" melaya-framework-bench` | Identical Debian userland + pinned python 3.12. Strips most host noise. |

All three drop their summary.json + CSV into the same `./results/`
directory.

---

## 4. Hardware-tier expectations

The numbers are not what you'll see on every laptop. The variables
that matter, in rough order of impact:

1. **CPython version** - 3.11+ ships the specializing adaptive
   interpreter; tool-dispatch is ~30 % faster than 3.10. Use 3.12+.
2. **CPU pinning + governor** - un-pinned threads migrate between
   cores every few ms, blowing the L1 i-cache. `performance` governor
   on Linux gets you another ~15 % on tail latency.
3. **Turbo + thermal headroom** - a laptop on battery throttles
   within seconds. Plug in.
4. **Background noise** - Chrome, antivirus, Spotlight indexing add
   p95/p99 jitter even when they don't move p50.

Expected ranges, by tier. **A number outside the range for your tier
means you found a methodology bug or a real regression - not a broken
claim.** Bring it up in
[GitHub Discussions](https://github.com/melaya-labs/melaya/discussions)
and paste your `results/<metric>/summary.json`.

### Framework-tier hardware (all microsecond-to-low-millisecond)

| Tier | Hardware example | OS / config | tool_dispatch p50 | pipeline_step p50 | registry_boot median | rag_retrieval_10k p50 |
|---|---|---|---|---|---|---|
| **A. Production (reference)** | Xeon Plat 8369B (Ice Lake-SP) | Ubuntu 22.04, pinned core, perf gov, py3.12 | _awaiting_ | _awaiting_ | _awaiting_ | _awaiting_ |
| **B. Modern Linux server** | Xeon Gold 6438 / EPYC 9354 | Ubuntu 22/24, perf gov, py3.12 | 3-8 µs | 10-25 µs | 1-3 s | 0.5-2 ms |
| **C. Apple Silicon (M2/M3/M4)** | MacBook Air/Pro, plugged in | macOS 14+, native arm64, py3.12 | 2-6 µs | 8-20 µs | 0.8-2 s | 0.4-1.5 ms |
| **D. Modern desktop (measured \*)** | i9-13900H (Raptor Lake-H) | Win11, py3.12.4, unpinned | 0.6-1.0 µs | 0.22 µs | 4.4 ms \*\* | 0.28 ms |

Tiers A-C are estimates pending contributor PRs; **tier-D is measured**
(committed under `results/contributed/tier-d-i9-13900h/`). Tier-A will be
filled from production telemetry once the on-prod profiling job ships.
\* unpinned dev box, so single-thread turbo can beat a pinned server core.
\*\* `registry_boot` is **register-only** - it excludes the ~3-15 s of
Python `import` time that dominates a real cold boot (see the metric note).
**First contributor to PR a `summary.json` for a tier replaces its
estimate with measured data.** See
[`results/README.md`](./results/README.md) for the submission format.

### Sub-tier (not advertised, expected to drift higher)

The setups below CAN run the benches but the hardware itself becomes
the bottleneck - the runner is doing the same work, the machine is
just slower at executing it. We don't quote ranges here because the
variance is dominated by external factors:

- **Laptop on battery / thermal-throttled** (i7-1260P, Ryzen 7 7840U
  on Win11 *Balanced*). Plug in + High Performance, or use the .ps1
  runner, before comparing.
- **Shared-tenant cloud VM** (AWS `t3.medium`, GCP `e2-medium`).
  Tiny burstable instances share a physical core. Use a dedicated
  host or compare via the Docker runner.
- **Dev container / WSL2 on Windows**. Adds 15-40 % p50 / 50-150 %
  p99 vs the host. Run native or via Docker on Linux for an
  apples-to-apples number.

---

## 5. Reading `summary.json`

After a run, every `./results/<metric>/summary.json` looks like this
(sample numbers from a notional run on tier-D hardware):

```json
{
  "n": 10000,
  "min_us": 1.8,
  "p50_us": 5.4,
  "p90_us": 8.1,
  "p95_us": 9.7,
  "p99_us": 14.3,
  "p999_us": 28.5,
  "max_us": 87.2,
  "metric": "tool_dispatch_5arg",
  "shim_call": "Toolkit.dispatch (5-arg)",
  "bench_shape_version": "0.1.x-shape",
  "headline_samples": 10000,
  "run_at_unix_ms": 1750000000000,
  "run_at_iso": "2026-06-20T14:30:00+00:00",
  "env": {
    "cpu_model": "Intel(R) Core(TM) i7-12700K CPU @ 3.60GHz",
    "logical_cores": 20,
    "os_kernel": "Windows 11 (10.0.22631)",
    "python_version": "3.12.4",
    "cpu_governor": null,
    "turbo_state": null,
    "arch": "AMD64",
    "os": "win32"
  },
  "what_this_is": "...",
  "what_this_is_not": "...",
  "reference_hardware": "...",
  "extra": { "shape": "5arg", "n_args": 5, "iterations": 10000 }
}
```

Each metric also writes `./results/<metric>/<metric>_us.csv` - one
row per iteration. Pipe it into your plotting tool of choice if you
want a histogram, or attach to a PR if a maintainer asks for raw
samples.

---

## 6. "My number is way off" - troubleshooting

| Symptom | Likely cause |
|---|---|
| **tool_dispatch p50 > 50 µs on any modern HW** | You're on CPython 3.10 or earlier, or a debug build. Upgrade to 3.12+, or check `python -c 'import sys; print(sys.gettrace())'` returns None (a profiler/tracer attached doubles dispatch cost). |
| **registry_boot > 10 s for 250 tools** | You're on Windows Defender real-time scan, or a slow filesystem. Check Defender exclusions on the bench dir. Docker runner strips most of this. |
| **rag_retrieval_100k p50 > 50 ms** | numpy is not using a BLAS backend. Run `python -c 'import numpy; numpy.show_config()'` - you want OpenBLAS or MKL. `pip install numpy --no-cache --force-reinstall` typically picks them up. |
| **Wild p99 / p999 (> 50× p50)** | Co-tenant or background work - antivirus scan, indexing, browser tab. Re-run after closing those. The Docker runner with `--cpuset-cpus` strips most of this. |
| **p50 looks correct on prod, dev box reads 2-3× higher** | Expected - see the tier table. Pinning + governor + turbo-off is what gets prod to the reference numbers. |
| **Numbers shift run-to-run by > 30 %** | `time.perf_counter_ns` resolution on your platform is the floor. Linux x86 vDSO clock_gettime is ~25 ns; Windows QueryPerformanceCounter is ~25-100 ns. For a 3-5 µs `tool_dispatch` target the clock noise is ~1-3 % of signal - manageable. For sub-µs measurements you'd need `__rdtsc`-class instrumentation; for our targets the noise floor is comfortable. |

If none of those match, open a
[Discussion](https://github.com/melaya-labs/melaya/discussions) with
your `results/*/summary.json` attached. We triage hardware-tier
regressions in the open.

---

## 7. Submitting your hardware tier

We want the tier table above to be **measured data, not estimates**.
First contributor to run the bench on a given tier wins:

1. Run the bench (any of the three runners).
2. Copy each metric's `results/<metric>/summary.json` into a PR that
   adds them to [`results/contributed/`](./results/) under
   `tier-{A..G}-{cpu-slug}/`. Example:
   ```
   results/contributed/tier-c-m2-air/
     tool_dispatch_0arg.json
     tool_dispatch_5arg.json
     tool_dispatch_20arg.json
     pipeline_orchestration_linear.json
     pipeline_orchestration_parallel.json
     registry_boot.json
     rag_retrieval_10k.json
     rag_retrieval_100k.json
     model_wrapper_overhead.json
     hitl_gate_overhead.json
     context_assembly.json
     session_memory.json
     cost_tracking.json
     tracing_overhead.json
     crew_orchestration.json
     prompt_injection_scan.json
   ```
3. Open a PR titled `bench: tier {X} framework measured on {short hardware name}`.
   The maintainer updates the README table on the next release cut.

Full submission format is in [`results/README.md`](./results/README.md).

---

## 8. File layout

| Path | Purpose |
|---|---|
| [`pyproject.toml`](./pyproject.toml) | Standalone package. **No path dependency.** |
| [`src/melaya_bench_framework/`](./src/melaya_bench_framework/) | Shim package - faithful repro of the production runtime hot paths. Package docstring documents the line-by-line correspondence. |
| [`benches/bench_tool_dispatch.py`](./benches/bench_tool_dispatch.py) | The 3-shape (0/5/20 arg) dispatch bench. |
| [`benches/bench_pipeline_orchestration.py`](./benches/bench_pipeline_orchestration.py) | 10-step linear + parallel pipelines, per-step transition cost. |
| [`benches/bench_registry_boot.py`](./benches/bench_registry_boot.py) | 250-synthetic-tool registry walk + register, 10 cold boots. |
| [`benches/bench_rag_retrieval.py`](./benches/bench_rag_retrieval.py) | Brute-force ANN over 10k + 100k chunks at 384 dims. |
| [`benches/bench_model_wrapper_overhead.py`](./benches/bench_model_wrapper_overhead.py) | One LLM turn through `ModelWrapper.call` with a 0-ms mock provider. |
| [`benches/bench_hitl_gate_overhead.py`](./benches/bench_hitl_gate_overhead.py) | Per-write HITL enforcement gate: sidecar read + write floor + per-tenant quota + cost cap. |
| [`benches/bench_context_assembly.py`](./benches/bench_context_assembly.py) | Static per-turn context block assembly (prompt + docs + tool schemas). |
| [`benches/bench_session_memory.py`](./benches/bench_session_memory.py) | Cross-run working-memory serialize + restore over a 50-turn crew memory. |
| [`benches/bench_cost_tracking.py`](./benches/bench_cost_tracking.py) | Per-call token/USD accounting + running aggregate. |
| [`benches/bench_tracing_overhead.py`](./benches/bench_tracing_overhead.py) | Per-span observability open/stamp/close/export tax. |
| [`benches/bench_crew_orchestration.py`](./benches/bench_crew_orchestration.py) | 4-persona crew hand-off with a mid-run risk veto. |
| [`benches/bench_prompt_injection.py`](./benches/bench_prompt_injection.py) | Per-input prompt-injection / jailbreak / exfiltration scan. |
| [`benches/conftest.py`](./benches/conftest.py) | Shared fixtures + the summary.json writer. |
| [`scripts/bench.sh`](./scripts/bench.sh) | POSIX runner (Linux + macOS) with pinning + governor hints. |
| [`scripts/bench.ps1`](./scripts/bench.ps1) | Windows runner with affinity + power-plan switch. |
| [`Dockerfile`](./Dockerfile) | Containerised runner (`python:3.12-slim`). Strips host noise. |
| [`results/`](./results/) | Output directory. Includes `README.md` for the contributor submission flow + `hitl_round_trip/methodology_only.json` for the human-bound metric. |

---

## 9. Licence

Apache-2.0, same as the rest of this repo.
