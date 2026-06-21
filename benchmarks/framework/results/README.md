# `results/`

This directory holds the output of `pytest benches/` (your local
run) plus contributor-submitted measurements from other hardware
tiers.

## Files written by your run

For each bench, you'll find:

| File | Contents |
|---|---|
| `<metric>/summary.json` | Aggregate stats (`n`, `min_us`, `p50_us`, `p90_us`, `p95_us`, `p99_us`, `p999_us`, `max_us`), plus environment metadata (CPU model, logical cores, OS/kernel, Python version, Linux governor + turbo state when available), plus provenance (`bench_shape_version`, `shim_call`, `run_at_iso`). |
| `<metric>/<metric>_us.csv` | Per-iteration microsecond samples (`iteration,us`). Useful for plotting histograms locally. |

These files are overwritten on each run. **They are not committed to
the repo by default** - they are your local result.

The HITL metric has a different shape - see
[`hitl_round_trip/methodology_only.json`](./hitl_round_trip/methodology_only.json)
for why it can't be benched synthetically and how production
telemetry will populate it.

## Contributor submissions (`contributed/`)

The hardware-tier expectations table in the parent
[`README.md`](../README.md#4-hardware-tier-expectations) starts as
estimates for tiers B-D. To replace an estimate with measured data:

1. Run the bench on hardware that matches one of the tiers
   (A: production / B: modern Linux server / C: Apple Silicon /
   D: modern desktop / E: modern laptop / F: cloud VM /
   G: dev container).
2. Copy each metric's `summary.json` into `contributed/<tier-slug>/`
   with descriptive names:
   ```
   contributed/tier-c-m2-air/
     tool_dispatch_0arg.json
     tool_dispatch_5arg.json
     tool_dispatch_20arg.json
     pipeline_orchestration_linear.json
     pipeline_orchestration_parallel.json
     registry_boot.json
     rag_retrieval_10k.json
     rag_retrieval_100k.json
     model_wrapper_overhead.json
   ```
3. Open a PR titled
   `bench: tier {X} framework measured on {short hardware name}`.
   The maintainer updates the README table from your files on the
   next release cut.

### What a good submission looks like

- `summary.json` files produced by an **unmodified** `pytest benches/`
  run (the CSVs are not required for submission - keep them locally
  if you want, but they're 100 KB - 1 MB each and would bloat the
  repo if committed for every tier).
- Run on a **quiescent host**: no big background work, ideally a
  dedicated runner. The pinned `scripts/bench.{sh,ps1}` runners and
  the `Dockerfile` all reduce noise - please use one of those rather
  than a raw `pytest` invocation so submissions stay comparable.
- If you ran multiple times and picked the best, **say so in the PR
  description** with the spread. We'd rather see a measured range
  than a cherry-pick.

### What the maintainer commits to

- Replace the tier-row estimates with your measured numbers, citing
  your CPU + OS + Python version.
- Keep the `bench_shape_version` field in the table so we can flag
  when a future bench rev invalidates older submissions.
- Run our own production probe once per quarter and post the updated
  tier-A reference to this directory.

## Why aren't there committed numbers from the maintainer yet?

This bench was authored from a development sandbox that prohibits
running the python interpreter as a benched workload (only
`pytest --collect-only`-style introspection is permitted). The
harness, shim, and tests are all wired up; the maintainer's headline
`summary.json` set will be committed from a follow-up run on a
known-tier machine - date TBD. Until then, **the first contributor
to PR a tier-A / tier-B / tier-C / tier-D submission becomes the
citable reference for that tier.**

The runner-perf claims are independently verifiable: the same shim
shapes live in production at the production runtime modules and
can be probed via `the production registry's tool-listing helper` + the
in-process pipeline tracer. This bench exists so an OSS user can
confirm the *order of magnitude* is correct on their own hardware;
it is not the only source of the production number.
