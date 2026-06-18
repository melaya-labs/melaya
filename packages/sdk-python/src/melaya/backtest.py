"""Backtest API — run strategies against historical data on the Rust engine.

Start a single run or a parameter sweep (grid / random), poll the job, then
pull metrics, the equity curve, and the trade list. All backtests run natively
on Melaya's in-house engine.
"""
from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

_Request = Callable[..., Any]


class BacktestAPI:
    def __init__(self, request: _Request) -> None:
        self._request = request

    def start(self, body: Dict[str, Any]) -> Dict[str, Any]:
        """Start a backtest. Returns the job id(s); poll with ``job()``.

        Pass ``mode='grid_sweep'`` / ``'random_sweep'`` with ``paramRanges`` to
        fan out a parameter search; omit ``mode`` for a single run.
        """
        return self._request("POST", "/api/v1/private/backtest/start", json=body)

    def job(self, job_id: str) -> Dict[str, Any]:
        """Job status + progress."""
        return self._request("GET", f"/api/v1/private/backtest/jobs/{job_id}")

    def results(self, job_id: str) -> Dict[str, Any]:
        """Metrics, equity curve, and OHLCV for a completed job."""
        return self._request("GET", f"/api/v1/private/backtest/results/{job_id}")["result"]

    def trades(self, job_id: str, *, limit: Optional[int] = None, offset: Optional[int] = None) -> List[Any]:
        """The trade list for a completed job (default 500, max 5000 per call)."""
        return self._request("GET", f"/api/v1/private/backtest/trades/{job_id}",
                             params={"limit": limit, "offset": offset})["trades"]

    def sweep(self, parent_id: str, *, objective: Optional[str] = None,
              limit: Optional[int] = None) -> Dict[str, Any]:
        """Ranked children of a sweep parent (default objective: sharpe DESC)."""
        return self._request("GET", f"/api/v1/private/backtest/sweep/{parent_id}",
                             params={"objective": objective, "limit": limit})

    def list(self, *, limit: Optional[int] = None, offset: Optional[int] = None) -> List[Any]:
        """Your backtest jobs, newest first."""
        return self._request("GET", "/api/v1/private/backtest",
                             params={"limit": limit, "offset": offset})["data"]["jobs"]

    def favorites(self, *, limit: Optional[int] = None, offset: Optional[int] = None) -> List[Any]:
        """Your favorited backtest jobs (Forge tier and above)."""
        return self._request("GET", "/api/v1/private/backtest/favorites",
                             params={"limit": limit, "offset": offset})["data"]["jobs"]

    def funding_range(self, *, exchange: str, symbol: str) -> Optional[int]:
        """Earliest funding-rate timestamp available for exchange+symbol (ms, or None)."""
        return self._request("GET", "/api/v1/private/backtest/funding-range",
                             params={"exchange": exchange, "symbol": symbol})["earliest_ms"]

    def cancel(self, job_id: str) -> Dict[str, Any]:
        """Cancel an in-flight job."""
        return self._request("POST", f"/api/v1/private/backtest/{job_id}/cancel")

    def delete(self, job_id: str) -> Dict[str, Any]:
        """Soft-delete a single job."""
        return self._request("DELETE", f"/api/v1/private/backtest/{job_id}")

    def delete_all(self) -> Dict[str, Any]:
        """Soft-delete every non-favorited job. Returns the count deleted."""
        return self._request("DELETE", "/api/v1/private/backtest")
