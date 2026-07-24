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
