"""Projects API — create and list agent projects.

Maps to ``/api/v1/private/projects``. Projects are the top-level namespace
for pipelines, connectors, and team membership.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> projects = m.projects.list()
>>> p = m.projects.create(name="trading-crew", description="My agentic trading crew")
>>> m.projects.rename(old_name="trading-crew", new_name="alpha-crew")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict


class ProjectsAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def list(self) -> List[JsonDict]:
        """List all projects the authenticated user can access (owned + team member)."""
        return self._request("GET", "/api/v1/private/projects")

    def create(self, *, name: str, description: Optional[str] = None) -> JsonDict:
        """Create a new agent project."""
        body: Dict[str, Any] = {"name": name}
        if description is not None:
            body["description"] = description
        return self._request("POST", "/api/v1/private/projects", json=body)

    def rename(self, *, old_name: str, new_name: str) -> JsonDict:
        """Rename a project (body: oldName, newName)."""
        return self._request("PATCH", "/api/v1/private/projects/rename",
                             json={"oldName": old_name, "newName": new_name})

    def runner_projects(self) -> List[JsonDict]:
        """Get projects list (runner-facing, authenticated)."""
        return self._request("GET", "/api/v1/private/projects/runner")
