"""Evals API — pipeline evaluation runs, summaries, and benchmarks.

Maps to ``/api/v1/private/evals/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> runs = m.evals.list_runs()
>>> summary = m.evals.summary()
>>> detail = m.evals.run_detail(runs[0]["id"])
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional

from .platform_types import JsonDict


class EvalsAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    def list_runs(self) -> List[JsonDict]:
        """List eval run results for the caller's tenant."""
        return self._request("GET", "/api/v1/private/evals/runs")

    def summary(self) -> JsonDict:
        """Get aggregate summary of eval results."""
        return self._request("GET", "/api/v1/private/evals/summary")

    def run_detail(self, run_id: str) -> JsonDict:
        """Get detailed results for a specific eval run."""
        return self._request("GET", f"/api/v1/private/evals/runs/{run_id}")

    def compare(self, *, run_ids: Optional[List[str]] = None, **kwargs: Any) -> JsonDict:
        """Compare results across multiple eval runs."""
        params: Dict[str, Any] = {**kwargs}
        if run_ids is not None:
            params["runIds"] = run_ids
        return self._request("GET", "/api/v1/private/evals/compare", params=params if params else None)

    def memory_graph(self) -> JsonDict:
        """Get memory graph visualization data for eval runs."""
        return self._request("GET", "/api/v1/private/evals/memory-graph")

    def run_memory(self, run_id: str) -> JsonDict:
        """Get memory usage for a specific eval run."""
        return self._request("GET", f"/api/v1/private/evals/runs/{run_id}/memory")

    def crew_memory(self, *, pipeline: str, project: str) -> JsonDict:
        """Get agent crew memory for a pipeline.

        Args:
            pipeline: The pipeline name.
            project: The project name.
        """
        return self._request("GET", "/api/v1/private/evals/crew-memory",
                             params={"pipeline": pipeline, "project": project})

    def benchmarks(self) -> JsonDict:
        """Get benchmark scores across eval runs."""
        return self._request("GET", "/api/v1/private/evals/benchmarks")
