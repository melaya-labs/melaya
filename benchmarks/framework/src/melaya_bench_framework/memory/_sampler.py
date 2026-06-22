"""Per-OS memory sampler + process-set resolution.

The measurement subject samples ITS OWN footprint (it is already the
isolated subprocess the harness spawned), summed over its real children
(Chromium, python_repl, MCP stdio) and any named sibling daemons
(the model server, resolved by port - never folded into the pipeline).

Metric kind is OS-correct and NEVER averaged across OS:

    linux  -> pss            (memory_full_info().pss)    HWM: VmHWM
    win32  -> private_bytes  (memory_info().private)     HWM: peak_wset
    darwin -> uss            (memory_full_info().uss)    HWM: ru_maxrss

`rss` is always captured alongside as the labelled upper bound.
"""

from __future__ import annotations

import sys
import threading
import time
from typing import Optional

import psutil

PLATFORM = sys.platform
if PLATFORM.startswith("linux"):
    OS_KEY, MEM_METRIC = "linux", "pss"
elif PLATFORM == "win32":
    OS_KEY, MEM_METRIC = "windows", "private_bytes"
elif PLATFORM == "darwin":
    OS_KEY, MEM_METRIC = "macos", "uss"
else:
    OS_KEY, MEM_METRIC = PLATFORM, "rss"

_MB = 1024.0 * 1024.0


def _proc_metric_bytes(p: psutil.Process) -> tuple[int, int]:
    """Return (os_metric_bytes, rss_bytes) for one process. The os_metric
    is PSS on Linux, private_bytes on Windows, USS on macOS, RSS otherwise.
    RSS is always the second element as the labelled upper bound."""
    try:
        mi = p.memory_info()
        rss = int(getattr(mi, "rss", 0))
        if OS_KEY == "windows":
            metric = int(getattr(mi, "private", rss))
        elif OS_KEY == "linux":
            metric = int(getattr(p.memory_full_info(), "pss", rss))
        elif OS_KEY == "macos":
            metric = int(getattr(p.memory_full_info(), "uss", rss))
        else:
            metric = rss
        return metric, rss
    except (psutil.NoSuchProcess, psutil.AccessDenied, ValueError):
        return 0, 0


def _peak_hwm_bytes(p: psutil.Process) -> int:
    """Authoritative high-water mark for one process (peak_wset on
    Windows, VmHWM on Linux). 0 if unavailable on this OS."""
    try:
        if OS_KEY == "windows":
            return int(getattr(p.memory_info(), "peak_wset", 0))
        if OS_KEY == "linux":
            with open(f"/proc/{p.pid}/status", "r") as fh:
                for line in fh:
                    if line.startswith("VmHWM:"):
                        return int(line.split()[1]) * 1024
    except (psutil.NoSuchProcess, psutil.AccessDenied, OSError, ValueError):
        pass
    return 0


def resolve_sibling_pids(ports: list[int]) -> list[psutil.Process]:
    """Resolve external daemons (the model server) by listening port -
    they are SIBLINGS reached over HTTP, never children of this process,
    so a process-tree walk would miss them. Reported in Subject B, kept
    OUT of the pipeline footprint by the caller."""
    found: dict[int, psutil.Process] = {}
    want = set(ports)
    try:
        for c in psutil.net_connections(kind="inet"):
            if c.laddr and c.laddr.port in want and c.pid and c.status == psutil.CONN_LISTEN:
                if c.pid not in found:
                    try:
                        found[c.pid] = psutil.Process(c.pid)
                    except psutil.NoSuchProcess:
                        pass
    except (psutil.AccessDenied, OSError):
        pass
    return list(found.values())


class SelfSampler:
    """Background thread sampling THIS process + its recursive children.

    Each tick records (t, os_metric_mb, rss_mb, n_procs). The model
    server (a named sibling) is sampled separately by the harness and is
    NOT included here - the pipeline footprint stays LLM-agnostic.
    """

    def __init__(self, interval_ms: int = 30) -> None:
        self.interval = max(0.005, interval_ms / 1000.0)
        self._self = psutil.Process()
        self._stop = threading.Event()
        self._thread: Optional[threading.Thread] = None
        self.samples: list[tuple[float, float, float, int]] = []

    def _proc_set(self) -> list[psutil.Process]:
        procs = [self._self]
        try:
            procs.extend(self._self.children(recursive=True))
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            pass
        return procs

    def _tick(self) -> tuple[float, float, int]:
        metric_sum = 0
        rss_sum = 0
        procs = self._proc_set()
        for p in procs:
            m, r = _proc_metric_bytes(p)
            metric_sum += m
            rss_sum += r
        return metric_sum / _MB, rss_sum / _MB, len(procs)

    def _run(self) -> None:
        while not self._stop.is_set():
            t = time.time()
            metric_mb, rss_mb, n = self._tick()
            self.samples.append((t, metric_mb, rss_mb, n))
            self._stop.wait(self.interval)

    def start(self) -> None:
        self._thread = threading.Thread(target=self._run, name="mem-sampler", daemon=True)
        self._thread.start()

    def stop(self) -> None:
        self._stop.set()
        if self._thread:
            self._thread.join(timeout=2.0)

    def snapshot_mb(self) -> float:
        """One immediate os-metric reading of the whole process set (MB)."""
        metric_mb, _, _ = self._tick()
        return metric_mb

    def peak_hwm_mb(self) -> float:
        """Max OS high-water mark across the current process set (MB)."""
        return max((_peak_hwm_bytes(p) for p in self._proc_set()), default=0) / _MB

    def orphan_child_count(self) -> int:
        try:
            return len(self._self.children(recursive=True))
        except (psutil.NoSuchProcess, psutil.AccessDenied):
            return 0
