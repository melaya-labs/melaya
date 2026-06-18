"""Paper-trading (sim broker) API.

The sim broker synthesises fills from Melaya's live ticker tape and keeps a
virtual wallet per strategy — no venue-side state changes, no exchange
credentials needed. Every call is scoped to a ``strategy_id``.
"""
from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

_Request = Callable[..., Any]


def _as_list(r: Any, key: str) -> List[Any]:
    if isinstance(r, list):
        return r
    if isinstance(r, dict):
        return r.get(key, []) or []
    return []


class SimAPI:
    def __init__(self, request: _Request) -> None:
        self._request = request

    def list_accounts(self) -> List[Any]:
        """Paper accounts (one virtual wallet per paper strategy)."""
        return _as_list(self._request("GET", "/api/v1/private/sim/list-accounts"), "accounts")

    def balance(self, *, strategy_id: str, asset: Optional[str] = None) -> Dict[str, Any]:
        """Virtual balance (equity, realized/unrealized PnL, free/used)."""
        return self._request("GET", "/api/v1/private/sim/balance",
                             params={"strategy_id": strategy_id, "asset": asset})

    def positions(self, *, strategy_id: str) -> List[Dict[str, Any]]:
        """Open paper positions for a strategy."""
        return _as_list(self._request("GET", "/api/v1/private/sim/positions",
                                      params={"strategy_id": strategy_id}), "positions")

    def open_orders(self, *, strategy_id: str) -> List[Dict[str, Any]]:
        """Resting paper orders for a strategy."""
        return _as_list(self._request("GET", "/api/v1/private/sim/open-orders",
                                      params={"strategy_id": strategy_id}), "orders")

    def my_trades(self, *, strategy_id: str) -> List[Dict[str, Any]]:
        """Filled paper trades for a strategy."""
        return _as_list(self._request("GET", "/api/v1/private/sim/my-trades",
                                      params={"strategy_id": strategy_id}), "trades")

    def create_order(
        self,
        *,
        strategy_id: str,
        exchange: str,
        symbol: str,
        side: str,
        amount: float,
        type: str = "market",
        price: Optional[float] = None,
        market: Optional[str] = None,
        leverage: Optional[float] = None,
        reduce_only: Optional[bool] = None,
        sl_price: Optional[float] = None,
        tp_price: Optional[float] = None,
        client_order_id: Optional[str] = None,
        params: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Place a paper order. Fills synthesise from the live ticker; nothing hits the venue."""
        body = {
            "strategy_id": strategy_id,
            "exchange": exchange,
            "symbol": symbol,
            "side": side,
            "amount": amount,
            "order_type": type,
            "orderType": type,
            "price": price,
            "market": market,
            "market_type": market,
            "leverage": leverage,
            "reduceOnly": reduce_only,
            "slPrice": sl_price,
            "tpPrice": tp_price,
            "client_order_id": client_order_id,
            "clientOrderId": client_order_id,
            "params": params,
        }
        return self._request("POST", "/api/v1/private/sim/create-order",
                             json={k: v for k, v in body.items() if v is not None})

    def cancel_order(self, *, strategy_id: str, order_id: str,
                     symbol: Optional[str] = None, exchange: Optional[str] = None) -> Dict[str, Any]:
        """Cancel a resting paper order."""
        return self._request("POST", "/api/v1/private/sim/cancel-order",
                             json={"strategy_id": strategy_id, "order_id": order_id,
                                   "orderId": order_id, "symbol": symbol, "exchange": exchange})
