"""Shared types for the Melaya Python SDK (trading plane)."""
from __future__ import annotations

from typing import Any, Dict, List, Optional, Tuple, Union

# Market kind
Market = str  # "spot" | "swap" | "future" | "futures" | "perpetuals" | "option"

# A single order-book level: [price, amount]
BookLevel = Tuple[float, float]

# A single OHLCV candle: [timestamp, open, high, low, close, volume]
Candle = Tuple[float, float, float, float, float, float]

# JSON-compatible generic dict
JsonDict = Dict[str, Any]

__all__ = ["Market", "BookLevel", "Candle", "JsonDict"]
