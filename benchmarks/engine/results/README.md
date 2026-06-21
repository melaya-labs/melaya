# `results/`

This directory holds the output of a `cargo bench --bench state_ticker`
run plus contributor-submitted measurements from other hardware tiers.

## Files written by your run

| File | Contents |
|---|---|
| `state_ticker_ns.csv` | Per-iteration nanosecond samples (100,000 rows: `iteration,ns`). |
| `summary.json` | Aggregate stats (`n`, `p50_ns`, `p95_ns`, `p99_ns`, `p999_ns`, `max_ns`), plus environment metadata (CPU model, logical cores, OS/kernel, rustc version, Linux governor + turbo state when available), plus provenance (`bench_shape_version`, `engine_call`, `run_at_iso`). |

These files are overwritten on each `cargo bench` run. **They are not
committed to the repo by default** — they are your local result.

## Contributor submissions (`contributed/`)

The hardware-tier expectations table in the parent
[`README.md`](../README.md#4-hardware-tier-expectations) starts as
estimates for tiers B–G. To replace an estimate with measured data:

1. Run the bench on hardware that matches one of the tiers
   (A: production / B: modern Linux server / C: Apple Silicon /
   D: modern desktop / E: modern laptop / F: cloud VM /
   G: dev container).
2. Copy your `summary.json` into `contributed/` with a descriptive name:
   ```
   contributed/tier-{tier-letter}-{cpu-slug}.json
   ```
   Examples:
   - `contributed/tier-c-m2-air.json`
   - `contributed/tier-d-i7-12700k-win11.json`
   - `contributed/tier-f-aws-t3-medium.json`
3. Open a PR titled `bench: tier {X} measured on {short hardware name}`.
   The maintainer updates the README table from your file on the next
   release cut.

### What a good submission looks like

- `summary.json` produced by an **unmodified** `cargo bench` run (the
  CSV is not required for submission — keep it locally if you want, but
  it's 1–2 MB and would bloat the repo if committed for every tier).
- Run on a **quiescent host**: no big background work, ideally a
  dedicated runner. The pinned `scripts/bench.{sh,ps1}` runners and the
  `Dockerfile` all reduce noise — please use one of those rather than a
  raw `cargo bench` so submissions stay comparable.
- If you ran multiple times and picked the best, **say so in the PR
  description** with the spread. We'd rather see a measured range than
  a cherry-pick.

### What the maintainer commits to

- Replace the tier-row estimate with your measured numbers, citing your
  CPU and OS.
- Keep the `bench_shape_version` field in the table so we can flag when
  a future bench rev invalidates older submissions.
- Run our own production probe on each `the engine` release and post
  the updated tier-A reference to this directory.

## Why aren't there committed numbers from the maintainer yet?

This bench was authored from a development sandbox that prohibits
running compiled bench binaries directly, only `cargo check`/`cargo test`.
The harness, lib, and tests build clean (4/4 unit tests pass in release
mode), but the maintainer's headline `summary.json` will be committed
from a follow-up run on a known-tier machine — date TBD. Until then,
**the first contributor to PR a tier-D / tier-C / tier-B submission
becomes the citable reference for that tier.**

The 310 ns production claim is independently verifiable: it lives in
the platform's `state_ticker_ns` in-process metrics histogram and is
captured at runtime from live
WebSocket frames — not from this bench. This bench exists so an OSS
user can confirm the *order of magnitude* is correct on their own
hardware; it is not the source of the production number.
