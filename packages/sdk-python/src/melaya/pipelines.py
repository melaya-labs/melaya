"""Pipelines API — overview, runs, traces, cron schedules, and pipeline lifecycle.

Maps to ``/api/v1/private/overview/pipeline*``, ``/api/v1/private/runs/:runId/traces``,
``/api/v1/private/pipeline-schedule``, and ``/api/v1/private/pipelines/*``.

Config shape
------------
A pipeline's run is generated ONLY from ``config["steps"]`` — a top-level
``config["agents"]`` list alone produces an EMPTY pipeline. Each agent step
carries its own full agent definition inline::

    {
        "steps": [{
            "kind": "agent",
            "agent": {
                "name": "researcher",
                "role": "Research analyst",
                "instruction": "Summarize the latest news on X.",
                "model": {"provider": "anthropic", "name": "claude-sonnet-4-6"},
                "agent_tools": ["web_search"],
                "human_approval_tools": [],
            },
        }],
    }

There is no ``prompt`` field — the two prompt fields are ``instruction`` (the
task) and ``system_prompt_override``. Other notable config fields:
``hitl_mode`` (``"safe"`` default | ``"autonomous"`` | ``"payments_only"``;
only ``"safe"`` honours each agent's ``human_approval_tools``),
``connector_source`` (``"personal"`` | ``"project"``), ``force_local_runner``,
and ``inputs`` (declares the run-input fields ``run()``'s ``run_inputs``
populates).

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

from typing import Any, Dict, IO, List, Optional, TypedDict, Union
from urllib.parse import quote

from .platform_types import JsonDict


def _read_bytes(file: Union[bytes, bytearray, "IO[bytes]"]) -> bytes:
    """Normalize a file input (raw bytes or a binary file-like object) to bytes."""
    if isinstance(file, (bytes, bytearray)):
        return bytes(file)
    data = file.read()
    if isinstance(data, str):
        data = data.encode("utf-8")
    return data


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
    def __init__(self, request: Any, post_multipart: Any = None, get_bytes: Any = None) -> None:
        self._request = request
        # Only used by upload_run_file/upload_doc/upload_retrieval_doc (multipart)
        # and run_input_file (raw bytes). Optional so PipelinesAPI can still be
        # constructed standalone (e.g. in tests) with just a request callable.
        self._post_multipart = post_multipart
        self._get_bytes = get_bytes

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
        """Fetch a pipeline by name.

        Returns an ENVELOPE, not a bare config: ``{name, client, config, code,
        docs}``. To edit and save, mutate ``envelope["config"]`` and pass that
        to ``update()`` — see the example below.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        project:
            Optional project filter query parameter.

        Example
        -------
        >>> envelope = m.pipelines.get("my-pipe", project="my-project")
        >>> envelope["config"]["steps"][0]["agent"]["model"] = {
        ...     "provider": "anthropic", "name": "claude-opus-4-8",
        ... }
        >>> m.pipelines.update("my-pipe", config=envelope["config"], project="my-project")
        """
        params: Dict[str, Any] = {}
        if project is not None:
            params["project"] = project
        return self._request("GET", f"/api/v1/private/pipelines/{quote(name, safe='')}", params=params)

    def update(self, name: str, *, config: Dict[str, Any], project: str) -> JsonDict:
        """Update an existing pipeline configuration.

        ``config`` should be the full config dict — typically
        ``envelope["config"]`` from ``get()`` with the desired edits applied
        (e.g. ``config["steps"][0]["agent"]["model"]``), not a partial patch.

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
        run_inputs: Optional[Dict[str, Any]] = None,
    ) -> JsonDict:
        """Trigger a pipeline run.

        Parameters
        ----------
        name:
            Pipeline name to run (URL-encoded automatically).
        project:
            Optional project override.
        execution_target:
            Used ONLY for the tier check at request time (e.g. gating
            cloud-spawn to paid tiers) — it does NOT decide where the run
            actually executes. That is decided by the pipeline's own stored
            config (its configured local model providers / ``force_local_runner``).
        studio_url:
            Optional Studio URL override.
        env_overrides:
            Optional environment variable overrides layered over the caller's
            stored credentials for this run only. ``MEL_*`` and ``MELAYA_*``
            keys are stripped server-side — they cannot be overridden this way.
        run_inputs:
            Optional per-run inputs matching the pipeline's declared
            ``inputs[]``: ``{"brief": str, "values": {name: value, ...}}``.
            A value in ``values`` may be a plain scalar, or a file reference
            produced by ``upload_run_file()`` / already available as
            ``{"file_id": "..."}``, a remote ``{"url": "..."}`` (≤25 MB), or an
            inline ``{"base64": "...", "name": "..."}`` (≤7 MB).

        Returns
        -------
        dict with ``run_id`` and ``queued`` keys, and ``run_inputs`` echoed
        back when the run was submitted with any.
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
        if run_inputs is not None:
            body["run_inputs"] = run_inputs
        return self._request("POST", f"/api/v1/private/pipelines/{quote(name, safe='')}/run", json=body)

    def upload_run_file(
        self,
        name: str,
        key: str,
        file: Union[bytes, bytearray, "IO[bytes]"],
        *,
        project: Optional[str] = None,
        filename: str = "file",
        content_type: Optional[str] = None,
    ) -> JsonDict:
        """Upload a single-use run-input file ahead of ``run()``.

        POSTs ``multipart/form-data`` (single field ``file``) to
        ``/api/v1/private/pipelines/{name}/run-files?key={key}``. The returned
        ``file_id`` is single-use and valid for 24 hours — reference it from
        ``run_inputs["values"]`` as ``{"file_id": file_id}``.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        key:
            The run-input key this file is being uploaded for (query parameter).
        file:
            Raw file bytes, or a binary file-like object (must support ``.read()``).
        project:
            Optional project filter query parameter.
        filename:
            Filename to send in the multipart part (default ``"file"``).
        content_type:
            Optional MIME type for the multipart part.

        Returns
        -------
        dict with a ``file_id`` string among other fields.
        """
        if self._post_multipart is None:
            raise RuntimeError("PipelinesAPI was constructed without a post_multipart transport.")
        params: Dict[str, Any] = {"key": key}
        if project is not None:
            params["project"] = project
        return self._post_multipart(
            f"/api/v1/private/pipelines/{quote(name, safe='')}/run-files",
            params=params,
            field_name="file",
            data=_read_bytes(file),
            filename=filename,
            content_type=content_type,
        )

    def set_inputs(self, name: str, *, project: str, inputs: List[Dict[str, Any]]) -> JsonDict:
        """Replace ONLY the pipeline's declared run inputs (``config.inputs``).

        The rest of the config is not touched. Max 30 declarations; an empty
        list removes them all. Editor or owner only. Raises on HTTP 422
        ``run_inputs_invalid: <reason>`` for a bad declaration. Keep keys
        stable: ``{{inputs.<key>}}`` placeholders in instructions use them.

        Each declaration: ``key`` (lower snake case, max 40, ``brief`` is
        reserved), ``label``, ``type`` (``text``, ``long_text``, ``number``,
        ``boolean``, ``choice``, ``url``, ``email``, ``file``, ``files``),
        ``required``, ``default`` (not for files), ``options`` (``choice``),
        ``accept`` (files: ``pdf``, ``office``, ``spreadsheet``, ``image``,
        ``text``), ``description``.

        Returns ``{"name": ..., "inputs": [...]}``: the normalized declaration.
        """
        body: Dict[str, Any] = {"inputs": inputs, "project": project}
        return self._request("PUT", f"/api/v1/private/pipelines/{quote(name, safe='')}/inputs", json=body)

    def run_inputs(self, name: str, run_id: str) -> JsonDict:
        """Get the brief/values/files a run was submitted with.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        run_id:
            Run ID — 16 hex chars (URL-encoded automatically).
        """
        return self._request(
            "GET",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/runs/{quote(run_id, safe='')}/inputs",
        )

    def run_input_file(self, name: str, run_id: str, index: int) -> bytes:
        """Download one run-input file by index, as raw bytes.

        This is a binary download — the response is NOT JSON and is returned
        as-is (do not attempt to JSON-parse it).

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        run_id:
            Run ID — 16 hex chars (URL-encoded automatically).
        index:
            File index within the run's inputs, 0-99.
        """
        if self._get_bytes is None:
            raise RuntimeError("PipelinesAPI was constructed without a get_bytes transport.")
        return self._get_bytes(
            f"/api/v1/private/pipelines/{quote(name, safe='')}/runs/{quote(run_id, safe='')}"
            f"/inputs/files/{index}",
        )

    def run_active(self, name: str, run_id: str) -> JsonDict:
        """Check whether a run is still active.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        run_id:
            Run ID — 16 hex chars (URL-encoded automatically).

        Returns
        -------
        dict ``{"active": bool}``.
        """
        return self._request(
            "GET",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/runs/{quote(run_id, safe='')}/active",
        )

    # ── Static-context documents ───────────────────────────────────────────────

    def list_docs(self, name: str) -> List[JsonDict]:
        """List static-context documents attached to a pipeline.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        """
        return self._request("GET", f"/api/v1/private/pipelines/{quote(name, safe='')}/docs")

    def upload_doc(
        self,
        name: str,
        file: Union[bytes, bytearray, "IO[bytes]"],
        *,
        filename: str,
        content_type: Optional[str] = None,
    ) -> JsonDict:
        """Upload a static-context document to a pipeline.

        POSTs ``multipart/form-data`` (single field ``file``) to
        ``/api/v1/private/pipelines/{name}/docs``. Allowed extensions: ``.txt
        .md .pdf .csv .json .docx .doc .pptx .xlsx``.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        file:
            Raw file bytes, or a binary file-like object (must support ``.read()``).
        filename:
            Filename to send in the multipart part — its extension is
            validated server-side, so it must be a real, allowed filename.
        content_type:
            Optional MIME type for the multipart part.
        """
        if self._post_multipart is None:
            raise RuntimeError("PipelinesAPI was constructed without a post_multipart transport.")
        return self._post_multipart(
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs",
            field_name="file",
            data=_read_bytes(file),
            filename=filename,
            content_type=content_type,
        )

    def delete_doc(self, name: str, filename: str) -> JsonDict:
        """Delete a static-context document from a pipeline.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        filename:
            Filename of the document to delete (URL-encoded automatically).
        """
        return self._request(
            "DELETE",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/{quote(filename, safe='')}",
        )

    # ── RAG (retrieval) documents ───────────────────────────────────────────────

    def upload_retrieval_doc(
        self,
        name: str,
        file: Union[bytes, bytearray, "IO[bytes]"],
        *,
        filename: str,
        content_type: Optional[str] = None,
    ) -> JsonDict:
        """Upload a RAG retrieval document to a pipeline.

        POSTs ``multipart/form-data`` (single field ``file``) to
        ``/api/v1/private/pipelines/{name}/docs/retrieval``. Call
        ``ingest_retrieval()`` afterwards to embed it.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        file:
            Raw file bytes, or a binary file-like object (must support ``.read()``).
        filename:
            Filename to send in the multipart part.
        content_type:
            Optional MIME type for the multipart part.
        """
        if self._post_multipart is None:
            raise RuntimeError("PipelinesAPI was constructed without a post_multipart transport.")
        return self._post_multipart(
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/retrieval",
            field_name="file",
            data=_read_bytes(file),
            filename=filename,
            content_type=content_type,
        )

    def ingest_retrieval(
        self,
        name: str,
        body: Optional[Dict[str, Any]] = None,
        *,
        timeout: float = 300.0,
    ) -> JsonDict:
        """Embed changed RAG retrieval documents with the pipeline's configured embedder.

        This can take minutes for large document sets, so the per-call timeout
        defaults to 300s (override with ``timeout=``) instead of the client's
        default request timeout.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        body:
            Optional JSON body; defaults to ``{}``.
        timeout:
            Per-call timeout override in seconds (default 300).
        """
        return self._request(
            "POST",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/retrieval/ingest",
            json=body if body is not None else {},
            timeout=timeout,
        )

    def delete_retrieval_doc(self, name: str, filename: str) -> JsonDict:
        """Delete a RAG retrieval document from a pipeline.

        Parameters
        ----------
        name:
            Pipeline name (URL-encoded automatically).
        filename:
            Filename of the document to delete (URL-encoded automatically).
        """
        return self._request(
            "DELETE",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/retrieval/{quote(filename, safe='')}",
        )

    def docs_preview(
        self,
        name: str,
        *,
        model_name: Optional[str] = None,
        model_provider: Optional[str] = None,
    ) -> JsonDict:
        """Per-document extraction stats of the static-context documents.

        Shows the characters kept per file, with caps for the model the agents
        use. Pass the model to see what fits its context window.
        """
        params: Dict[str, Any] = {}
        if model_name is not None:
            params["model_name"] = model_name
        if model_provider is not None:
            params["model_provider"] = model_provider
        return self._request(
            "GET",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/preview",
            params=params if params else None,
        )

    def retrieval_preview(self, name: str) -> JsonDict:
        """Stats of the pipeline's retrieval store (documents, chunks, embedder)."""
        return self._request("GET", f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/retrieval/preview")

    def test_retrieve(self, name: str, query: str, *, limit: Optional[int] = None) -> JsonDict:
        """Run a sample query against the pipeline's retrieval store.

        Returns the passages agents would get. ``limit`` is 1-20 (server default
        5). Needs an embedder configured; a store built with another embedder
        answers 409 (re-ingest it).
        """
        body: Dict[str, Any] = {"query": query}
        if limit is not None:
            body["limit"] = limit
        return self._request(
            "POST",
            f"/api/v1/private/pipelines/{quote(name, safe='')}/docs/retrieval/test_retrieve",
            json=body,
        )

    # ── Tool-call audit ─────────────────────────────────────────────────────────

    def project_tool_calls(
        self,
        project: str,
        *,
        before_created_at: Optional[str] = None,
        before_id: Optional[str] = None,
        limit: Optional[int] = None,
        tool: Optional[str] = None,
        agent: Optional[str] = None,
        run_id: Optional[str] = None,
        status: Optional[str] = None,
        search: Optional[str] = None,
        connector_source: Optional[str] = None,
        approval: Optional[str] = None,
        provider: Optional[str] = None,
        sort: Optional[str] = None,
    ) -> JsonDict:
        """List tool-call audit records for a project (cursor-paginated).

        Parameters
        ----------
        project:
            Project slug.
        before_created_at, before_id:
            Cursor pair from a previous page's ``nextCursor`` (both required
            together to page forward).
        limit:
            Page size, 1-100 (default 30).
        tool, agent, run_id, provider:
            Exact-match filters.
        status:
            ``"ok"`` or ``"error"``.
        search:
            Free-text search.
        connector_source:
            ``"project"`` or ``"personal"``.
        approval:
            ``"auto"``, ``"approved"``, or ``"by:<username>"``.
        sort:
            ``"recent"`` (default), ``"oldest"``, ``"slowest"``, or ``"fastest"``.

        Returns
        -------
        dict ``{"items": [...], "nextCursor": {"beforeCreatedAt", "beforeId"} | None, "capped": bool}``.
        """
        params: Dict[str, Any] = {}
        if before_created_at is not None:
            params["beforeCreatedAt"] = before_created_at
        if before_id is not None:
            params["beforeId"] = before_id
        if limit is not None:
            params["limit"] = limit
        if tool is not None:
            params["tool"] = tool
        if agent is not None:
            params["agent"] = agent
        if run_id is not None:
            params["runId"] = run_id
        if status is not None:
            params["status"] = status
        if search is not None:
            params["search"] = search
        if connector_source is not None:
            params["connectorSource"] = connector_source
        if approval is not None:
            params["approval"] = approval
        if provider is not None:
            params["provider"] = provider
        if sort is not None:
            params["sort"] = sort
        return self._request(
            "GET",
            f"/api/v1/private/projects/{quote(project, safe='')}/tool-calls",
            params=params,
        )

    def project_tool_call_facets(self, project: str) -> JsonDict:
        """Get available filter facets for a project's tool-call audit log.

        Parameters
        ----------
        project:
            Project slug.

        Returns
        -------
        dict ``{"tools": [{"name": str, "count": int}, ...], "agents": [str, ...]}``.
        """
        return self._request(
            "GET",
            f"/api/v1/private/projects/{quote(project, safe='')}/tool-calls/facets",
        )

    def tool_call_detail(self, run_id: str, span_id: str) -> JsonDict:
        """Get the full, untruncated input/output for a single tool call.

        Parameters
        ----------
        run_id:
            Run ID (URL-encoded automatically).
        span_id:
            Tool-call span ID (URL-encoded automatically).
        """
        return self._request(
            "GET",
            f"/api/v1/private/runs/{quote(run_id, safe='')}/tool-calls/{quote(span_id, safe='')}",
        )

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
