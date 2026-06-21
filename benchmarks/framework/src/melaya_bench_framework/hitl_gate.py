"""HITL gate shim — the per-write enforcement path the runner runs
*before* a write tool is allowed through.

This is distinct from the human approval *wait* (which is human-bound and
unbenchable — see ``results/hitl_round_trip/methodology_only.json``). What
this models is the synchronous safety machinery that runs on every write
attempt before it is ever queued for a human:

    1. sidecar-state read   — a per-pipeline ContextVar; a RED state
                              (set by a reactive risk watcher) auto-rejects
    2. per-cycle write floor — caps writes per reasoning cycle
    3. daily quota           — a per-tenant counter (production backs this
                              with an atomic store; here it is an in-process
                              dict so the bench needs no network)
    4. cost cap              — running USD spend vs the tenant's daily cap

These are the "trading-grade discipline" checks that gate every write. The
shim reproduces their *shape and cost*, not the production thresholds or
the real store — no network, no secrets, no proprietary policy values.
"""
from __future__ import annotations

import contextvars
from typing import Tuple

# Per-pipeline isolation primitive: each crew/pipeline runs with its own
# sidecar state so one tenant's halt never leaks into another's.
_sidecar_state: contextvars.ContextVar[str] = contextvars.ContextVar(
    "sidecar_state", default="GREEN"
)


class _CounterStore:
    """In-process stand-in for the atomic per-tenant counter the
    production gate increments for the daily write/order quota. No
    network — keeps the bench self-contained and air-gap runnable."""

    def __init__(self) -> None:
        self._counts: dict[str, int] = {}

    def incr(self, key: str) -> int:
        self._counts[key] = self._counts.get(key, 0) + 1
        return self._counts[key]


class HitlGate:
    """Reproduces the synchronous enforcement path run on every write
    before human approval. ``check_write`` returns ``(allowed, reason)``.

    Thresholds here are bench knobs, not production policy values.
    """

    def __init__(
        self,
        *,
        max_writes_per_cycle: int = 5,
        daily_cap: int = 1000,
        cost_cap_usd: float = 10.0,
    ) -> None:
        self.max_writes_per_cycle = max_writes_per_cycle
        self.daily_cap = daily_cap
        self.cost_cap_usd = cost_cap_usd
        self._store = _CounterStore()
        self._cycle_writes = 0
        self._spent_usd = 0.0

    def reset_cycle(self) -> None:
        self._cycle_writes = 0

    def check_write(self, tenant: str, est_cost_usd: float = 0.0) -> Tuple[bool, str]:
        """The four synchronous checks the gate runs per write attempt.
        Pure in-process control flow + one dict incr — no I/O."""
        # 1. sidecar state (ContextVar read) — RED auto-rejects
        if _sidecar_state.get() == "RED":
            return False, "sidecar_red"
        # 2. per-cycle write floor
        self._cycle_writes += 1
        if self._cycle_writes > self.max_writes_per_cycle:
            return False, "write_cap"
        # 3. per-tenant daily quota (atomic counter incr)
        if self._store.incr(f"{tenant}:day") > self.daily_cap:
            return False, "daily_quota"
        # 4. running cost cap
        self._spent_usd += est_cost_usd
        if self._spent_usd > self.cost_cap_usd:
            return False, "cost_cap"
        return True, "ok"
