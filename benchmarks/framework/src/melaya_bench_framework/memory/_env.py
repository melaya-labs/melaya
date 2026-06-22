"""Environment + provenance block for the memory bench.

Strict superset of the latency suite's env block: adds total_ram_gb,
glibc_version, allocator, malloc_arena_max, mem_metric. Every probe is
best-effort; on failure the field is null with a `<field>_unavailable_reason`.
"""

from __future__ import annotations

import os
import platform
import subprocess
import sys

import psutil

from ._sampler import OS_KEY, MEM_METRIC


def _shell(cmd: list[str]) -> str | None:
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=2)
        if out.returncode != 0:
            return None
        s = out.stdout.strip()
        return s or None
    except Exception:
        return None


def _cpu_model() -> str:
    if OS_KEY == "linux":
        try:
            with open("/proc/cpuinfo") as fh:
                for line in fh:
                    if line.startswith("model name"):
                        return line.split(":", 1)[1].strip()
        except OSError:
            pass
    if OS_KEY == "macos":
        s = _shell(["sysctl", "-n", "machdep.cpu.brand_string"])
        if s:
            return s
    if OS_KEY == "windows":
        s = _shell(["powershell", "-NoProfile", "-Command",
                    "(Get-CimInstance Win32_Processor).Name"])
        if s:
            return s.splitlines()[0].strip()
    return platform.processor() or "unknown"


def _glibc_version() -> tuple[str | None, str | None]:
    if OS_KEY != "linux":
        return None, "not-linux"
    try:
        v = platform.libc_ver()
        return (v[1] or None), (None if v[1] else "libc_ver-empty")
    except Exception as e:  # noqa: BLE001
        return None, str(e)[:60]


def env_block() -> dict:
    glibc, glibc_reason = _glibc_version()
    block = {
        "cpu_model": _cpu_model(),
        "logical_cores": os.cpu_count() or 0,
        "arch": platform.machine(),
        "os": OS_KEY,
        "os_kernel": platform.platform(),
        "python_version": platform.python_version(),
        "total_ram_gb": round(psutil.virtual_memory().total / (1024 ** 3), 1),
        "mem_metric": MEM_METRIC,
        "allocator": os.environ.get("MEL_MEM_ALLOCATOR", "glibc-default" if OS_KEY == "linux" else "system"),
        "malloc_arena_max": os.environ.get("MALLOC_ARENA_MAX"),
        "malloc_trim_applied": OS_KEY == "linux",
        "glibc_version": glibc,
        "cross_os_comparable": False,
    }
    if glibc is None and OS_KEY == "linux":
        block["glibc_version_unavailable_reason"] = glibc_reason
    return block
