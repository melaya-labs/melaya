#!/usr/bin/env bash
# bench.sh — POSIX runner for the state_ticker bench. Linux + macOS.
#
# What this script does that `cargo bench --bench state_ticker` doesn't:
#   1. Optionally pins the bench process to a single core (taskset on
#      Linux, no-op on macOS — see Apple's policy on QoS classes).
#   2. Optionally requests `performance` CPU governor on Linux when run
#      as root (skipped silently otherwise — we don't sudo for you).
#   3. Prints the resulting governor / turbo state so the user can spot
#      a low-power-state run before they screenshot the number.
#   4. Echoes the headline p50/p95/p99 from results/summary.json after
#      the run completes.
#
# Usage:
#   ./scripts/bench.sh                # default: pin to core 3, run bench
#   PIN_CORE=0 ./scripts/bench.sh     # pin to a specific core
#   PIN_CORE=none ./scripts/bench.sh  # disable pinning
#   NO_GOVERNOR=1 ./scripts/bench.sh  # don't attempt to set governor

set -euo pipefail

# Resolve repo paths so this script works from any CWD.
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

PIN_CORE="${PIN_CORE:-3}"
NO_GOVERNOR="${NO_GOVERNOR:-0}"

OS="$(uname -s)"

echo "──────────────────────────────────────────────────────────────────"
echo " Melaya engine bench — state_ticker_ns"
echo " host : ${OS} $(uname -r 2>/dev/null || true) / $(uname -m)"
echo " repo : ${BENCH_DIR}"
echo "──────────────────────────────────────────────────────────────────"

# ── (Linux only) try to set the `performance` governor for cpu0 ──
if [[ "${OS}" == "Linux" && "${NO_GOVERNOR}" != "1" ]]; then
    GOV_PATH="/sys/devices/system/cpu/cpu0/cpufreq/scaling_governor"
    if [[ -w "${GOV_PATH}" ]]; then
        echo " - setting cpu0 governor=performance (writable as $(id -un))"
        echo performance > "${GOV_PATH}" || true
    elif [[ -r "${GOV_PATH}" ]]; then
        CURRENT_GOV="$(cat "${GOV_PATH}")"
        echo " - current cpu0 governor: ${CURRENT_GOV}"
        if [[ "${CURRENT_GOV}" != "performance" ]]; then
            echo "   (tip: 'sudo cpupower frequency-set -g performance' for tighter p99)"
        fi
    fi

    TURBO_PATH="/sys/devices/system/cpu/intel_pstate/no_turbo"
    if [[ -r "${TURBO_PATH}" ]]; then
        TURBO="$(cat "${TURBO_PATH}")"
        if [[ "${TURBO}" == "0" ]]; then
            echo " - intel_pstate turbo: ENABLED (variance ↑, peak speed ↑)"
        else
            echo " - intel_pstate turbo: DISABLED (variance ↓, peak speed ↓)"
        fi
    fi
fi

# ── Build runner: pin to a core if asked & possible ──
RUN_PREFIX=()
if [[ "${PIN_CORE}" != "none" && "${OS}" == "Linux" ]]; then
    if command -v taskset >/dev/null 2>&1; then
        RUN_PREFIX=(taskset -c "${PIN_CORE}")
        echo " - pinning to core ${PIN_CORE} via taskset"
    fi
fi

# `cargo bench` insists on rebuilding to be sure profile flags match;
# that adds ~10-20 s the first time. Subsequent runs are instant.
echo
echo "──────────────────────────────────────────────────────────────────"
echo " running cargo bench (this takes ~30 s including build)"
echo "──────────────────────────────────────────────────────────────────"

cd "${BENCH_DIR}"
"${RUN_PREFIX[@]}" cargo bench --bench state_ticker

# ── Echo the headline numbers ──
SUMMARY="${BENCH_DIR}/results/summary.json"
if [[ -r "${SUMMARY}" ]]; then
    echo
    echo "──────────────────────────────────────────────────────────────────"
    echo " results/summary.json (headline)"
    echo "──────────────────────────────────────────────────────────────────"
    # python3 if present (cleanest), else jq, else just cat.
    if command -v python3 >/dev/null 2>&1; then
        python3 -c "
import json, sys
s = json.load(open(sys.argv[1]))
print(f\"  p50 = {s['p50_ns']:>5} ns\")
print(f\"  p95 = {s['p95_ns']:>5} ns\")
print(f\"  p99 = {s['p99_ns']:>5} ns\")
print(f\"  max = {s['max_ns']:>5} ns\")
print(f\"  env: {s['env']['cpu_model']}\")
" "${SUMMARY}"
    elif command -v jq >/dev/null 2>&1; then
        jq '{p50_ns, p95_ns, p99_ns, max_ns, env}' "${SUMMARY}"
    else
        cat "${SUMMARY}"
    fi
fi

echo
echo " Done. Files written:"
echo "   ${BENCH_DIR}/results/state_ticker_ns.csv"
echo "   ${BENCH_DIR}/results/summary.json"
echo
echo " If your hardware tier isn't represented in the README's"
echo " expectations table, open a PR with your summary.json — that's"
echo " how we keep the table honest."
