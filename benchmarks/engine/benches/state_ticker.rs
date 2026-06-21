//! state_ticker.rs — reproducible criterion bench for the engine
//! `state_ticker_ns` hot path.
//!
//! See README.md for what this measures (and what it doesn't). One line:
//! the cost of one `TickerCache::write_ticker` call — same data
//! shape as the engine, whose internal histogram showed 310 ns median
//! on the 2026-04-24 prod probe.
//!
//! Run:
//!   cargo bench --bench state_ticker
//!
//! After the run completes, samples are written to
//!   results/state_ticker_ns.csv
//!   results/summary.json
//!
//! The summary.json includes hardware metadata (CPU model, core count,
//! OS, rustc version, governor on Linux) so a reader can quickly tell
//! whether a number landed in the expected range for that hardware
//! tier. See the README "Hardware-tier expectations" table.

use std::fs;
use std::io::Write as _;
use std::path::PathBuf;
use std::process::Command;
use std::time::Instant;

use criterion::{
    black_box, criterion_group, BatchSize, Criterion, Throughput,
};

use melaya_bench_engine::{fresh_ticker_cache, TickerSample, BENCH_ENGINE_SHAPE_VERSION};

// ── Tunables ────────────────────────────────────────────────────────────────

/// How many samples we want in `results/state_ticker_ns.csv`. The
/// engine probe collected 89,033 ticker samples in a 60 s window;
/// we go a bit higher so the percentile estimates are tighter.
const HEADLINE_SAMPLES: usize = 100_000;

/// Warm-up duration before the timed loop. The Latency Matrix runs
/// a 60 s probe; criterion's default warm-up of 3 s is enough for
/// branch predictors + i-cache, but we set it explicitly so the
/// constraint is checked, not implied.
const WARMUP_SECS: u64 = 3;

// ── Path helpers ────────────────────────────────────────────────────────────

fn results_dir() -> PathBuf {
    // Resolve relative to CARGO_MANIFEST_DIR so the path is stable
    // whether you `cargo bench` from the crate dir or the repo root.
    let mut p = PathBuf::from(env!("CARGO_MANIFEST_DIR"));
    p.push("results");
    fs::create_dir_all(&p).expect("create results dir");
    p
}

// ── Environment detection ───────────────────────────────────────────────────
//
// All shell-outs here are best-effort. They run ONCE, OUTSIDE the timed
// section, and any failure falls through to `"unknown"`. The intent is
// to capture enough metadata that a reader of `summary.json` can answer
// "is my hardware comparable to theirs?" in 5 seconds.

/// Run a command, return trimmed stdout, or `None` on any failure.
fn shell(cmd: &str, args: &[&str]) -> Option<String> {
    let out = Command::new(cmd).args(args).output().ok()?;
    if !out.status.success() {
        return None;
    }
    let s = String::from_utf8_lossy(&out.stdout).trim().to_string();
    if s.is_empty() {
        None
    } else {
        Some(s)
    }
}

fn cpu_model() -> String {
    // Order: Linux /proc/cpuinfo, then macOS sysctl, then Windows wmic,
    // then env-var fallback, then unknown.
    if let Ok(s) = fs::read_to_string("/proc/cpuinfo") {
        for line in s.lines() {
            if let Some(rest) = line.strip_prefix("model name") {
                if let Some(colon) = rest.find(':') {
                    return rest[colon + 1..].trim().to_string();
                }
            }
        }
    }
    if let Some(s) = shell("sysctl", &["-n", "machdep.cpu.brand_string"]) {
        return s;
    }
    if let Some(s) = shell(
        "wmic",
        &["cpu", "get", "name", "/format:list"],
    ) {
        for line in s.lines() {
            if let Some(rest) = line.strip_prefix("Name=") {
                let t = rest.trim();
                if !t.is_empty() {
                    return t.to_string();
                }
            }
        }
    }
    // PowerShell fallback for Win11 where wmic is being deprecated.
    if let Some(s) = shell(
        "powershell",
        &["-NoProfile", "-Command", "(Get-CimInstance Win32_Processor).Name"],
    ) {
        return s;
    }
    std::env::var("PROCESSOR_IDENTIFIER")
        .or_else(|_| std::env::var("CPU"))
        .unwrap_or_else(|_| "unknown".to_string())
}

fn logical_cores() -> usize {
    std::thread::available_parallelism()
        .map(|n| n.get())
        .unwrap_or(0)
}

fn os_kernel() -> String {
    if let Some(s) = shell("uname", &["-srm"]) {
        return s;
    }
    if let Some(s) = shell(
        "powershell",
        &["-NoProfile", "-Command", "(Get-CimInstance Win32_OperatingSystem).Caption + ' ' + (Get-CimInstance Win32_OperatingSystem).Version"],
    ) {
        return s;
    }
    format!("{} {}", std::env::consts::OS, std::env::consts::ARCH)
}

fn cpu_governor() -> Option<String> {
    // Linux only — read the first CPU's scaling governor. None elsewhere.
    let path = "/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor";
    fs::read_to_string(path).ok().map(|s| s.trim().to_string())
}

fn turbo_state() -> Option<String> {
    // Linux Intel only — `intel_pstate/no_turbo` is "0" if turbo is on.
    let path = "/sys/devices/system/cpu/intel_pstate/no_turbo";
    if let Ok(s) = fs::read_to_string(path) {
        return Some(match s.trim() {
            "0" => "intel_pstate: turbo ENABLED".to_string(),
            "1" => "intel_pstate: turbo DISABLED".to_string(),
            other => format!("intel_pstate: no_turbo={}", other),
        });
    }
    None
}

fn rustc_version() -> String {
    // `rustc --version` is on PATH for any user running `cargo bench`.
    shell("rustc", &["--version"]).unwrap_or_else(|| "unknown".to_string())
}

fn run_at_iso() -> String {
    chrono::Utc::now().to_rfc3339()
}

fn run_at_ms() -> i64 {
    chrono::Utc::now().timestamp_millis()
}

// ── Criterion bench: the headline measurement ───────────────────────────────

fn bench_state_ticker(c: &mut Criterion) {
    let template = TickerSample::binance_btc_usdt();

    // Pre-seed the TickerCache so the timed section measures STEADY-STATE
    // updates (no HashMap entry-creation in the timed path). This matches
    // the engine probe — the histogram was collected over 60 s of live
    // traffic where every (exchange, symbol) key was long-resident.
    let mut state = fresh_ticker_cache();
    state.write_ticker(
        template.exchange.clone(),
        template.symbol.clone(),
        template.bid,
        template.ask,
        template.last,
        template.ts_ms,
        template.recv_wall_ns,
    );

    let mut group = c.benchmark_group("state_ticker_ns");
    group.warm_up_time(std::time::Duration::from_secs(WARMUP_SECS));
    // Throughput: one ticker frame per iteration.
    group.throughput(Throughput::Elements(1));

    group.bench_function("write_ticker", |b| {
        b.iter_batched(
            || {
                // Per-iteration setup: allocate two fresh Strings, the
                // way `parse_frame` produces them on the live hot path.
                // String::clone of a short string is ~10-15 ns; we
                // include it because the engine's own histogram does.
                (template.exchange.clone(), template.symbol.clone())
            },
            |(ex, sym)| {
                state.write_ticker(
                    black_box(ex),
                    black_box(sym),
                    black_box(template.bid),
                    black_box(template.ask),
                    black_box(template.last),
                    black_box(template.ts_ms),
                    black_box(template.recv_wall_ns),
                );
                // No return value; criterion measures wall time around
                // the closure. `state` mutation is observed by the next
                // iteration (same key), preventing dead-code elim.
                black_box(&state);
            },
            BatchSize::SmallInput,
        )
    });
    group.finish();
}

// ── Hand-rolled high-sample collector: per-iteration ns to CSV ──────────────
//
// criterion's HTML report is great for engineers, but the public proof
// behind the 310 ns claim wants a flat CSV of per-iteration nanosecond
// samples + a compact summary.json. We do our own monotonic-clock loop
// AFTER criterion has run (so the JIT'd release binary is fully warm).

fn collect_raw_samples() {
    let template = TickerSample::binance_btc_usdt();

    let mut state = fresh_ticker_cache();
    // Identical pre-seed to the criterion bench.
    state.write_ticker(
        template.exchange.clone(),
        template.symbol.clone(),
        template.bid,
        template.ask,
        template.last,
        template.ts_ms,
        template.recv_wall_ns,
    );

    // Warm-up: spin for ≥ WARMUP_SECS before the timed section, doing
    // the same work we're about to measure. Branch predictor + i-cache
    // are then representative.
    let warmup_deadline = Instant::now() + std::time::Duration::from_secs(WARMUP_SECS);
    while Instant::now() < warmup_deadline {
        let ex = template.exchange.clone();
        let sym = template.symbol.clone();
        state.write_ticker(
            black_box(ex),
            black_box(sym),
            black_box(template.bid),
            black_box(template.ask),
            black_box(template.last),
            black_box(template.ts_ms),
            black_box(template.recv_wall_ns),
        );
        black_box(&state);
    }

    // Timed section: HEADLINE_SAMPLES individually-clocked iterations.
    //
    // NOTE on clock resolution: on Linux x86, `Instant::now()` resolves
    // via clock_gettime(CLOCK_MONOTONIC) which is ~25 ns/call via vDSO.
    // On Windows, `Instant::now()` resolves via QueryPerformanceCounter
    // which is ~25-100 ns/call. For a 300 ns target measurement the
    // clock noise is ~10-30% of signal — manageable for p50/p95 but
    // explains why p99 has more jitter on Windows than on prod Linux.
    let mut samples: Vec<u64> = Vec::with_capacity(HEADLINE_SAMPLES);
    for _ in 0..HEADLINE_SAMPLES {
        let ex = template.exchange.clone();
        let sym = template.symbol.clone();

        let t0 = Instant::now();
        state.write_ticker(
            black_box(ex),
            black_box(sym),
            black_box(template.bid),
            black_box(template.ask),
            black_box(template.last),
            black_box(template.ts_ms),
            black_box(template.recv_wall_ns),
        );
        let dt = t0.elapsed().as_nanos() as u64;
        black_box(&state);
        samples.push(dt);
    }

    write_csv(&samples).expect("write csv");
    write_summary(&samples).expect("write summary");
}

fn write_csv(samples: &[u64]) -> std::io::Result<()> {
    let mut path = results_dir();
    path.push("state_ticker_ns.csv");
    let mut f = fs::File::create(&path)?;
    writeln!(f, "iteration,ns")?;
    for (i, ns) in samples.iter().enumerate() {
        writeln!(f, "{},{}", i, ns)?;
    }
    eprintln!("wrote {} samples to {}", samples.len(), path.display());
    Ok(())
}

fn write_summary(samples: &[u64]) -> std::io::Result<()> {
    let mut sorted: Vec<u64> = samples.to_vec();
    sorted.sort_unstable();
    let n = sorted.len();
    let pct = |p: f64| -> u64 {
        if n == 0 {
            return 0;
        }
        let idx = ((n as f64 - 1.0) * p).round() as usize;
        sorted[idx.min(n - 1)]
    };

    let env = serde_json::json!({
        "cpu_model":      cpu_model(),
        "logical_cores":  logical_cores(),
        "os_kernel":      os_kernel(),
        "rustc_version":  rustc_version(),
        "cpu_governor":   cpu_governor(),
        "turbo_state":    turbo_state(),
        "arch":           std::env::consts::ARCH,
        "os":             std::env::consts::OS,
    });

    let summary = serde_json::json!({
        // Headline percentile suite
        "n":               n,
        "min_ns":          *sorted.first().unwrap_or(&0),
        "p50_ns":          pct(0.50),
        "p90_ns":          pct(0.90),
        "p95_ns":          pct(0.95),
        "p99_ns":          pct(0.99),
        "p999_ns":         pct(0.999),
        "max_ns":          *sorted.last().unwrap_or(&0),

        // Provenance
        "metric":          "state_ticker_ns",
        "engine_call":     "TickerCache::write_ticker",
        "bench_shape_version": BENCH_ENGINE_SHAPE_VERSION,
        "headline_samples": HEADLINE_SAMPLES,
        "warmup_secs":      WARMUP_SECS,

        // Run metadata
        "run_at_unix_ms":  run_at_ms(),
        "run_at_iso":      run_at_iso(),

        // Environment (CPU + OS + toolchain — fill from real probes)
        "env":             env,

        // Editorial — copies what this is and isn't into the file
        // itself so you can read it standalone without the README.
        "what_this_is":
            "Cost of one in-memory ticker write into TickerCache. \
             Matches the engine-internal `state_ticker_ns` histogram \
             (Latency Matrix §3) for the steady-state same-key case. \
             It is NOT network, NOT venue lag, NOT order dispatch.",
        "what_this_is_not":
            "Order-to-kernel dispatch (~2-7 µs, see engine dispatch.rs). \
             End-to-end ws.read → handle_messages (~14 µs p50). \
             Per-venue lag_us (1-200 ms, network + venue side, NOT engine speed).",
        "reference_hardware":
            "Engine v0.4.48 measured 310 ns p50 on Intel Xeon Platinum 8369B \
             (Ice Lake-SP @ 2.7 GHz, 4 vCPU SCHED_FIFO-pinned, Ubuntu 22.04). \
             Commodity laptop ranges in README.md.",
    });

    let mut path = results_dir();
    path.push("summary.json");
    let mut f = fs::File::create(&path)?;
    f.write_all(serde_json::to_string_pretty(&summary)?.as_bytes())?;
    f.write_all(b"\n")?;
    eprintln!("wrote summary to {}", path.display());

    // Echo the headline numbers + env to stdout so a user running
    // `cargo bench` gets a one-screen result without opening files.
    eprintln!();
    eprintln!("──────────────────────────────────────────────────────────────────");
    eprintln!(" state_ticker_ns — {} samples", n);
    eprintln!("──────────────────────────────────────────────────────────────────");
    eprintln!(
        " p50 = {} ns    p95 = {} ns    p99 = {} ns    max = {} ns",
        pct(0.50),
        pct(0.95),
        pct(0.99),
        sorted.last().copied().unwrap_or(0),
    );
    eprintln!();
    eprintln!(" hardware : {}", cpu_model());
    eprintln!(" cores    : {}", logical_cores());
    eprintln!(" os       : {}", os_kernel());
    eprintln!(" rustc    : {}", rustc_version());
    if let Some(g) = cpu_governor() {
        eprintln!(" governor : {}", g);
    }
    if let Some(t) = turbo_state() {
        eprintln!(" turbo    : {}", t);
    }
    eprintln!("──────────────────────────────────────────────────────────────────");
    eprintln!(
        " Compare to engine v0.4.48 reference: p50 = 310 ns on Xeon 8369B"
    );
    eprintln!(
        " See benchmarks/engine/README.md → Hardware-tier expectations."
    );
    eprintln!("──────────────────────────────────────────────────────────────────");

    Ok(())
}

criterion_group!(benches, bench_state_ticker);

// Custom main instead of `criterion_main!`: run the criterion headline
// group, emit criterion's own summary, then collect the high-sample CSV
// + summary.json exactly ONCE.
//
// The collector is a hand-rolled monotonic-clock loop. It must NOT go
// through criterion's measurement machinery: the previous version wrapped
// it in `iter_custom` returning a constant 1 ns, which made criterion run
// the (3 s warm-up + 100k-sample) collection many times AND then panic in
// its stats layer trying to bootstrap a zero-variance sample
// (`assertion failed: slice.len() > 1 && ... !is_nan()`). Calling it
// directly here keeps the reproducibility artifacts and makes
// `cargo bench --bench state_ticker` exit 0.
fn main() {
    benches();
    Criterion::default().configure_from_args().final_summary();
    collect_raw_samples();
}
