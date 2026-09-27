"""Connector Tools API — discover and call connector tools directly, the same
surface the MCP server exposes to models (``services/mcp/tools/connectors.ts``).

Maps to ``/api/v1/private/connector-tools/*``. Not to be confused with
``connectors`` (project-scoped credential storage): this module discovers and
CALLS tools once a service is connected — it never accepts or returns a
credential value.

Reads run immediately. Writes default to an approval card raised in the
Melaya app (``approval="required"``, the same card the Assistant raises);
``approval="none"`` runs the write immediately and is audit-logged. Tools
that move money or trade are always refused, under both approval modes.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> m.connector_tools.services()
>>> m.connector_tools.search("unread email")
>>> m.connector_tools.call("gmail_list_messages", {"max_results": 5})
>>> outcome = m.connector_tools.call_and_wait(
...     "gmail_send", {"to": "a@b.c", "subject": "Hi", "body": "..."}
... )
"""
from __future__ import annotations

import time
from typing import Any, Dict, Optional

from .platform_types import ConnectorApproval, JsonDict


class ConnectorToolsAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def services(self) -> JsonDict:
        """List connected services + tool counts.

        Returns ``{"services": [...], "builtIn": "melaya_core",
        "toolCounts": {service: {"readTools": n, "writeTools": n}}}``.
        """
        return self._request("GET", "/api/v1/private/connector-tools/services")

    def search(self, q: str, *, limit: Optional[int] = None) -> JsonDict:
        """Discover tools by plain business keywords (e.g. ``"unread email"``).

        ``limit`` is 1-50, default 15 (clamped server-side). Returns
        ``{"query": q, "services": [...], "tools": [ToolInfo, ...]}`` where
        each ``ToolInfo`` is ``{name, service, description, readOnly,
        movesMoney, params}``.
        """
        params: Dict[str, Any] = {"q": q}
        if limit is not None:
            params["limit"] = limit
        return self._request("GET", "/api/v1/private/connector-tools/search", params=params)

    def describe(self, tool: str) -> JsonDict:
        """Full description + parameters for one tool (a ``ToolInfo``).

        Raises ``MelayaError`` (404) when the tool is unknown, or not
        unlocked by any of your connected services.
        """
        return self._request("GET", f"/api/v1/private/connector-tools/tools/{tool}")

    def test(self, service: str) -> JsonDict:
        """Test the STORED credential for a connected service.

        Returns ``{"service", "success": bool, "message"}``. Raises
        ``MelayaError`` (504, code ``"timeout"``) if the service does not
        answer within 30 seconds.
        """
        return self._request("POST", "/api/v1/private/connector-tools/test", json={"service": service})

    def connect(self, service: str) -> JsonDict:
        """Start connecting a service. Never accepts a secret.

        Returns ``{"service", "kind": "oauth" | "oauth_unavailable" |
        "interactive_login" | "api_key", "authorizationUrl"?, "connectUrl"?,
        "message"}``. For ``"oauth"`` the user opens ``authorizationUrl``;
        for the other kinds, they finish on the Melaya Connectors page.
        """
        return self._request("POST", "/api/v1/private/connector-tools/connect", json={"service": service})

    def call(
        self,
        tool: str,
        args: Optional[Dict[str, Any]] = None,
        *,
        approval: ConnectorApproval = "required",
    ) -> JsonDict:
        """Call a connector tool.

        A read tool (or a write with ``approval="none"``) runs immediately
        and returns ``{"status": "done", "tool", "readOnly", "result"}``.

        A write with ``approval="required"`` (the default) is staged as the
        same approval card the Assistant raises in the Melaya app; the call
        returns HTTP **202** — ``{"status": "pending_approval", "tool",
        "requestId", "message"}`` — which is returned here like any other
        successful response, not raised as an error. Poll the outcome with
        ``call_status()``, or use ``call_and_wait()`` to block until it
        settles.

        Tools that move money or trade are refused under BOTH approval
        modes and raise ``MelayaError`` (403, code
        ``"money_moving_requires_app_approval"``); they run only from the
        Melaya app.
        """
        body: Dict[str, Any] = {"tool": tool, "args": args or {}, "approval": approval}
        return self._request("POST", "/api/v1/private/connector-tools/call", json=body)

    def call_status(self, request_id: str) -> JsonDict:
        """Outcome of a staged write, by the ``requestId`` returned from ``call()``.

        Returns one of:
          - ``{"requestId", "tool", "status": "pending" | "running" | "expired"}``
          - ``{"requestId", "tool", "status": "done", "ok": bool, "result"?, "error"?}``
          - ``{"requestId", "tool", "status": "rejected", "reason"?}``

        Raises ``MelayaError`` (404) when ``requestId`` is unknown or expired
        beyond recall.
        """
        return self._request("GET", f"/api/v1/private/connector-tools/calls/{request_id}")

    def call_and_wait(
        self,
        tool: str,
        args: Optional[Dict[str, Any]] = None,
        *,
        approval: ConnectorApproval = "required",
        poll_interval: float = 3.0,
        timeout: float = 600.0,
    ) -> JsonDict:
        """``call()`` a tool and block until a staged write settles.

        A read (or a write with ``approval="none"``) returns immediately,
        exactly like ``call()``. A write with ``approval="required"`` (the
        default) is staged; this then polls ``call_status()`` — every
        ``poll_interval`` seconds (default 3) — until the status is
        ``"done"``, ``"rejected"``, or ``"expired"``, or until ``timeout``
        seconds have elapsed (default 600 = 10 minutes), whichever comes
        first, and returns that final ``call_status()`` outcome.

        This is synchronous-blocking, matching the rest of this SDK's REST
        surface. It never raises on a settled ``"rejected"``/``"expired"``
        status or an ``ok: False`` result — those are meaningful return
        values, not transport errors.
        """
        outcome = self.call(tool, args, approval=approval)
        if outcome.get("status") != "pending_approval":
            return outcome
        request_id = outcome["requestId"]
        deadline = time.monotonic() + timeout
        status_out = self.call_status(request_id)
        while status_out.get("status") not in ("done", "rejected", "expired"):
            if time.monotonic() >= deadline:
                break
            time.sleep(poll_interval)
            status_out = self.call_status(request_id)
        return status_out
