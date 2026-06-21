"""Crew-orchestration shim.

A multi-persona crew (e.g. macro -> technical -> risk -> execution) where
each persona hands its context to the next, the risk persona can VETO and
halt the chain mid-run, and a reactive sidecar can interrupt. Models the
orchestration + veto-halt propagation cost. Each persona is a zero-work
mock so the number is pure runner orchestration overhead.
"""
from __future__ import annotations
from typing import Any, Awaitable, Callable


class Persona:
    def __init__(self, name: str, fn: Callable[[dict], Awaitable[dict]]) -> None:
        self.name = name
        self.fn = fn

    async def run(self, ctx: dict[str, Any]) -> dict[str, Any]:
        return await self.fn(ctx)


class Crew:
    def __init__(self, personas: list[Persona], risk_index: int | None = None) -> None:
        self.personas = personas
        self.risk_index = risk_index

    async def run(self, ctx: dict[str, Any], veto: bool = False) -> dict[str, Any]:
        for i, p in enumerate(self.personas):
            ctx = await p.run(ctx)
            if self.risk_index is not None and i == self.risk_index and veto:
                return {"halted_at": p.name, "ctx": ctx}
        return {"completed": True, "ctx": ctx}
