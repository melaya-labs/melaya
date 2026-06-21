"""Cost / token-tracking shim.

Records per-call token usage against a price table and aggregates the
running USD spend + per-model breakdown. Prices here are illustrative
placeholders, not real rates. Pure in-process arithmetic + dict updates.
"""
from __future__ import annotations
from typing import Any


class CostTracker:
    # Illustrative USD per 1M tokens (input, output) — NOT real rates.
    PRICES: dict[str, tuple[float, float]] = {
        "model_a": (3.0, 15.0),
        "model_b": (0.5, 1.5),
        "model_c": (10.0, 30.0),
    }

    def __init__(self) -> None:
        self._in = 0
        self._out = 0
        self._usd = 0.0
        self._by_model: dict[str, dict[str, float]] = {}

    def record(self, model: str, in_tok: int, out_tok: int) -> float:
        p_in, p_out = self.PRICES.get(model, (1.0, 1.0))
        cost = in_tok / 1e6 * p_in + out_tok / 1e6 * p_out
        self._in += in_tok
        self._out += out_tok
        self._usd += cost
        m = self._by_model.setdefault(model, {"in": 0, "out": 0, "usd": 0.0})
        m["in"] += in_tok
        m["out"] += out_tok
        m["usd"] += cost
        return cost

    def summary(self) -> dict[str, Any]:
        return {"input": self._in, "output": self._out,
                "usd": round(self._usd, 6), "by_model": self._by_model}
