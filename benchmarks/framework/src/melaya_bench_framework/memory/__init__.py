"""Memory-footprint & capacity benchmark for the Melaya agentic runner.

OPT-IN HEAVY suite - intentionally NOT collected by `pytest benches/`.
The lightweight latency suite (`pip install -e . && pytest benches/ -s`)
stays self-contained and never imports this subpackage or psutil; the
top-level `melaya_bench_framework.__init__` does not import `.memory`.

Run it explicitly:

    pip install -e ".[memory]"
    python -m melaya_bench_framework.memory --scenarios s0,s1 --runs 5

See `Obsidian/01 Architecture/Memory Footprint and Capacity Benchmark
Spec.md` for the full design (two decoupled subjects, per-OS metric, the
capacity model).
"""

from __future__ import annotations

MEM_SHAPE_VERSION = "0.1.0-mem-shape"

__all__ = ["MEM_SHAPE_VERSION", "main"]


def main(argv: list[str] | None = None) -> int:
    from ._harness import main as _main
    return _main(argv)
