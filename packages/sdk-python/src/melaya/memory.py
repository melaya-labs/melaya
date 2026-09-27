"""Memory API — edit and delete cross-run persistent crew memory entries.

Maps to ``/api/v1/private/memory/crew/*``. Crew memory is keyed by pipeline
name (not a run id) and scoped to the caller's own memory directory; read it
via ``m.evals.crew_memory(pipeline=..., project=...)``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> m.memory.edit_entry("my-pipe", "entry_123", topic="renamed topic")
>>> m.memory.delete_entry("my-pipe", "entry_123")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict


class MemoryAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def edit_entry(
        self,
        pipeline: str,
        entry_id: str,
        *,
        project: Optional[str] = None,
        topic: Optional[str] = None,
        content: Optional[str] = None,
        tags: Optional[List[str]] = None,
    ) -> JsonDict:
        """Edit one persisted cross-run crew memory entry.

        Parameters
        ----------
        pipeline:
            Pipeline / crew name the entry belongs to.
        entry_id:
            The memory entry's ID.
        project:
            Optional project to disambiguate the pipeline name.
        topic, content, tags:
            Patch fields — only the ones passed are updated; omit a field to
            leave it unchanged.
        """
        patch: Dict[str, Any] = {}
        if topic is not None:
            patch["topic"] = topic
        if content is not None:
            patch["content"] = content
        if tags is not None:
            patch["tags"] = tags
        body: Dict[str, Any] = {"pipeline": pipeline, "entryId": entry_id, "patch": patch}
        if project is not None:
            body["project"] = project
        return self._request("POST", "/api/v1/private/memory/crew/edit", json=body)

    def delete_entry(self, pipeline: str, entry_id: str, *, project: Optional[str] = None) -> JsonDict:
        """Delete one persisted cross-run crew memory entry.

        Parameters
        ----------
        pipeline:
            Pipeline / crew name the entry belongs to.
        entry_id:
            The memory entry's ID.
        project:
            Optional project to disambiguate the pipeline name.
        """
        body: Dict[str, Any] = {"pipeline": pipeline, "entryId": entry_id}
        if project is not None:
            body["project"] = project
        return self._request("POST", "/api/v1/private/memory/crew/delete", json=body)
