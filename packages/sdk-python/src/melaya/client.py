"""Core Melaya client."""
from __future__ import annotations

from typing import Any, Dict, Optional

import httpx

from .errors import MelayaError
from .market import MarketAPI
from .account import AccountAPI
from .sim import SimAPI
from .strategies import StrategiesAPI
from .backtest import BacktestAPI
from .trade import TradeAPI
from .stream import StreamAPI

DEFAULT_BASE_URL = "https://api.melaya.org"
DEFAULT_WS_URL = "wss://wss.melaya.org"


class Melaya:
    """Client for the Melaya unified market-data & streaming API.

    Example
    -------
    >>> from melaya import Melaya
    >>> m = Melaya(api_key="mk_...")
    >>> t = m.market.ticker(exchange="binance", symbol="BTC/USDT", market="spot")
    >>> print(t["last"], t["bid"], t["ask"])
    """

    def __init__(
        self,
        api_key: str,
        *,
        base_url: str = DEFAULT_BASE_URL,
        ws_url: str = DEFAULT_WS_URL,
        timeout: float = 30.0,
    ) -> None:
        if not api_key:
            raise ValueError("Melaya: api_key is required (create one at melaya.org -> Settings -> API Keys).")
        if not api_key.startswith("mk_"):
            raise ValueError("Melaya: API keys must be prefixed 'mk_'.")
        self._api_key = api_key
        self._http = httpx.Client(
            base_url=base_url,
            timeout=timeout,
            headers={"Authorization": f"Bearer {api_key}"},
        )
        self.market = MarketAPI(self._request)
        self.account = AccountAPI(self._request)
        self.sim = SimAPI(self._request)
        self.strategies = StrategiesAPI(self._request)
        self.backtest = BacktestAPI(self._request)
        self.trade = TradeAPI(self._request)
        self.stream = StreamAPI(api_key, ws_url, self._request)

    def _request(
        self,
        method: str,
        path: str,
        *,
        params: Optional[Dict[str, Any]] = None,
        json: Any = None,
    ) -> Any:
        query: Dict[str, Any] = {k: v for k, v in (params or {}).items() if v is not None}
        query["apiKey"] = self._api_key
        resp = self._http.request(method, path, params=query, json=json)
        try:
            data = resp.json() if resp.content else None
        except ValueError:
            data = resp.text
        if resp.status_code >= 400:
            code = data.get("error") if isinstance(data, dict) else None
            raise MelayaError(
                f"Melaya API {resp.status_code}" + (f" ({code})" if code else ""),
                status=resp.status_code,
                code=code,
                body=data,
            )
        # The API wraps every payload in an {ok, <data>} envelope. A false `ok`
        # is a request-level failure (an unsupported per-venue operation, or a
        # cold venue) — surface it rather than returning a silent null payload.
        if isinstance(data, dict) and data.get("ok") is False:
            code = data.get("error")
            raise MelayaError(
                "Melaya API request failed" + (f": {code}" if code else ""),
                status=resp.status_code,
                code=code,
                body=data,
            )
        return data

    def close(self) -> None:
        self._http.close()

    def __enter__(self) -> "Melaya":
        return self

    def __exit__(self, *exc: Any) -> None:
        self.close()
