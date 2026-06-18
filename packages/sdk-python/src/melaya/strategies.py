"""Strategies API — launch, control, and inspect trading strategies.

A strategy is a server-managed runner (the Trading Engine, or an Agentic
Trading Crew) that trades a universe on a cadence with server-side SL/TP and
safety rails. Launch in paper mode (``dry_run=True``) or live
(``dry_run=False``, which requires a connected exchange key).
"""
from __future__ import annotations

from typing import Any, Callable, Dict, List, Optional

_Request = Callable[..., Any]


class StrategiesAPI:
    def __init__(self, request: _Request) -> None:
        self._request = request

    def list(self) -> List[Dict[str, Any]]:
        """Every strategy you own (running, paused, paper, and live)."""
        return self._request("GET", "/api/v1/strategies/list")["strategies"]

    def get(self, strategy_id: str) -> Dict[str, Any]:
        """A single strategy by id."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}")["strategy"]

    def create(
        self,
        *,
        name: str,
        strategy_type: str,
        exchange: str,
        market: Optional[str] = None,
        symbol: Optional[str] = None,
        api_key_id: Optional[str] = None,
        params: Optional[Dict[str, Any]] = None,
        runtime_mode: Optional[str] = None,
        dry_run: bool = True,
        key_bindings: Optional[Dict[str, Any]] = None,
    ) -> Dict[str, Any]:
        """Launch a strategy. ``dry_run=True`` is paper; live needs ``api_key_id``. Returns the new id."""
        body = {
            "name": name,
            "strategyType": strategy_type,
            "exchange": exchange,
            "market": market,
            "symbol": symbol,
            "apiKeyId": api_key_id,
            "params": params,
            "runtimeMode": runtime_mode,
            "dryRun": dry_run,
            "keyBindings": key_bindings,
        }
        return self._request("POST", "/api/v1/strategies",
                             json={k: v for k, v in body.items() if v is not None})

    def pause(self, strategy_id: str) -> Dict[str, Any]:
        """Pause a running strategy."""
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/pause")

    def resume(self, strategy_id: str) -> Dict[str, Any]:
        """Resume a paused strategy."""
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/resume")

    def stop(self, strategy_id: str) -> Dict[str, Any]:
        """Stop a strategy and tear down its runner."""
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/stop")

    def delete(self, strategy_id: str) -> Dict[str, Any]:
        """Soft-delete a strategy."""
        return self._request("DELETE", f"/api/v1/strategies/{strategy_id}")

    def update_params(self, strategy_id: str, params: Dict[str, Any]) -> Dict[str, Any]:
        """Update a running strategy's params (universe, cadence, risk caps)."""
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/update-params", json=params)

    def status(self, strategy_id: str) -> Dict[str, Any]:
        """Live runtime status of a strategy's runner."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/status")

    def performance(self, strategy_id: str) -> List[Any]:
        """Performance series for a strategy (equity, PnL over time)."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/performance")["rows"]

    def executions(self, strategy_id: str) -> List[Any]:
        """Execution (order) rows for a strategy."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/executions")["rows"]

    def trades(self, strategy_id: str) -> List[Any]:
        """Trade (fill) rows for a strategy."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/trades")["rows"]

    def logs(self, strategy_id: str) -> List[Any]:
        """Log rows for a strategy (cycle markers, persona messages, errors)."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/logs")["rows"]

    # ── AI parameter optimizer ────────────────────────────────────────────────

    def ai_opt_start(self, strategy_id: str, *, param_bounds: Dict[str, Any],
                     objective: str = "sharpe", max_iterations: int = 3,
                     require_approval: Optional[bool] = None) -> Dict[str, Any]:
        """Kick off an AI-driven parameter optimization. Returns the run id."""
        body = {"paramBounds": param_bounds, "objective": objective, "maxIterations": max_iterations}
        if require_approval is not None:
            body["requireApproval"] = require_approval
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/ai-opt/start", json=body)

    def ai_opt_status(self, strategy_id: str) -> Dict[str, Any]:
        """Current optimization status for a strategy."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/ai-opt/status")

    def ai_opt_approve(self, strategy_id: str, body: Optional[Dict[str, Any]] = None) -> Dict[str, Any]:
        """Approve and apply the optimizer's proposed params to the running strategy."""
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/ai-opt/approve", json=body or {})

    def ai_opt_stop(self, strategy_id: str) -> Dict[str, Any]:
        """Stop an in-progress optimization."""
        return self._request("POST", f"/api/v1/strategies/{strategy_id}/ai-opt/stop")

    def ai_opt_runs(self, strategy_id: str) -> Any:
        """Past optimization runs for a strategy."""
        return self._request("GET", f"/api/v1/strategies/{strategy_id}/ai-opt/runs")
