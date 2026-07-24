"""Team API — manage project team membership, roles, and invitations.

Maps to ``/api/v1/private/projects/:project/members/*`` and
``/api/v1/private/team/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> members = m.team.list_members("my-project")
>>> m.team.invite("my-project", username="alice")
>>> link = m.team.create_invite_link("my-project")
>>> m.team.accept_invite(token=link["token"])
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict, TeamRole


class TeamAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def list_members(self, project: str) -> List[JsonDict]:
        """List members of a project team."""
        return self._request("GET", f"/api/v1/private/projects/{project}/members")

    def invite(self, project: str, *, username: str) -> JsonDict:
        """Invite a user to a project team by username."""
        return self._request("POST", f"/api/v1/private/projects/{project}/members/invite",
                             json={"username": username})

    def create_invite_link(self, project: str) -> JsonDict:
        """Create a shareable invite link for a project."""
        return self._request("POST", f"/api/v1/private/projects/{project}/invite-link")

    def accept_invite(self, *, token: str) -> JsonDict:
        """Accept a project invite using the token from an invite link."""
        return self._request("POST", "/api/v1/private/team/invite/accept", json={"token": token})

    def update_member_role(self, project: str, user_id: str, *, role: TeamRole) -> JsonDict:
        """Update a team member's role in a project."""
        return self._request("PATCH", f"/api/v1/private/projects/{project}/members/{user_id}",
                             json={"role": role})

    def remove_member(self, project: str, user_id: str) -> JsonDict:
        """Remove a member from a project team."""
        return self._request("DELETE", f"/api/v1/private/projects/{project}/members/{user_id}")

    # ── Pipeline visibility ──────────────────────────────────────────────────────

    def get_pipeline_visibility(self, project: str, pipeline: str) -> JsonDict:
        """Get visibility settings for a pipeline within a project."""
        return self._request("GET", f"/api/v1/private/projects/{project}/pipelines/{pipeline}/visibility")

    def set_pipeline_visibility(self, project: str, pipeline: str, **body: Any) -> JsonDict:
        """Set pipeline visibility within a project."""
        return self._request("PUT", f"/api/v1/private/projects/{project}/pipelines/{pipeline}/visibility",
                             json=body)
