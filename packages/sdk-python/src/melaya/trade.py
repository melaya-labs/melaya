"""Live trading API — credentialed order placement, account state, and
position management on a CONNECTED exchange.

Every method POSTs to ``https://api.melaya.org/api/v1/private/<op>``; the server
resolves your connected exchange credential (referenced by ``api_key_id`` — see
``account.keys()``) and forwards the call to the venue through Melaya's in-house
Rust engine. Responses share an envelope:
``{ok, exchange, operation, orderId, clientOrderId, payload, data, ...}``.

WARNING: these hit the REAL venue with REAL funds. The write methods
(create_order, cancel_order, amend_order, cancel_all_orders, cancel_plan_orders,
close_position, set_leverage, set_margin_mode, set_position_mode) move money or
change account state. For risk-free testing use the sim (paper) broker or a
paper strategy instead.
"""
from __future__ import annotations

from typing import Any, Callable, Dict, Optional

_Request = Callable[..., Any]


def _clean(d: Dict[str, Any]) -> Dict[str, Any]:
    return {k: v for k, v in d.items() if v is not None}


class TradeAPI:
    def __init__(self, request: _Request) -> None:
        self._request = request

    def _op(self, op: str, body: Dict[str, Any]) -> Dict[str, Any]:
        return self._request("POST", f"/api/v1/private/{op}", json=_clean(body))

    # ── Account state (reads) ────────────────────────────────────────────────

    def balance(self, *, exchange: str, api_key_id: Optional[str] = None, key_id: Optional[str] = None,
                market_type: Optional[str] = None, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Live account balance on a connected venue."""
        return self._op("balance", {"exchange": exchange, "apiKeyId": api_key_id, "keyId": key_id,
                                    "marketType": market_type, "params": params})

    def positions(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                  symbol: Optional[str] = None, params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Live open positions."""
        return self._op("positions", {"exchange": exchange, "apiKeyId": api_key_id, "marketType": market_type,
                                      "symbol": symbol, "params": params})

    def positions_history(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                          symbol: Optional[str] = None) -> Dict[str, Any]:
        """Historical positions (venue-dependent)."""
        return self._op("positions-history", {"exchange": exchange, "apiKeyId": api_key_id,
                                             "marketType": market_type, "symbol": symbol})

    def open_orders(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                    symbol: Optional[str] = None) -> Dict[str, Any]:
        """Resting (open) orders."""
        return self._op("open-orders", {"exchange": exchange, "apiKeyId": api_key_id,
                                       "marketType": market_type, "symbol": symbol})

    def orders(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
               symbol: Optional[str] = None) -> Dict[str, Any]:
        """All orders (open + recent)."""
        return self._op("orders", {"exchange": exchange, "apiKeyId": api_key_id,
                                  "marketType": market_type, "symbol": symbol})

    def closed_orders(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                      symbol: Optional[str] = None) -> Dict[str, Any]:
        """Closed/filled orders."""
        return self._op("closed-orders", {"exchange": exchange, "apiKeyId": api_key_id,
                                        "marketType": market_type, "symbol": symbol})

    def my_trades(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                  symbol: Optional[str] = None) -> Dict[str, Any]:
        """Your trade (fill) history."""
        return self._op("my-trades", {"exchange": exchange, "apiKeyId": api_key_id,
                                     "marketType": market_type, "symbol": symbol})

    def my_trades_history(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                          symbol: Optional[str] = None) -> Dict[str, Any]:
        """Extended trade history (venue-dependent)."""
        return self._op("my-trades-history", {"exchange": exchange, "apiKeyId": api_key_id,
                                            "marketType": market_type, "symbol": symbol})

    def plan_orders(self, *, exchange: str, api_key_id: Optional[str] = None, market_type: Optional[str] = None,
                    symbol: Optional[str] = None) -> Dict[str, Any]:
        """Resting conditional/plan (trigger) orders."""
        return self._op("plan-orders", {"exchange": exchange, "apiKeyId": api_key_id,
                                       "marketType": market_type, "symbol": symbol})

    def leverage(self, *, exchange: str, api_key_id: Optional[str] = None, symbol: Optional[str] = None,
                 market_type: Optional[str] = None) -> Dict[str, Any]:
        """Current leverage for a symbol."""
        return self._op("leverage", {"exchange": exchange, "apiKeyId": api_key_id,
                                    "symbol": symbol, "marketType": market_type})

    def leverage_tiers(self, *, exchange: str, api_key_id: Optional[str] = None, symbol: Optional[str] = None,
                       market_type: Optional[str] = None) -> Dict[str, Any]:
        """Leverage tiers / brackets for a symbol."""
        return self._op("leverage-tiers", {"exchange": exchange, "apiKeyId": api_key_id,
                                         "symbol": symbol, "marketType": market_type})

    # ── Order placement & management (LIVE writes — real funds) ───────────────

    def create_order(self, *, exchange: str, symbol: str, side: str, amount: float,
                     api_key_id: Optional[str] = None, type: str = "market", price: Optional[float] = None,
                     market_type: Optional[str] = None, stop_price: Optional[float] = None,
                     take_profit_price: Optional[float] = None, reduce_only: Optional[bool] = None,
                     leverage: Optional[float] = None, client_order_id: Optional[str] = None,
                     params: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Place a live order on the venue. WARNING: real money."""
        p = dict(params or {})
        if stop_price is not None: p["stopPrice"] = stop_price
        if take_profit_price is not None: p["takeProfitPrice"] = take_profit_price
        if reduce_only is not None: p["reduceOnly"] = reduce_only
        return self._op("create-order", {"exchange": exchange, "apiKeyId": api_key_id, "symbol": symbol,
                                        "side": side, "amount": amount, "type": type, "price": price,
                                        "marketType": market_type, "leverage": leverage,
                                        "clientOrderId": client_order_id, "params": p})

    def cancel_order(self, *, exchange: str, api_key_id: Optional[str] = None, order_id: Optional[str] = None,
                     client_order_id: Optional[str] = None, symbol: Optional[str] = None,
                     market_type: Optional[str] = None) -> Dict[str, Any]:
        """Cancel a live order by id. WARNING."""
        return self._op("cancel-order", {"exchange": exchange, "apiKeyId": api_key_id, "orderId": order_id,
                                        "clientOrderId": client_order_id, "symbol": symbol, "marketType": market_type})

    def amend_order(self, *, exchange: str, api_key_id: Optional[str] = None, order_id: Optional[str] = None,
                    symbol: Optional[str] = None, amount: Optional[float] = None, price: Optional[float] = None) -> Dict[str, Any]:
        """Amend (modify) a live order. WARNING."""
        return self._op("amend-order", {"exchange": exchange, "apiKeyId": api_key_id, "orderId": order_id,
                                       "symbol": symbol, "amount": amount, "price": price})

    def cancel_all_orders(self, *, exchange: str, api_key_id: Optional[str] = None, symbol: Optional[str] = None,
                          market_type: Optional[str] = None) -> Dict[str, Any]:
        """Cancel every open order (optionally scoped to a symbol). WARNING."""
        return self._op("cancel-all-orders", {"exchange": exchange, "apiKeyId": api_key_id,
                                            "symbol": symbol, "marketType": market_type})

    def cancel_plan_orders(self, *, exchange: str, api_key_id: Optional[str] = None, symbol: Optional[str] = None,
                           market_type: Optional[str] = None) -> Dict[str, Any]:
        """Cancel resting plan/trigger orders. WARNING."""
        return self._op("cancel-plan-orders", {"exchange": exchange, "apiKeyId": api_key_id,
                                             "symbol": symbol, "marketType": market_type})

    def close_position(self, *, exchange: str, symbol: str, api_key_id: Optional[str] = None,
                       market_type: Optional[str] = None) -> Dict[str, Any]:
        """Close an open position (market reduce-only). WARNING."""
        return self._op("close-position", {"exchange": exchange, "apiKeyId": api_key_id,
                                         "symbol": symbol, "marketType": market_type})

    def set_leverage(self, *, exchange: str, symbol: str, leverage: float, api_key_id: Optional[str] = None,
                     market_type: Optional[str] = None) -> Dict[str, Any]:
        """Set leverage for a symbol. WARNING."""
        return self._op("set-leverage", {"exchange": exchange, "apiKeyId": api_key_id, "symbol": symbol,
                                        "leverage": leverage, "marketType": market_type})

    def set_margin_mode(self, *, exchange: str, margin_mode: str, api_key_id: Optional[str] = None,
                        symbol: Optional[str] = None, market_type: Optional[str] = None) -> Dict[str, Any]:
        """Set margin mode (cross/isolated). WARNING."""
        return self._op("set-margin-mode", {"exchange": exchange, "apiKeyId": api_key_id, "marginMode": margin_mode,
                                          "symbol": symbol, "marketType": market_type})

    def set_position_mode(self, *, exchange: str, api_key_id: Optional[str] = None, hedged: Optional[bool] = None,
                          mode: Optional[str] = None, market_type: Optional[str] = None) -> Dict[str, Any]:
        """Set position mode (one-way / hedge). WARNING."""
        return self._op("set-position-mode", {"exchange": exchange, "apiKeyId": api_key_id, "hedged": hedged,
                                            "mode": mode, "marketType": market_type})
