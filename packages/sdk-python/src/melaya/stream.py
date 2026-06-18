"""WebSocket streaming API (async).

Each method returns an async-iterable stream of normalized frames:

    async for frame in m.stream.ticker(exchange="binance", symbol="BTC/USDT", market="spot"):
        print(frame["last"])

Requires the optional `websockets` dependency: ``pip install "melaya[stream]"``.
"""
from __future__ import annotations

import json
from typing import Any, AsyncIterator, Dict, Optional
from urllib.parse import urlencode

from .errors import MelayaError

try:  # optional dependency
    import websockets  # type: ignore
except ImportError:  # pragma: no cover
    websockets = None  # type: ignore


class _Stream:
    """An async-iterable stream of JSON frames from a Melaya WebSocket."""

    def __init__(self, url: str) -> None:
        self._url = url

    async def __aiter__(self) -> AsyncIterator[Dict[str, Any]]:
        if websockets is None:
            raise MelayaError(
                "Melaya: streaming requires the 'websockets' package. "
                "Install with: pip install \"melaya[stream]\""
            )
        async with websockets.connect(self._url) as ws:
            async for raw in ws:
                try:
                    yield json.loads(raw)
                except (ValueError, TypeError):
                    continue  # ignore non-JSON keep-alive frames


class StreamAPI:
    def __init__(self, api_key: str, ws_url: str, request: Any = None) -> None:
        self._api_key = api_key
        self._ws_url = ws_url.rstrip("/")
        self._request = request

    def ticker(self, *, exchange: str, symbol: str, market: Optional[str] = None) -> _Stream:
        """Live ticker frames (fires only when the normalized ticker advances)."""
        return self._open("/ws/ticker", {"exchange": exchange, "symbol": symbol, "market": market})

    def orderbook(self, *, exchange: str, symbol: str, limit: Optional[int] = None,
                  market: Optional[str] = None) -> _Stream:
        """Live order-book frames."""
        return self._open("/ws/orderbook",
                          {"exchange": exchange, "symbol": symbol, "limit": limit, "market": market})

    def ohlcv(self, *, exchange: str, symbol: str, timeframe: str, market: Optional[str] = None) -> _Stream:
        """Live OHLCV candle frames."""
        return self._open("/ws/ohlcv",
                          {"exchange": exchange, "symbol": symbol, "timeframe": timeframe, "market": market})

    def trades(self, *, exchange: str, symbol: str, market: Optional[str] = None) -> _Stream:
        """Live public-trade frames."""
        return self._open("/ws/public-trades", {"exchange": exchange, "symbol": symbol, "market": market})

    def liquidations(self, *, exchange: Optional[str] = None) -> _Stream:
        """Cross-exchange liquidation firehose. Omit exchange for all venues."""
        return self._open("/ws/liquidations", {"exchange": exchange})

    # ── Private feeds (authenticated; ticket-minted) ──────────────────────────

    def strategies(self) -> _Stream:
        """Live strategy events for your account (cycle markers, agent messages,
        approval requests, executions, status). Mints a ticket, opens /ws/strategies."""
        return self._open_private("/ws/strategies", "strategies", {})

    def private(self, *, exchange: str, market: Optional[str] = None,
                api_key_id: Optional[str] = None, key_id: Optional[str] = None,
                symbol: Optional[str] = None) -> _Stream:
        """Live private account feed for one connected exchange key (balance,
        positions, your orders/fills). Pass ``api_key_id`` from ``account.keys()``."""
        return self._open_private("/ws/private", "private", {
            "exchange": exchange, "market": market,
            "apiKeyId": api_key_id, "keyId": key_id, "symbol": symbol,
        })

    def _open(self, path: str, params: Dict[str, Any]) -> _Stream:
        query = {k: v for k, v in params.items() if v is not None}
        query["apiKey"] = self._api_key
        return _Stream(f"{self._ws_url}{path}?{urlencode(query)}")

    def _open_private(self, path: str, stream: str, params: Dict[str, Any]) -> _Stream:
        if self._request is None:
            raise MelayaError("Melaya: private streams require the full client (use m.stream).")
        body = {"stream": stream}
        body.update({k: v for k, v in params.items() if v is not None})
        ticket = self._request("POST", "/api/v1/private/private-ticket", json=body)["wsTicket"]
        return _Stream(f"{self._ws_url}{path}?{urlencode({'wsTicket': ticket})}")
