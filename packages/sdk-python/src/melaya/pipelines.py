"""Pipelines API — overview, runs, traces, cron schedules, and pipeline lifecycle.

Maps to ``/api/v1/private/overview/pipeline*``, ``/api/v1/private/runs/:runId/traces``,
``/api/v1/private/pipeline-schedule``, and ``/api/v1/private/pipelines/*``.

Example
-------
>>> from melaya import Melaya
>>> m = Melaya(api_key="mk_...")
>>> runs = m.pipelines.list(project="my-project", limit=20)
>>> traces = m.pipelines.traces(run_id="run-123")
>>> m.pipelines.upsert_schedule("my-project", "nightly-report", cron="0 2 * * *")
>>> cfg = m.pipelines.create(name="my-pipe", project="my-project")
>>> result = m.pipelines.run("my-pipe", project="my-project")
"""
from __future__ import annotations

from typing import Any, Dict, List, Optional, TypedDict
from urllib.parse import quote

from .platform_types import JsonDict


class TraceSummary(TypedDict, total=False):
    traceId: str
    traceName: str
    startTime: str
    endTime: str
    status: Any
    spanCount: int
    totalTokens: int


class TracesPage(TypedDict):
    """Paginated envelope returned by ``GET /api/v1/private/runs/:runId/traces``."""
    list: List[TraceSummary]
    total: int
    page: int
    pageSize: int


class DeleteTracesResult(TypedDict, total=False):
    """Return shape for DELETE /api/v1/private/runs/:runId/traces."""
    deletedSpans: int
    requestedTraces: int


class PipelinesAPI:
    def __init__(self, request: Any) -> None:
        self._request = request

    # ── Overview ────────────────────────────────────────────────────────────────

    def overview(self) -> JsonDict:
        """Dashboard overview: usage stats, active strategies, recent runs."""
        return self._request("GET", "/api/v1/private/overview")

    def model_prices(self) -> JsonDict:
        """Get pricing data for available AI models."""
        return self._request("GET", "/api/v1/private/overview/model-prices")

    def chart_data(self) -> JsonDict:
        """Get chart data for overview dashboard (cost/usage over time)."""
        return self._request("GET", "/api/v1/private/overview/chart")

    def cost_breakdown(self) -> JsonDict:
        """Get cost breakdown by model/provider."""
        return self._request("GET", "/api/v1/private/overview/cost-breakdown")

    def count(self) -> JsonDict:
        """Get count of pipeline runs grouped by status."""
        return self._request("GET", "/api/v1/private/overview/pipeline-count")

    def list(
        self,
        *,
        project: Optional[str] = None,
        pipeline_name: Optional[str] = None,
        status: Optional[str] = None,
        limit: Optional[int] = None,
        offset: Optional[int] = None,
        page: Optional[int] = None,
    ) -> List[JsonDict]:
        """Get paginated list of pipeline runs."""
        params: Dict[str, Any] = {}
        if project is not None:
            params["project"] = project
        if pipeline_name is not None:
            params["pipelineName"] = pipeline_name
        if status is not None:
            params["status"] = status
        if limit is not None:
            params["limit"] = limit
        if offset is not None:
            params["offset"] = offset
        if page is not None:
            params["page"] = page
        return self._request("GET", "/api/v1/private/overview/pipelines", params=params)

    def recent(self) -> List[JsonDict]:
        """Get most recent pipeline runs for dashboard widget."""
        return self._request("GET", "/api/v1/private/overview/pipelines/recent")

    # ── Traces ──────────────────────────────────────────────────────────────────

    def traces(self, run_id: str) -> TracesPage:
        """List traces for a run.

        Returns a paginated envelope ``{"list": [...], "total": int, "page": int, "pageSize": int}``.
        The envelope is nested under a ``"data"`` key in the raw API response;
        this method unwraps it so callers receive the inner page dict directly.
        """
        result = self._request("GET", f"/api/v1/private/runs/{quote(run_id, safe='')}/traces")
        # Server returns { data: { list, total, page, pageSize } }
        if isinstance(result, dict) and "data" in result:
            return result["data"]
        return result

    def trace(self, run_id: str, trace_id: str) -> JsonDict:
        """Get a single trace by ID."""
        return self._request(
            "GET",
            f"/api/v1/private/runs/{quote(run_id, safe='')}/traces/{quote(trace_id, safe='')}",
        )

    def trace_stats(self, run_id: str, trace_id: str) -> JsonDict:
        """Get statistics for a specific trace."""
        return self._request(
            "GET",
            f"/api/v1/private/runs/{quote(run_id, safe='')}/traces/{quote(trace_id, safe='')}/stats",
        )

    def delete_traces(self, run_id: str) -> DeleteTracesResult:
        """Delete all traces for a run by runId.

        Sends ``DELETE /api/v1/private/runs/:runId/traces`` with no request body.

        Returns
        -------
        DeleteTracesResult
            ``{"deletedSpans": int}`` (``requestedTraces`` is included when available).
        """
        return self._request("DELETE", f"/api/v1/private/runs/{quote(run_id, safe='')}/traces")

    # ── Schedule ────────────────────────────────────────────────────────────────

    def list_schedules(self) -> List[JsonDict]:
        """List all pipeline schedules accessible to the caller."""
        return self._request("GET", "/api/v1/private/pipeline-schedule")

    def get_schedule(self, project: str, pipeline_name: str) -> JsonDict:
        """Get schedule status for a pipeline."""
        return self._request(
            "GET",
            f"/api/v1/private/pipeline-schedule/{quote(project, safe='')}/{quote(pipeline_name, safe='')}",
        )

    def upsert_schedule(
        self,
        project: str,
        pipeline_name: str,
        *,
        cron: str,
        config: Optional[Dict[str, Any]] = None,
    ) -> JsonDict:
        """Create or update a pipeline schedule (cron expression + optional pipeline config)."""
        body: Dict[str, Any] = {"cron": cron}
        if config is not None:
            body["config"] = config
        return self._request(
            "PUT",
            f"/api/v1/private/pipeline-schedule/{quote(project, safe='')}/{quote(pipeline_name, safe='')}",
            json=body,
        )

    def pause_schedule(self, project: str, pipeline_name: str) -> JsonDict:
        """Pause a pipeline schedule."""
        return self._request(
            "POST",
            f"/api/v1/private/pipeline-schedule/{quote(project, safe='')}/{quote(pipeline_name, safe='')}/pause",
        )

    def resume_schedule(self, project: str, pipeline_name: str) -> JsonDict:
        """Resume a paused pipeline schedule."""
        return self._request(
            "POST",
            f"/api/v1/private/pipeline-schedule/{quote(project, safe='')}/{quote(pipeline_name, safe='')}/resume",
        )

    # ── Pipeline lifecycle ───────────────────────────────────────────────────────

    def list_pipelines(self) -> List[JsonDict]:
        """List all pipeline configurations accessible to the caller.

        Returns
        -------
        list of pipeline config dicts under the ``pipelines`` key.
        """
        result = self._request("GET", "/api/v1/private/pipelines")
        if isinstance(result, dict) and "pipelines" in result:
            return result["pipelines"]
        return result

    def create(
        self,
        *,
        name: str,
        project: str,
        description: Optional[str] = None,
        **config: Any,
    ) -> JsonDict:
        """Create a new pipeline configuration.

        Parameters
        ----------
        name:
            Unique pipeline name within the project.
        project:
            Project slug the pipeline belongs to.
        description:
            Optional human-readable description.
        **config:
            Additional pipeline configuration fields forwarded to the API.
        """
        body: Dict[str, Any] = {"name": name, "project": project, **config}
        if description is not None:
            body["description"] = description
        return self._request("POST", "/api/v1/private/pipelines", json=body)

    def get(self, name: str, *, project: Optional[str] = None) -> JsonDict:
        """Fetch a pipeline configuration by name.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        project:
            Optional project filter query parameter.
        """
        params: Dict[str, Any] = {}
        if project is not None:
            params["project"] = project
        return self._request("GET", f"/api/v1/private/pipelines/{quote(name, safe='')}", params=params)

    def update(self, name: str, *, config: Dict[str, Any], project: str) -> JsonDict:
        """Update an existing pipeline configuration.

        Parameters
        ----------
        name:
            Pipeline name to update (URL-encoded automatically).
        config:
            New pipeline configuration dict to persist.
        project:
            Project the pipeline belongs to.
        """
        body: Dict[str, Any] = {"config": config, "project": project}
        return self._request("PUT", f"/api/v1/private/pipelines/{quote(name, safe='')}", json=body)

    def remove(self, name: str, *, project: str) -> JsonDict:
        """Delete a pipeline configuration.

        Parameters
        ----------
        name:
            Pipeline name to delete (URL-encoded automatically).
        project:
            Project the pipeline belongs to (sent as query parameter).
        """
        return self._request(
            "DELETE",
            f"/api/v1/private/pipelines/{quote(name, safe='')}",
            params={"project": project},
        )

    def run(
        self,
        name: str,
        *,
        project: Optional[str] = None,
        execution_target: Optional[str] = None,
        studio_url: Optional[str] = None,
        env_overrides: Optional[Dict[str, str]] = None,
    ) -> JsonDict:
        """Trigger a pipeline run.

        Parameters
        ----------
        name:
            Pipeline name to run (URL-encoded automatically).
        project:
            Optional project override.
        execution_target:
            Optional execution target override: local-runner or cloud-spawn.
        studio_url:
            Optional Studio URL override.
        env_overrides:
            Optional environment variable overrides for this run.

        Returns
        -------
        dict with ``run_id`` and ``queued`` keys.
        """
        body: Dict[str, Any] = {}
        if project is not None:
            body["project"] = project
        if execution_target is not None:
            body["executionTarget"] = execution_target
        if studio_url is not None:
            body["studio_url"] = studio_url
        if env_overrides is not None:
            body["env_overrides"] = env_overrides
        return self._request("POST", f"/api/v1/private/pipelines/{quote(name, safe='')}/run", json=body)

    def run_ids(self, name: str) -> List[str]:
        """List all run IDs for a pipeline.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).

        Returns
        -------
        list of run ID strings under the ``run_ids`` key.
        """
        result = self._request("GET", f"/api/v1/private/pipelines/{quote(name, safe='')}/runs")
        if isinstance(result, dict) and "run_ids" in result:
            return result["run_ids"]
        return result

    def run_status(self, name: str, run_id: str) -> JsonDict:
        """Get the status of a specific pipeline run.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        run_id:
            Run ID (URL-encoded automatically).

        Returns
        -------
        dict with ``runId``, ``status``, ``createdAt``, ``executionTarget``, and ``cost``.
        """
        return self._request(
            "GET",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/runs/{quote(run_id, safe='')}",
        )

    def cancel_run(self, name: str, run_id: str) -> JsonDict:
        """Cancel a running or queued pipeline run.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        run_id:
            Run ID to cancel (URL-encoded automatically).
        """
        return self._request(
            "DELETE",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/runs/{quote(run_id, safe='')}",
        )

    def outputs(self, name: str) -> List[JsonDict]:
        """List all output artifacts produced by a pipeline.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        """
        return self._request("GET", f"/api/v1/private/pipelines/{quote(name, safe='')}/outputs")

    def output(self, name: str, path: str, *, download: bool = False) -> JsonDict:
        """Fetch a single output artifact by path.

        Each segment of ``path`` is URL-encoded individually and joined with
        ``/`` so that slashes in individual path components are preserved as
        separators rather than escaped.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        path:
            Artifact path (e.g. ``"results/report.json"``). Each ``/``-delimited
            segment is percent-encoded independently.
        download:
            When ``True``, appends ``?download=1`` to request a download URL.
        """
        encoded_path = "/".join(quote(segment, safe="") for segment in path.split("/"))
        params: Dict[str, Any] = {}
        if download:
            params["download"] = 1
        return self._request(
            "GET",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/outputs/{encoded_path}",
            params=params,
        )

    def preview_code(self, config: Dict[str, Any]) -> JsonDict:
        """Generate a code preview for a pipeline configuration without saving it.

        Parameters
        ----------
        config:
            Pipeline configuration dict to preview.
        """
        return self._request("POST", "/api/v1/private/pipelines/preview-code", json=config)

    def tools(self) -> JsonDict:
        """Get the tools registry available to pipelines."""
        return self._request("GET", "/api/v1/private/pipelines/tools")

    def subagents(self) -> JsonDict:
        """Get the subagents registry available to pipelines."""
        return self._request("GET", "/api/v1/private/pipelines/subagents")

    def instantiate_template(
        self,
        template_id: str,
        *,
        name: str,
        project: str,
        overrides: Optional[Dict[str, Any]] = None,
    ) -> JsonDict:
        """Instantiate a pipeline from a template.

        Parameters
        ----------
        template_id:
            Template ID to instantiate (URL-encoded automatically).
        name:
            Name for the new pipeline.
        project:
            Project to create the pipeline in.
        overrides:
            Optional configuration overrides applied on top of the template defaults.

        Returns
        -------
        dict with a ``pipeline`` key containing the new pipeline config.
        """
        body: Dict[str, Any] = {"name": name, "project": project}
        if overrides is not None:
            body["overrides"] = overrides
        return self._request(
            "POST",
            f"/api/v1/private/templates/{quote(template_id, safe='')}/instantiate",
            json=body,
        )

    def build_with_ai(self, brief: Dict[str, Any]) -> JsonDict:
        """Build a pipeline configuration from a natural language brief using AI.

        Parameters
        ----------
        brief:
            Dict describing the desired pipeline behaviour. Shape is open-ended
            and passed directly to the AI build endpoint.

        Returns
        -------
        Generated pipeline configuration dict.
        """
        return self._request("POST", "/api/v1/private/ai/build-pipeline/sync", json=brief)
