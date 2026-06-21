# `state_ticker_ns` - reproducible micro-bench for the Melaya engine

This crate exists for one reason: **anyone can clone the public Melaya
repo and re-measure the headline number we publish for the engine's
ticker hot path** -

> `state_ticker_ns` p50 ≈ **310 ns** per ticker write
> (Engine v0.4.48, 2026-04-24 production probe, 89,033 samples on an
> Intel Xeon 8369B / Ice Lake-SP, 4 vCPU SCHED_FIFO-pinned, Ubuntu 22.04.
> Full methodology in the Latency Matrix that ships with the platform.)

The bench is a thin criterion harness over **one engine method** -
`TickerCache::write_ticker` - with the exact same release-profile
flags the production engine ships with (`opt-level = 3`, fat LTO,
`codegen-units = 1`).

---

## 1. What this measures (and what it doesn't)

> [!important] One number, one operation.
> This bench measures the cost of writing **one** ticker frame into the
> engine's in-memory `TickerCache` cache. It is **pure in-process Rust
> compute**: hash the lower-cased exchange key, hash the upper-cased
> symbol key, look up the `TickerSnapshot` slot, stamp in the new bid /
> ask / last / `recv_wall_ns`. **No locks**, **no network**, **no HTTP**,
> **no syscalls beyond a single monotonic-clock read**.

Three numbers get conflated in trading-engine marketing. They're all
real, but they measure completely different segments of the pipeline:

| Number | Range | What it actually is |
|---|---|---|
| **`state_ticker_ns` (this bench)** | **300–500 ns p50** on engine-tier HW | One `TickerCache::write_ticker` call - pure in-process Rust. |
| `end_to_end_ns` | 14 µs p50 | `ws.read` return → `handle_messages` done. Includes parse, all state writes, histogram record. |
| Order dispatch to kernel | 2–7 µs | `dispatch_signal` → prepared HTTP send through the warm `ureq` connection - **outside** this code path. |
| `lag_us` (per-venue) | 1–200 ms | `recv_wall_ns − venue_ts_ms` - wholly dominated by network distance + venue-side aggregation. **NOT engine speed.** |

**Be especially careful not to confuse `state_ticker_ns` (this bench)
with "dispatch time" or "order latency"** - they are different
sub-systems with different cost models.

---

## 2. Three-command reproduction (verified)

```bash
# 1. Install Rust stable if you haven't already.
rustup toolchain install stable

# 2. Clone + cd. No platform side-clone required.
git clone https://github.com/melaya-labs/melaya
cd melaya/benchmarks/engine

# 3. Run the bench.
cargo bench --bench state_ticker
```

That's it. The crate has **no path dependencies** and no external setup
beyond `rustup`. First run takes ~30 s (build + bench); subsequent runs
take ~10 s. Results land in `./results/`.

### Why no `melaya-platform` clone?

The previous revision of this bench depended on the engine via a
relative path into the private engine crate. That
broke for every external user because `melaya-platform` is a private
repo. To make the bench universally reproducible we ship **a
self-contained reproduction of the engine's hot-path data shape** -
same `HashMap<(String, String), TickerSnapshot>` insert, same
`into_lower` / `into_upper` allocation-elision, same struct layout, same
release profile flags.

The full mapping (which engine lines reproduce as which bench lines, and
which lines are deliberately elided as no-ops on the steady-state hot
path) lives in the crate-level docstring at
[`src/lib.rs`](./src/lib.rs). Anyone with platform access can verify
equivalence by polling the engine's in-process histogram on the same
hardware. Bench vs engine should agree within ~10–15 %.

The long-term fix is publishing `the engine` to crates.io. When
that ships, this crate switches the shim for a direct dependency and
the file at `src/lib.rs` shrinks to a 5-line re-export - no other
changes required.

---

## 3. Pinned & opinionated runners

`cargo bench` works on any machine, but several easy-to-forget knobs
swing the headline number by 2–5×. The scripts below set them for you:

| Platform | Command | What it does |
|---|---|---|
| Linux / macOS | `./scripts/bench.sh` | Pins to core 3 via `taskset`, attempts `performance` governor (Linux), prints turbo state. |
| Windows | `.\scripts\bench.ps1` | Switches power plan to *High performance* for the duration, pins child process via `ProcessorAffinity`, restores the plan after. |
| Containerized (gold standard) | `docker build -t melaya-engine-bench . && docker run --rm --cpuset-cpus 3 -v "$PWD/results:/bench/results" melaya-engine-bench` | Identical Alpine userland + pinned rustc 1.94. Strips most host noise. |

All three drop their CSV + summary into the same `./results/` directory.

---

## 4. Hardware-tier expectations

A 310 ns p50 isn't a number you'll see on every laptop. The variables
that matter, in rough order of impact:

1. **µ-arch generation** - Ice Lake / Zen 4 / Apple Silicon are ~2× the
   per-op throughput of a 2018 Skylake or M1.
2. **CPU pinning + SCHED_FIFO** - un-pinned threads migrate between
   cores every few ms, blowing the L1 i-cache. SCHED_FIFO on Linux gets
   you another ~20 % over SCHED_OTHER for tail latency.
3. **Turbo + thermal headroom** - a laptop on battery throttles within
   seconds. Plug in.
4. **Background noise** - Chrome, an antivirus scan, Spotlight indexing
   all add p95/p99 jitter even though they don't move p50 much.

Expected p50 ranges, by tier. **A number outside the range for your
tier means you found a methodology bug or a real regression - not a
broken claim.** Bring it up in
[GitHub Discussions](https://github.com/melaya-labs/melaya/discussions)
and paste `results/summary.json`.

### Engine-tier hardware (all sub-microsecond on p50)

| Tier | Hardware example | OS / config | Expected p50 | Expected p95 |
|---|---|---|---|---|
| **A. Production (reference)** | Xeon Plat 8369B (Ice Lake-SP) | Ubuntu 22.04, pinned core, SCHED_FIFO, no turbo | **310 ns** | **980 ns** |
| **B. Modern Linux server** | Xeon Gold 6438 / EPYC 9354 | Ubuntu 22/24, performance governor | 350–500 ns | 0.7–1.2 µs |
| **C. Apple Silicon (M2/M3/M4)** | MacBook Air/Pro, plugged in | macOS 14+, native arm64 | 250–450 ns | 0.6–1.1 µs |
| **D. Modern Intel/AMD desktop** | i7-12700K / Ryzen 7700X | Win11 *High performance* or Linux performance gov | 400–650 ns | 0.8–1.4 µs |

Numbers in tiers B–D are estimates pending contributor PRs; the
production-tier row (A) is the measured production probe. **First
contributor to PR a `summary.json` for each tier replaces the estimate
with measured data.** See [`results/README.md`](./results/README.md)
for the submission format.

### Sub-engine-tier (not advertised, expected to drift past 1 µs)

The setups below CAN run the bench but the hardware itself becomes the
bottleneck - the engine is doing the same work, the machine is just
slower at executing it. We don't quote ranges here because the variance
is dominated by external factors (battery state, noisy neighbours,
CPU governor) rather than the bench:

- **Laptop on battery / thermal-throttled** (i7-1260P, Ryzen 7 7840U on Win11 *Balanced*).
  Plug in + High Performance, or use the `.ps1` runner, before comparing.
- **Shared-tenant cloud VM** (AWS `t3.medium`, GCP `e2-medium`).
  Tiny burstable instances share a physical core with strangers.
  Use a dedicated host or compare via the Docker runner.
- **Dev container / WSL2 on Windows**. Adds 10–30 % p50 / 30–100 % p99
  vs the host. Run native or via Docker on Linux for an apples-to-apples
  number.

If your `summary.json` looks like an engine-tier number, you're done.
If it doesn't, check §6 *Troubleshooting* before assuming the bench
itself is wrong.

---

## 5. Reading `summary.json`

After a run, `./results/summary.json` looks like this (real shape, sample
numbers from a notional run on tier-D hardware):

```json
{
  "n": 100000,
  "min_ns": 100,
  "p50_ns": 480,
  "p90_ns": 620,
  "p95_ns": 740,
  "p99_ns": 1900,
  "p999_ns": 6500,
  "max_ns": 17200,
  "metric": "state_ticker_ns",
  "engine_call": "TickerCache::write_ticker",
  "bench_shape_version": "0.4.x-shape",
  "headline_samples": 100000,
  "warmup_secs": 3,
  "run_at_iso": "2026-06-20T14:30:00+00:00",
  "env": {
    "cpu_model": "Intel(R) Core(TM) i7-12700K CPU @ 3.60GHz",
    "logical_cores": 20,
    "os_kernel": "Microsoft Windows 11 Pro 10.0.22631",
    "rustc_version": "rustc 1.94.0 (4a4ef493e 2026-03-02)",
    "cpu_governor": null,
    "turbo_state": null,
    "arch": "x86_64",
    "os": "windows"
  },
  "reference_hardware": "Engine v0.4.48 measured 310 ns p50 on Intel Xeon Platinum 8369B …"
}
```

The bench also writes `./results/state_ticker_ns.csv` - one row per
iteration (100,000 rows), `iteration,ns`. Pipe it into your favourite
plotting tool if you want a histogram, or attach it to a PR if a
maintainer asks for raw samples.

---

## 6. "My number is way off" - troubleshooting

| Symptom | Likely cause |
|---|---|
| **p50 > 5 µs on any modern hardware** | You ran a debug build. Verify the binary path contains `target/release/`. Criterion enforces release by default; if you wrote your own runner, `cargo run` is **not** what you want - `cargo bench` is. |
| **p50 > 2 µs on a laptop with no apparent reason** | Battery / low-power state. Plug in, disable battery saver, set Windows to *High performance* (or use the .ps1 runner), check macOS isn't on Low Power Mode. |
| **Wild p99 / p999 (> 50× p50)** | Co-tenant or background work - antivirus full scan, indexing, browser tab. Re-run after closing those. The Docker runner with `--cpuset-cpus` strips most of this. |
| **p50 ~1 µs on a recent Linux box** | Governor stuck at `powersave` or `ondemand`. Run `cat /sys/devices/system/cpu/cpu0/cpufreq/scaling_governor`. Set to `performance` (`sudo cpupower frequency-set -g performance`) and re-run. |
| **p50 looks correct on prod, my dev box reads 2-3× higher** | Expected - see the tier table. Pinning + SCHED_FIFO + turbo-off is what gets prod to 310 ns. |
| **Numbers shift run-to-run by > 30 %** | `Instant::now()` resolution on your platform is the floor. On Linux this is ~25 ns; on Windows QueryPerformanceCounter is ~25-100 ns. The bench is measuring something whose true cost is in the same order - there is irreducible clock noise. The Docker runner pinned to a single core is the tightest you'll get without `rdtsc` instrumentation. |

If none of those match, open a
[Discussion](https://github.com/melaya-labs/melaya/discussions) with
`results/summary.json` attached. We triage hardware-tier regressions in
the open.

---

## 7. Submitting your hardware tier

We want the tier table above to be **measured data, not estimates**.
First contributor to run the bench on a given tier wins:

1. Run the bench (any of the three runners).
2. Copy `results/summary.json` into a PR that adds a row to
   [`results/contributed/`](./results/) named
   `tier-{A..G}-{cpu-slug}.json` (e.g., `tier-c-m2-air.json`).
3. The maintainer updates the README table from your measured numbers
   on the next release.

Full submission format is in [`results/README.md`](./results/README.md).

---

## 8. File layout

| Path | Purpose |
|---|---|
| [`Cargo.toml`](./Cargo.toml) | Standalone crate. **No path dependency.** |
| [`src/lib.rs`](./src/lib.rs) | Faithful reproduction of `TickerCache::write_ticker`. Crate-level docstring documents the line-by-line correspondence with the engine. |
| [`benches/state_ticker.rs`](./benches/state_ticker.rs) | The criterion harness + the 100k-sample CSV collector + env detection. |
| [`scripts/bench.sh`](./scripts/bench.sh) | POSIX runner (Linux + macOS) with pinning + governor hints. |
| [`scripts/bench.ps1`](./scripts/bench.ps1) | Windows runner with affinity + power-plan switch. |
| [`Dockerfile`](./Dockerfile) | Containerised runner (`rust:1.94-slim`). Strips host noise. |
| [`results/`](./results/) | Output directory. Includes `README.md` for the contributor submission flow. |

---

## 9. Licence

Apache-2.0, same as the rest of this repo.
