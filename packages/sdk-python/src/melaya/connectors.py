"""Project Connectors API — manage credentials at project scope.

Maps to ``/api/v1/private/projects/:project/connectors/*``. Project-scoped
connectors are isolated per project, letting separate projects use different
API keys for the same service.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> m.connectors.set("my-project", "openai", value="sk-...")
>>> services = m.connectors.connected_services("my-project")
>>> handle = m.connectors.env_handle("my-project")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional
from urllib.parse import quote

from .platform_types import JsonDict


class ConnectorsAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def connected_services(self, project: str) -> List[JsonDict]:
        """List connected services for a project."""
        return self._request("GET", f"/api/v1/private/projects/{project}/connectors/services")

    def set(
        self,
        project: str,
        service: str,
        *,
        value: str,
        key: Optional[str] = None,
        label: Optional[str] = None,
    ) -> JsonDict:
        """Store a connector credential at project scope."""
        body: Dict[str, Any] = {"value": value}
        if key is not None:
            body["key"] = key
        if label is not None:
            body["label"] = label
        return self._request("PUT", f"/api/v1/private/projects/{project}/connectors/{service}", json=body)

    def delete(self, project: str, service: str) -> JsonDict:
        """Delete a project-scoped connector credential."""
        return self._request("DELETE", f"/api/v1/private/projects/{project}/connectors/{service}")

    def env_handle(self, project: str) -> JsonDict:
        """Get short-lived env-handle token for project-scoped credentials.

        The runner uses this token to decrypt credentials without a full session.
        """
        return self._request("POST", f"/api/v1/private/projects/{project}/connectors/env-handle")

    def google_oauth_start(self, project: str, **kwargs: Any) -> JsonDict:
        """Start Google OAuth flow for project-scoped connector."""
        return self._request("POST", f"/api/v1/private/projects/{project}/connectors/google/oauth",
                             json=kwargs if kwargs else None)

    def apply_personal(
        self, project: str, service: str, *, google_capabilities: Optional[List[str]] = None
    ) -> JsonDict:
        """Share the caller's own personal connector into a project.

        Requires editor/owner on the project. The caller's personal connector
        credential for ``service`` becomes usable by pipelines in ``project``.
        """
        body: Dict[str, Any] = {}
        if google_capabilities is not None:
            body["googleCapabilities"] = google_capabilities
        return self._request(
            "POST", f"/api/v1/private/projects/{project}/connectors/{service}/apply-personal", json=body
        )

    # ── Several accounts per project connector (owner only for writes) ──────────

    @staticmethod
    def _accounts_path(project: str, service: str) -> str:
        return f"/api/v1/private/projects/{quote(project, safe='')}/connectors/{quote(service, safe='')}/accounts"

    def accounts(self, project: str, service: str) -> List[JsonDict]:
        """List the accounts connected to one project connector (labels and ids only).

        Each item is ``{"id", "label", "isDefault", "createdAt"}`` (``createdAt``
        may be ``None``).
        """
        return self._request("GET", self._accounts_path(project, service))

    def add_account(
        self,
        project: str,
        service: str,
        *,
        fields: Dict[str, str],
        label: Optional[str] = None,
        current_label: Optional[str] = None,
        make_default: Optional[bool] = None,
    ) -> List[JsonDict]:
        """Add another account to a project connector (owner; the connection is tested first).

        Same body as ``m.credentials.add_account()``. Returns the updated list.
        """
        body: Dict[str, Any] = {"fields": fields}
        if label is not None:
            body["label"] = label
        if current_label is not None:
            body["currentLabel"] = current_label
        if make_default is not None:
            body["makeDefault"] = make_default
        return self._request("POST", self._accounts_path(project, service), json=body)

    def set_default_account(self, project: str, service: str, account_id: str) -> List[JsonDict]:
        """Choose which account the project connector uses (owner). Returns the updated list."""
        return self._request(
            "PUT", f"{self._accounts_path(project, service)}/default", json={"accountId": account_id}
        )

    def rename_account(self, project: str, service: str, account_id: str, label: str) -> List[JsonDict]:
        """Rename one account of a project connector (owner, max 80 chars)."""
        return self._request(
            "PUT",
            f"{self._accounts_path(project, service)}/{quote(account_id, safe='')}",
            json={"label": label},
        )

    def remove_account(self, project: str, service: str, account_id: str) -> List[JsonDict]:
        """Remove one account from a project connector (owner). Returns the remaining list."""
        return self._request(
            "DELETE", f"{self._accounts_path(project, service)}/{quote(account_id, safe='')}"
        )

    def shared_by(self, project: str) -> List[JsonDict]:
        """List connectors shared into a project by their owners (via ``apply_personal``)."""
        return self._request("GET", f"/api/v1/private/projects/{project}/connectors/shared-by")

    def google_status(self, project: str) -> JsonDict:
        """Get Google account connection status for a project's connectors."""
        return self._request("GET", f"/api/v1/private/projects/{project}/connectors/google/status")

    def google_set_default(self, project: str, capability: str, account_id: str) -> JsonDict:
        """Set the default connected Google account for one capability, project-scoped.

        ``capability`` is one of: gmail, calendar, drive, sheets, docs,
        search_console, youtube, google_ads, analytics, meet, slides.
        ``account_id`` is a 24-hex-char id.
        """
        return self._request(
            "PUT",
            f"/api/v1/private/projects/{project}/connectors/google/default",
            json={"capability": capability, "accountId": account_id},
        )

    def google_disconnect(self, project: str, account_id: str, *, capability: Optional[str] = None) -> JsonDict:
        """Disconnect a Google account (or one capability of it), project-scoped.

        Omit ``capability`` to disconnect the account entirely from this project.
        """
        body: Dict[str, Any] = {"accountId": account_id}
        if capability is not None:
            body["capability"] = capability
        return self._request(
            "DELETE", f"/api/v1/private/projects/{project}/connectors/google/access", json=body
        )

    def db_test_start(self, project: str, service: str, *, credentials: Optional[Dict[str, str]] = None) -> JsonDict:
        """Start a database connectivity probe from the user's runner, project-scoped.

        Returns ``{"sessionId": ...}``; poll with ``db_test_status()``.
        """
        body: Dict[str, Any] = {"service": service}
        if credentials is not None:
            body["credentials"] = credentials
        return self._request("POST", f"/api/v1/private/projects/{project}/connectors/db-test", json=body)

    def db_test_status(self, project: str, session_id: str) -> JsonDict:
        """Poll a project-scoped database connectivity probe session."""
        return self._request("GET", f"/api/v1/private/projects/{project}/connectors/db-test/{session_id}")
