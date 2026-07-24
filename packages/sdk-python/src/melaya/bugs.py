"""Bugs API — submit and track bug reports.

Maps to ``/api/v1/private/bugs/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> report = m.bugs.create(title="Something broke", description="Details...")
>>> my_bugs = m.bugs.list_mine()
>>> m.bugs.add_comment(report["id"], comment="Follow-up info")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict


class BugsAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def create(self, *, title: str, description: Optional[str] = None, **kwargs: Any) -> JsonDict:
        """Submit a bug report (user-facing feedback form)."""
        body: Dict[str, Any] = {"title": title, **kwargs}
        if description is not None:
            body["description"] = description
        return self._request("POST", "/api/v1/private/bugs", json=body)

    def list_mine(self) -> List[JsonDict]:
        """List bug reports submitted by the caller."""
        return self._request("GET", "/api/v1/private/bugs/mine")

    def get(self, bug_id: str) -> JsonDict:
        """Get a single bug report by ID."""
        return self._request("GET", f"/api/v1/private/bugs/{bug_id}")

    def add_comment(self, bug_id: str, *, comment: str) -> JsonDict:
        """Add a comment to a bug report."""
        return self._request("POST", f"/api/v1/private/bugs/{bug_id}/comments", json={"comment": comment})

    def list_notifications(self) -> List[JsonDict]:
        """List unread bug-related notifications for the caller."""
        return self._request("GET", "/api/v1/private/bugs/notifications")

    def mark_notifications_read(self, *, notification_ids: Optional[List[str]] = None) -> JsonDict:
        """Mark bug notifications as read."""
        body: Dict[str, Any] = {}
        if notification_ids is not None:
            body["notificationIds"] = notification_ids
        return self._request("POST", "/api/v1/private/bugs/notifications/read",
                             json=body if body else None)
