"""Templates API — create, manage, and share pipeline templates.

Maps to ``/api/v1/private/user-templates/*`` and
``/api/v1/private/templates/*``.

Templates bundle a pipeline definition into a reusable, shareable artifact.
Visibility levels: ``private`` (only you) → ``team`` → ``community`` →
``assigned`` (explicitly assigned to users or projects).

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> templates = m.templates.list()
>>> t = m.templates.save(name="My report", payload={"pipeline": "..."})
>>> m.templates.share(t["id"], visibility="team")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict, TemplateVisibility


class TemplatesAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def list(self) -> List[JsonDict]:
        """List all templates visible to the caller — own + team + community + assigned."""
        return self._request("GET", "/api/v1/private/user-templates")

    def list_global(self) -> List[JsonDict]:
        """List all community-visibility (global) templates."""
        return self._request("GET", "/api/v1/private/templates/global")

    def list_validated(self) -> List[str]:
        """List IDs of all validated (platform-approved) templates."""
        return self._request("GET", "/api/v1/private/templates/validated")

    def save(
        self,
        *,
        name: str,
        payload: Dict[str, Any],
        description: Optional[str] = None,
        category: Optional[str] = None,
    ) -> JsonDict:
        """Create a new private user template."""
        body: Dict[str, Any] = {"name": name, "payload": payload}
        if description is not None:
            body["description"] = description
        if category is not None:
            body["category"] = category
        return self._request("POST", "/api/v1/private/user-templates", json=body)

    def update(
        self,
        template_id: str,
        *,
        name: Optional[str] = None,
        description: Optional[str] = None,
        category: Optional[str] = None,
        payload: Optional[Dict[str, Any]] = None,
    ) -> JsonDict:
        """Update name/description/category/payload of a private user template."""
        body: Dict[str, Any] = {}
        if name is not None:
            body["name"] = name
        if description is not None:
            body["description"] = description
        if category is not None:
            body["category"] = category
        if payload is not None:
            body["payload"] = payload
        return self._request("PATCH", f"/api/v1/private/user-templates/{template_id}", json=body)

    def duplicate(self, template_id: str, *, new_name: Optional[str] = None) -> JsonDict:
        """Duplicate a readable template into the caller's private library."""
        body: Dict[str, Any] = {}
        if new_name is not None:
            body["newName"] = new_name
        return self._request("POST", f"/api/v1/private/user-templates/{template_id}/duplicate",
                             json=body if body else None)

    def delete(self, template_id: str) -> JsonDict:
        """Delete (or soft-demote if shared) a template."""
        return self._request("DELETE", f"/api/v1/private/user-templates/{template_id}")

    def share(self, template_id: str, *, visibility: TemplateVisibility) -> JsonDict:
        """Change the visibility of a template (private/team/community/assigned)."""
        return self._request("PUT", f"/api/v1/private/user-templates/{template_id}/visibility",
                             json={"visibility": visibility})

    # ── Assignments ──────────────────────────────────────────────────────────────

    def list_assignments(self, template_id: str) -> List[JsonDict]:
        """List all assignments (users/projects) for a template."""
        return self._request("GET", f"/api/v1/private/user-templates/{template_id}/assignments")

    def assign(self, template_id: str, *,
               user_id: Optional[str] = None, project_id: Optional[str] = None) -> JsonDict:
        """Assign a template to a user or project.

        Exactly one of ``user_id`` or ``project_id`` must be provided (both are
        UUIDs). The server contract is ``{ userId }`` XOR ``{ projectId }`` in
        the JSON body.

        Args:
            template_id: The template to assign.
            user_id: UUID of the user to assign the template to.
            project_id: UUID of the project to assign the template to.
        """
        body = self._assignment_body(user_id=user_id, project_id=project_id)
        return self._request("POST", f"/api/v1/private/user-templates/{template_id}/assignments",
                             json=body)

    def unassign(self, template_id: str, *,
                 user_id: Optional[str] = None, project_id: Optional[str] = None) -> JsonDict:
        """Remove an assignment from a template.

        Exactly one of ``user_id`` or ``project_id`` must be provided (both are
        UUIDs). The DELETE endpoint reads ``userId``/``projectId`` from QUERY
        PARAMS only — request bodies on DELETE are ignored by the server.
        """
        params = self._assignment_body(user_id=user_id, project_id=project_id)
        return self._request("DELETE", f"/api/v1/private/user-templates/{template_id}/assignments",
                             params=params)

    @staticmethod
    def _assignment_body(*, user_id: Optional[str], project_id: Optional[str]) -> Dict[str, Any]:
        """Build the ``{ userId } XOR { projectId }`` assignment payload."""
        if (user_id is None) == (project_id is None):
            raise ValueError(
                "Melaya: templates.assign/unassign requires exactly one of user_id or project_id."
            )
        return {"userId": user_id} if user_id is not None else {"projectId": project_id}

    def share_targets(self) -> List[JsonDict]:
        """List projects the caller is a member of (for the share target picker)."""
        return self._request("GET", "/api/v1/private/user-templates/share-targets")
