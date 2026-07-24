"""WebSocket streaming API (async).

Each method returns an async-iterable stream of normalized frames:

    async for frame in m.stream.ticker(exchange="binance", symbol="BTC/USDT", market="spot"):
        print(frame["last"])

Requires the optional `websockets` dependency: ``pip install "melaya[stream]"``.
"""
from __future__ import annotations

import asyncio
import json
import logging
from typing import Any, AsyncIterator, Callable, Dict, Optional
from urllib.parse import urlencode

from .errors import MelayaError

try:  # optional dependency
    import websockets  # type: ignore
except ImportError:  # pragma: no cover
    websockets = None  # type: ignore

logger = logging.getLogger(__name__)

# Reconnect backoff: start at 1s, double up to a 30s cap. Reset to the base
# after any successful connection so a brief blip doesn't leave us slow to
# recover on the next one.
_RECONNECT_BASE_SEC = 1.0
_RECONNECT_MAX_SEC = 30.0
# After this many CONSECUTIVE failed connection attempts the stream stops
# retrying and raises the last error — permanent failures (bad credentials,
# revoked key, dead endpoint) must surface, not spin forever.
_MAX_CONSECUTIVE_FAILURES = 5


class _Stream:
    """An async-iterable stream of JSON frames from a Melaya WebSocket.

    The stream auto-reconnects with capped exponential backoff on disconnect:
    a dropped connection (network blip, server restart, idle timeout) does NOT
    silently terminate the iterator — it reconnects and keeps yielding frames.
    After ``_MAX_CONSECUTIVE_FAILURES`` failed connection attempts in a row,
    the last error is raised as :class:`MelayaError` instead of retrying
    forever; any successful connection resets the counter. The connect URL is
    produced by a factory on EVERY attempt, so private streams mint a fresh
    one-shot ``wsTicket`` for each reconnect. Cancel the consuming task (or
    break out of the ``async for``) to stop.
    """

    def __init__(self, url_factory: Callable[[], str]) -> None:
        self._url_factory = url_factory

    async def __aiter__(self) -> AsyncIterator[Dict[str, Any]]:
        if websockets is None:
            raise MelayaError(
                "Melaya: streaming requires the 'websockets' package. "
                "Install with: pip install \"melaya[stream]\""
            )
        backoff = _RECONNECT_BASE_SEC
        failures = 0
        while True:
            try:
                # Re-derive the URL every attempt: private streams must mint a
                # fresh wsTicket per connection (tickets are short-lived and
                # one-shot). The factory may do blocking HTTP — run it off-loop.
                url = await asyncio.to_thread(self._url_factory)
                async with websockets.connect(url) as ws:
                    backoff = _RECONNECT_BASE_SEC  # healthy connection — reset
                    failures = 0
                    async for raw in ws:
                        try:
                            yield json.loads(raw)
                        except (ValueError, TypeError):
                            continue  # ignore non-JSON keep-alive frames
                # Clean server close of the read loop → reconnect after backoff.
            except asyncio.CancelledError:
                # Consumer cancelled/broke out — do not reconnect.
                raise
            except Exception as exc:  # noqa: BLE001 — reconnect on transport error
                failures += 1
                if failures >= _MAX_CONSECUTIVE_FAILURES:
                    # The URL carries the credential; include only the error
                    # TYPE in the message (the cause is chained for debugging).
                    raise MelayaError(
                        f"Melaya: stream failed after {failures} consecutive "
                        f"connection attempts (last error: {type(exc).__name__})"
                    ) from exc
                # The URL carries the credential; never log it. Log only the
                # error type/message so a key can't leak into logs.
                logger.debug(
                    "Melaya: stream disconnected (%s); reconnecting in %.1fs",
                    type(exc).__name__,
                    backoff,
                )
            await asyncio.sleep(backoff)
            backoff = min(backoff * 2, _RECONNECT_MAX_SEC)


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
        url = f"{self._ws_url}{path}?{urlencode(query)}"
        return _Stream(lambda: url)

    def _open_private(self, path: str, stream: str, params: Dict[str, Any]) -> _Stream:
        if self._request is None:
            raise MelayaError("Melaya: private streams require the full client (use m.stream).")
        body = {"stream": stream}
        body.update({k: v for k, v in params.items() if v is not None})

        def _mint_url() -> str:
            # wsTickets are short-lived and one-shot: mint a FRESH one for
            # every (re)connect instead of baking a stale ticket into the URL.
            ticket = self._request("POST", "/api/v1/private/private-ticket", json=body)["wsTicket"]
            return f"{self._ws_url}{path}?{urlencode({'wsTicket': ticket})}"

        return _Stream(_mint_url)
