#!/usr/bin/env bash
# bench.sh — POSIX runner for the framework bench suite. Linux + macOS.
#
# What this script does that bare `pytest` doesn't:
#   1. Optionally pins the python interpreter to a single core via
#      taskset (Linux only — macOS has no equivalent without
#      thread-affinity APIs).
#   2. Optionally requests the `performance` CPU governor on Linux
#      when run as root (skipped silently otherwise — we don't sudo
#      for you).
#   3. Prints the resulting governor / turbo state so the user can
#      spot a low-power-state run before they screenshot the number.
#   4. Echoes a one-screen summary of each bench's headline numbers
#      from the per-metric summary.json files after the run.
#
# Usage:
#   ./scripts/bench.sh                # default: pin to core 3, run all benches
#   PIN_CORE=0 ./scripts/bench.sh     # pin to a specific core
#   PIN_CORE=none ./scripts/bench.sh  # disable pinning
#   NO_GOVERNOR=1 ./scripts/bench.sh  # don't attempt to set governor
#   ./scripts/bench.sh -k dispatch    # forwards extra args to pytest

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BENCH_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

PIN_CORE="${PIN_CORE:-3}"
NO_GOVERNOR="${NO_GOVERNOR:-0}"

OS="$(uname -s)"

echo "──────────────────────────────────────────────────────────────────"
echo " Melaya framework bench — agentic runner suite"
echo " host  : ${OS} $(uname -r 2>/dev/null || true) / $(uname -m)"
echo " repo  : ${BENCH_DIR}"
echo " python: $(python3 --version 2>&1 || python --version 2>&1)"
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

RUN_PREFIX=()
if [[ "${PIN_CORE}" != "none" && "${OS}" == "Linux" ]]; then
    if command -v taskset >/dev/null 2>&1; then
        RUN_PREFIX=(taskset -c "${PIN_CORE}")
        echo " - pinning to core ${PIN_CORE} via taskset"
    fi
fi

# Pick whichever python is on PATH.
PYBIN="python3"
command -v python3 >/dev/null 2>&1 || PYBIN="python"

echo
echo "──────────────────────────────────────────────────────────────────"
echo " installing bench package (editable)"
echo "──────────────────────────────────────────────────────────────────"
cd "${BENCH_DIR}"
"${PYBIN}" -m pip install -e . >/dev/null

echo
echo "──────────────────────────────────────────────────────────────────"
echo " running pytest (5 benches, capped at ~5 min total)"
echo "──────────────────────────────────────────────────────────────────"

"${RUN_PREFIX[@]}" "${PYBIN}" -m pytest "${BENCH_DIR}/benches" -s "$@"

# ── Echo the headline numbers per metric ──
echo
echo "──────────────────────────────────────────────────────────────────"
echo " results/ summary"
echo "──────────────────────────────────────────────────────────────────"
for f in "${BENCH_DIR}"/results/*/summary.json; do
    [[ -f "$f" ]] || continue
    "${PYBIN}" -c "
import json, sys
s = json.load(open(sys.argv[1]))
print(f\"  {s['metric']:<32} p50={s['p50_us']:>8.2f} µs  p99={s['p99_us']:>8.2f} µs  n={s['n']}\")
" "$f"
done

echo
echo " Files written under:  ${BENCH_DIR}/results/<metric>/{summary.json, *_us.csv}"
echo
echo " If your hardware tier isn't represented in the README's"
echo " expectations table, open a PR with your summary.json — that's"
echo " how we keep the table honest. See results/README.md."
