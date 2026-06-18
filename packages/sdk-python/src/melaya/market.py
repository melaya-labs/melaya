"""REST market-data API — normalized across all 70+ venues.

The API wraps payloads in an {ok, <data>} envelope; these methods unwrap to the
inner data and the client raises on `ok: false`.
"""
from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

_Request = Callable[..., Any]


class MarketAPI:
    def __init__(self, request: _Request) -> None:
        self._request = request

    # ── Reference ───────────────────────────────────────────────────────────

    def list_exchanges(self) -> List[Dict[str, Any]]:
        """List the exchanges Melaya supports right now (the source of truth)."""
        return self._request("GET", "/api/v1/market/list-exchanges")["exchanges"]

    # ── Market data (GET) ───────────────────────────────────────────────────

    def ticker(self, *, exchange: str, symbol: str, market: Optional[str] = None) -> Dict[str, Any]:
        """Best bid/ask, last, and 24h aggregates for one symbol."""
        return self._request("GET", "/api/v1/market/ticker",
                             params={"exchange": exchange, "symbol": symbol, "market": market})["ticker"]

    def orderbook(self, *, exchange: str, symbol: str, limit: Optional[int] = None,
                  market: Optional[str] = None) -> Dict[str, Any]:
        """Order book to a given depth."""
        return self._request("GET", "/api/v1/market/orderbook",
                             params={"exchange": exchange, "symbol": symbol, "limit": limit, "market": market})["orderbook"]

    def ohlcv(self, *, exchange: str, symbol: str, timeframe: str, limit: Optional[int] = None,
              market: Optional[str] = None) -> List[List[float]]:
        """OHLCV candles: each is [timestamp, open, high, low, close, volume]."""
        return self._request("GET", "/api/v1/market/ohlcv",
                             params={"exchange": exchange, "symbol": symbol, "timeframe": timeframe,
                                     "limit": limit, "market": market})["candles"]

    def trades(self, *, exchange: str, symbol: str, market: Optional[str] = None) -> List[Dict[str, Any]]:
        """Recent public trades."""
        return self._request("GET", "/api/v1/market/trades",
                             params={"exchange": exchange, "symbol": symbol, "market": market})["trades"]

    def markets(self, *, exchange: str) -> List[Any]:
        """Tradable markets on a venue."""
        return self._request("GET", "/api/v1/market/markets", params={"exchange": exchange})["markets"]

    def currencies(self, *, exchange: str) -> List[Any]:
        """Listed currencies on a venue. (Not supported on every venue.)"""
        return self._request("GET", "/api/v1/market/currencies", params={"exchange": exchange})["currencies"]

    def status(self, *, exchange: str) -> Dict[str, Any]:
        """Operational status: ok / maintenance / degraded."""
        return self._request("GET", "/api/v1/market/status", params={"exchange": exchange})["status"]

    def time(self, *, exchange: str) -> Any:
        """Exchange server time."""
        return self._request("GET", "/api/v1/market/time", params={"exchange": exchange})["time"]

    # ── Batch / derivatives (POST) ──────────────────────────────────────────

    def tickers(self, *, exchange: str, symbols: List[str], market: Optional[str] = None) -> Dict[str, Any]:
        """Tickers for many symbols on one venue in a single call. Keyed by symbol."""
        return self._request("POST", "/api/v1/market/tickers",
                             json={"exchange": exchange, "symbols": symbols, "market": market})["tickers"]

    def funding_rates(self, *, exchange: str, symbols: List[str], market: Optional[str] = None) -> Dict[str, Any]:
        """Latest funding rates for perpetuals. Keyed by symbol."""
        return self._request("POST", "/api/v1/market/funding-rates",
                             json={"exchange": exchange, "symbols": symbols, "market": market})["rates"]

    def funding_rate_history(self, *, exchange: str, symbol: str, hours: Optional[int] = None,
                             market: Optional[str] = None) -> List[Any]:
        """Funding-rate history."""
        return self._request("POST", "/api/v1/market/funding-rate-history",
                             json={"exchange": exchange, "symbol": symbol, "hours": hours, "market": market})["history"]

    def open_interest(self, *, exchange: str, symbols: List[str], market: Optional[str] = None) -> Dict[str, Any]:
        """Open interest for one or more perpetuals. Keyed by symbol."""
        return self._request("POST", "/api/v1/market/open-interest",
                             json={"exchange": exchange, "symbols": symbols, "market": market})["openInterest"]

    def open_interest_history(self, *, exchange: str, symbol: str, hours: Optional[int] = None,
                              market: Optional[str] = None) -> List[Any]:
        """Open-interest history."""
        return self._request("POST", "/api/v1/market/open-interest-history",
                             json={"exchange": exchange, "symbol": symbol, "hours": hours, "market": market})["history"]

    def instruments(self, *, exchange: str, market: Optional[str] = None) -> Any:
        """Instrument list + trading constraints (tick size, min notional, qty step)."""
        return self._request("POST", "/api/v1/market/instruments",
                             json={"exchange": exchange, "market": market})

    def liquidation_events(self, *, exchange: Optional[str] = None, symbol: Optional[str] = None,
                           since_ms: Optional[int] = None, limit: Optional[int] = None) -> List[Dict[str, Any]]:
        """Cross-exchange liquidation events (historical query)."""
        return self._request("POST", "/api/v1/market/liquidation-events",
                             json={"exchange": exchange, "symbol": symbol, "sinceMs": since_ms, "limit": limit})["events"]

    def ohlcv_multi(self, *, exchange: str, symbols: List[str], timeframe: str,
                    limit: Optional[int] = None, market: Optional[str] = None) -> Dict[str, Any]:
        """Multi-symbol OHLCV in one call. Candle arrays keyed by symbol."""
        return self._request("POST", "/api/v1/market/ohlcv-multi",
                             json={"exchange": exchange, "symbols": symbols, "timeframe": timeframe,
                                   "limit": limit, "market": market})["perSymbol"]

    def market_constraints(self, *, exchange: str, symbol: str, market: Optional[str] = None) -> Any:
        """Trading constraints for one symbol (tick size, min notional, qty step, leverage)."""
        return self._request("POST", "/api/v1/market/market-constraints",
                             json={"exchange": exchange, "symbol": symbol, "market": market})["constraints"]

    def funding_rate_history_multi(self, *, exchanges: List[str], symbol: str,
                                   hours: Optional[int] = None) -> Dict[str, Any]:
        """Funding-rate history for one symbol across several venues. Keyed by exchange."""
        return self._request("POST", "/api/v1/market/funding-rate-history-multi",
                             json={"exchanges": exchanges, "symbol": symbol, "hours": hours})["perExchange"]

    def open_interest_history_multi(self, *, exchanges: List[str], symbol: str,
                                    hours: Optional[int] = None) -> Dict[str, Any]:
        """Open-interest history for one symbol across several venues. Keyed by exchange."""
        return self._request("POST", "/api/v1/market/open-interest-history-multi",
                             json={"exchanges": exchanges, "symbol": symbol, "hours": hours})["perExchange"]

    def prediction_markets(self, *, venue: str = "polymarket") -> List[Dict[str, Any]]:
        """Prediction-market listings for a venue (polymarket, kalshi, drift_pm, sxbet, azuro, overtime)."""
        return self._request("POST", "/api/v1/market/pm-markets", json={"venue": venue})["markets"]

    def catalog_counts(self) -> Dict[str, Any]:
        """Live platform catalog counts (agentic tools, subagents, by category). Public."""
        return self._request("GET", "/api/v1/public/catalog-counts")
