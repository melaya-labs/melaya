"""HITL (Human-in-the-Loop) API — list, approve, and reject pending tool-call approvals.

Maps to ``/api/v1/private/hitl/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> pending = m.hitl.pending()
>>> for req in pending:
...     m.hitl.approve(req["requestId"], comment="Looks good")
>>> m.hitl.bulk_decide(request_ids=["r1", "r2"], decision="approved")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import HitlDecision, JsonDict


class HitlAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def pending(self) -> List[JsonDict]:
        """List all pending HITL tool-call approvals for the authenticated user."""
        return self._request("GET", "/api/v1/private/hitl/approvals/pending")

    def history(self, *, limit: Optional[int] = None, offset: Optional[int] = None) -> List[JsonDict]:
        """List historical HITL approval decisions."""
        params: Dict[str, Any] = {}
        if limit is not None:
            params["limit"] = limit
        if offset is not None:
            params["offset"] = offset
        return self._request("GET", "/api/v1/private/hitl/approvals/history", params=params)

    def approve(self, request_id: str, *, comment: Optional[str] = None) -> JsonDict:
        """Approve a pending HITL tool-call approval."""
        body: Dict[str, Any] = {}
        if comment is not None:
            body["comment"] = comment
        return self._request("POST", f"/api/v1/private/hitl/approvals/{request_id}/approve",
                             json=body if body else None)

    def reject(self, request_id: str, *, comment: Optional[str] = None) -> JsonDict:
        """Reject a pending HITL tool-call approval."""
        body: Dict[str, Any] = {}
        if comment is not None:
            body["comment"] = comment
        return self._request("POST", f"/api/v1/private/hitl/approvals/{request_id}/reject",
                             json=body if body else None)

    def bulk_decide(
        self,
        *,
        request_ids: List[str],
        decision: HitlDecision,
        comment: Optional[str] = None,
    ) -> JsonDict:
        """Bulk approve or reject multiple pending tool calls in one call."""
        body: Dict[str, Any] = {"requestIds": request_ids, "decision": decision}
        if comment is not None:
            body["comment"] = comment
        return self._request("POST", "/api/v1/private/hitl/approvals/bulk", json=body)

    # ── Run-level inspection ─────────────────────────────────────────────────────

    def run_tool_stats(self, run_id: str) -> JsonDict:
        """Get tool-call statistics for a specific pipeline run."""
        return self._request("GET", f"/api/v1/private/hitl/runs/{run_id}/tool-stats")

    def run_messages(
        self,
        run_id: str,
        *,
        limit: Optional[int] = None,
        cursor: Optional[str] = None,
    ) -> List[JsonDict]:
        """Get paginated messages for a pipeline run."""
        params: Dict[str, Any] = {}
        if limit is not None:
            params["limit"] = limit
        if cursor is not None:
            params["cursor"] = cursor
        return self._request("GET", f"/api/v1/private/hitl/runs/{run_id}/messages", params=params)

    def run_tool_stats_by_agent(self, run_id: str) -> JsonDict:
        """Get tool-call stats broken down by agent for a run."""
        return self._request("GET", f"/api/v1/private/hitl/runs/{run_id}/tool-stats/by-agent")

    def run_tool_calls(self, run_id: str) -> List[JsonDict]:
        """Get all tool calls for a pipeline run."""
        return self._request("GET", f"/api/v1/private/hitl/runs/{run_id}/tool-calls")
