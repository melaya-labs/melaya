//! # Pipeline configuration
//!
//! A run is generated ONLY from `config.steps[]`. `agents[]` alone (with no
//! matching `steps[]` entries) produces an EMPTY pipeline — every step must
//! embed its own agent inline:
//!
//! ```no_run
//! # use serde_json::json;
//! let config = json!({
//!     "steps": [{
//!         "kind": "agent",
//!         "agent": {
//!             "name": "researcher",
//!             "role": "Careful web researcher",
//!             "instruction": "Summarize today's top AI news in 5 bullets.",
//!             "model": { "provider": "anthropic", "name": "claude-sonnet-4-6" },
//!             "agent_tools": ["web_search"],
//!             "human_approval_tools": []
//!         }
//!     }]
//! });
//! ```
//!
//! There is no `prompt` field. The prompt fields are `instruction` (the
//! task) and `system_prompt_override` (a system-level override).
//!
//! Other config fields worth knowing:
//! - `hitl_mode`: `"safe"` (default) | `"autonomous"` | `"payments_only"` —
//!   only `"safe"` honours `human_approval_tools`.
//! - `connector_source`: `"personal"` | `"project"` — which credential pool
//!   the run draws from.
//! - `force_local_runner`: pin execution to the caller's own runner.
//! - `inputs[]`: declares the run-time inputs that `run()`'s `run_inputs`
//!   supplies (see [`RunInputs`]).
//!
//! [`get`](PipelinesAPI::get) returns an ENVELOPE `{ name, client, config,
//! code, docs }`. To edit a pipeline, mutate the `config` field of that
//! envelope and pass THAT to [`update`](PipelinesAPI::update):
//!
//! ```no_run
//! # use melaya::Melaya;
//! # #[tokio::main] async fn main() -> Result<(), Box<dyn std::error::Error>> {
//! # let m = Melaya::new(&std::env::var("MK")?)?;
//! let mut envelope = m.pipelines.get("daily-digest", Some("acme")).await?;
//! envelope["config"]["steps"][0]["agent"]["model"] =
//!     serde_json::json!({ "provider": "anthropic", "name": "claude-opus-4-8" });
//! m.pipelines.update("daily-digest", &envelope["config"], "acme").await?;
//! # Ok(()) }
//! ```

use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

// ── Trace types ───────────────────────────────────────────────────────────────

/// A single trace summary row returned by `GET /runs/:runId/traces`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TraceSummary {
    pub trace_id: String,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub trace_name: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub start_time: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub end_time: Option<String>,
    /// Integer status code: 0 = ok, 1 = unset/warn, 2 = error (OTel convention).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub status: Option<i64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub span_count: Option<u64>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub total_tokens: Option<u64>,
}

/// Paginated envelope returned by `getTraces` / `GET /runs/:runId/traces`.
/// Matches `{ data: { list, total, page, pageSize } }` from the server.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TracesPage {
    pub list: Vec<TraceSummary>,
    pub total: u64,
    pub page: u64,
    pub page_size: u64,
}

/// Response from `DELETE /runs/:runId/traces`.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DeleteTracesResult {
    pub deleted_spans: u64,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub requested_traces: Option<u64>,
}

// ── Lifecycle structs ─────────────────────────────────────────────────────────

/// Options for running a pipeline.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PipelineRunOptions {
    /// Project the pipeline belongs to.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub project: Option<String>,
    /// Target label: local-runner or cloud-spawn.
    /// Used for the TIER CHECK only. Where the run actually executes is
    /// decided by the pipeline's stored config (local model providers /
    /// `force_local_runner`), never by this field.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub execution_target: Option<String>,
    /// Studio URL used for cloud executions (injected by the Studio).
    #[serde(rename = "studio_url", skip_serializing_if = "Option::is_none")]
    pub studio_url: Option<String>,
    /// Per-run environment variable overrides layered over the caller's
    /// stored credentials. `MEL_*` / `MELAYA_*` keys are stripped
    /// server-side — identity and tier are always stamped by the platform,
    /// never accepted from the client.
    #[serde(rename = "env_overrides", skip_serializing_if = "Option::is_none")]
    pub env_overrides: Option<Value>,
    /// Run-time brief/values for the inputs declared by the pipeline's
    /// `inputs[]` config. See [`RunInputs`].
    #[serde(rename = "run_inputs", skip_serializing_if = "Option::is_none")]
    pub run_inputs: Option<RunInputs>,
}

/// Run-time inputs for [`PipelinesAPI::run`]: a free-form brief and/or
/// keyed values declared by the pipeline's `inputs[]` config.
#[derive(Debug, Clone, Default, Serialize, Deserialize)]
pub struct RunInputs {
    /// Free-form instruction text for this run.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub brief: Option<String>,
    /// Values keyed by the pipeline's declared input name. A file value may
    /// be `{ "file_id": ... }` (from [`PipelinesAPI::upload_run_file`]),
    /// `{ "url": ... }` (≤25 MB), or `{ "base64": ..., "name": ... }`
    /// (≤7 MB).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub values: Option<HashMap<String, Value>>,
}

/// Response returned by the pipeline run endpoint.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PipelineRunAccepted {
    /// Unique identifier for the queued run.
    #[serde(rename = "run_id")]
    pub run_id: String,
    /// Whether the run was queued successfully.
    pub queued: bool,
    /// Echoed back when the run was started with `run_inputs`.
    #[serde(rename = "run_inputs", skip_serializing_if = "Option::is_none")]
    pub run_inputs: Option<Value>,
}

/// Response from [`PipelinesAPI::run_active`].
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct RunActiveStatus {
    /// Whether the run is still active on the executing runner/process.
    pub active: bool,
}

/// Live status of a pipeline run.
#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PipelineRunStatus {
    /// Unique identifier for the run.
    pub run_id: String,
    /// Current status string (e.g. `"queued"`, `"running"`, `"success"`, `"failed"`).
    pub status: String,
    /// ISO-8601 creation timestamp.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub created_at: Option<String>,
    /// Where the run executed.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub execution_target: Option<String>,
    /// Structured run cost totals (if available).
    #[serde(skip_serializing_if = "Option::is_none")]
    pub cost: Option<Value>,
}

/// Percent-encode a single URL path segment.
///
/// Encodes every byte that is not an RFC 3986 unreserved character
/// (`ALPHA / DIGIT / - . _ ~`). This is safe for embedding in any path
/// segment where `/` must remain a path delimiter.
fn encode_segment(s: &str) -> String {
    let mut out = String::with_capacity(s.len());
    for b in s.bytes() {
        match b {
            b'A'..=b'Z' | b'a'..=b'z' | b'0'..=b'9' | b'-' | b'.' | b'_' | b'~' => {
                out.push(b as char);
            }
            _ => {
                out.push('%');
                out.push(
                    char::from_digit((b >> 4) as u32, 16)
                        .unwrap()
                        .to_ascii_uppercase(),
                );
                out.push(
                    char::from_digit((b & 0xF) as u32, 16)
                        .unwrap()
                        .to_ascii_uppercase(),
                );
            }
        }
    }
    out
}

/// Pipelines API — overview, runs, traces, and cron schedules.
#[derive(Clone)]
pub struct PipelinesAPI {
    http: HttpClient,
}

impl PipelinesAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    // ── Overview ────────────────────────────────────────────────────────────

    /// Dashboard overview: usage stats, active strategies, recent runs.
    pub async fn overview(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/overview", &q).await
    }

    /// Get pricing data for available AI models.
    pub async fn model_prices(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/overview/model-prices", &q)
            .await
    }

    /// Get chart data for the overview dashboard (cost/usage over time).
    pub async fn chart_data(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/overview/chart", &q).await
    }

    /// Get cost breakdown by model/provider.
    pub async fn cost_breakdown(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/overview/cost-breakdown", &q)
            .await
    }

    /// Count of pipeline runs grouped by status.
    pub async fn count(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/overview/pipeline-count", &q)
            .await
    }

    /// Paginated list of pipeline runs.
    pub async fn list(
        &self,
        project: Option<&str>,
        pipeline_name: Option<&str>,
        status: Option<&str>,
        limit: Option<u32>,
        offset: Option<u32>,
        page: Option<u32>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("project", project.map(str::to_owned));
        q.insert("pipelineName", pipeline_name.map(str::to_owned));
        q.insert("status", status.map(str::to_owned));
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("offset", offset.map(|v| v.to_string()));
        q.insert("page", page.map(|v| v.to_string()));
        self.http
            .get("/api/v1/private/overview/pipelines", &q)
            .await
    }

    /// Most recent pipeline runs for a dashboard widget.
    pub async fn recent(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/overview/pipelines/recent", &q)
            .await
    }

    // ── Traces ──────────────────────────────────────────────────────────────

    /// List all traces for a run (paginated).
    ///
    /// Returns a `TracesPage` envelope: `{ list, total, page, pageSize }`.
    pub async fn traces(&self, run_id: &str) -> Result<TracesPage> {
        let q = HashMap::new();
        let enc_run = encode_segment(run_id);
        let raw = self
            .http
            .get(&format!("/api/v1/private/runs/{enc_run}/traces"), &q)
            .await?;
        // Server wraps in { data: { list, total, page, pageSize } }
        let inner = raw.get("data").cloned().unwrap_or(raw);
        Ok(serde_json::from_value(inner)?)
    }

    /// Get a single trace by ID.
    pub async fn trace(&self, run_id: &str, trace_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_run = encode_segment(run_id);
        let enc_trace = encode_segment(trace_id);
        self.http
            .get(
                &format!("/api/v1/private/runs/{enc_run}/traces/{enc_trace}"),
                &q,
            )
            .await
    }

    /// Get statistics for a specific trace.
    pub async fn trace_stats(&self, run_id: &str, trace_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_run = encode_segment(run_id);
        let enc_trace = encode_segment(trace_id);
        self.http
            .get(
                &format!("/api/v1/private/runs/{enc_run}/traces/{enc_trace}/stats"),
                &q,
            )
            .await
    }

    /// Delete all traces for a run by run ID.
    ///
    /// Calls `DELETE /api/v1/private/runs/:runId/traces` (no body).
    /// Returns `{ deletedSpans, requestedTraces? }`.
    pub async fn delete_traces(&self, run_id: &str) -> Result<DeleteTracesResult> {
        let q = HashMap::new();
        let enc_run = encode_segment(run_id);
        let raw = self
            .http
            .delete(&format!("/api/v1/private/runs/{enc_run}/traces"), &q)
            .await?;
        Ok(serde_json::from_value(raw)?)
    }

    // ── Schedule ────────────────────────────────────────────────────────────

    /// List all pipeline schedules accessible to the caller.
    pub async fn list_schedules(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/pipeline-schedule", &q).await
    }

    /// Get the schedule for a specific pipeline.
    pub async fn get_schedule(&self, project: &str, pipeline_name: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_proj = encode_segment(project);
        let enc_name = encode_segment(pipeline_name);
        self.http
            .get(
                &format!("/api/v1/private/pipeline-schedule/{enc_proj}/{enc_name}"),
                &q,
            )
            .await
    }

    /// Create or update a pipeline schedule (cron expression + optional config).
    pub async fn upsert_schedule(
        &self,
        project: &str,
        pipeline_name: &str,
        cron: &str,
        config: Option<&Value>,
    ) -> Result<Value> {
        let mut body = json!({ "cron": cron });
        if let Some(cfg) = config {
            body["config"] = cfg.clone();
        }
        let enc_proj = encode_segment(project);
        let enc_name = encode_segment(pipeline_name);
        self.http
            .put(
                &format!("/api/v1/private/pipeline-schedule/{enc_proj}/{enc_name}"),
                &body,
            )
            .await
    }

    /// Pause a pipeline schedule.
    pub async fn pause_schedule(&self, project: &str, pipeline_name: &str) -> Result<Value> {
        let enc_proj = encode_segment(project);
        let enc_name = encode_segment(pipeline_name);
        self.http
            .post(
                &format!("/api/v1/private/pipeline-schedule/{enc_proj}/{enc_name}/pause"),
                &json!({}),
            )
            .await
    }

    /// Resume a paused pipeline schedule.
    pub async fn resume_schedule(&self, project: &str, pipeline_name: &str) -> Result<Value> {
        let enc_proj = encode_segment(project);
        let enc_name = encode_segment(pipeline_name);
        self.http
            .post(
                &format!("/api/v1/private/pipeline-schedule/{enc_proj}/{enc_name}/resume"),
                &json!({}),
            )
            .await
    }

    // ── Pipeline lifecycle (CRUD + run management) ───────────────────────────

    /// List all pipelines accessible to the caller.
    ///
    /// Returns `{ pipelines: [...] }`.
    pub async fn list_pipelines(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/pipelines", &q).await
    }

    /// Create a new pipeline.
    ///
    /// `config` may contain any additional pipeline configuration fields
    /// beyond `name`, `project`, and `description`.
    pub async fn create(
        &self,
        name: &str,
        project: &str,
        description: Option<&str>,
        config: Option<&Value>,
    ) -> Result<Value> {
        let mut body = json!({
            "name": name,
            "project": project,
        });
        if let Some(d) = description {
            body["description"] = json!(d);
        }
        if let Some(cfg) = config {
            if let Some(obj) = cfg.as_object() {
                for (k, v) in obj {
                    body[k] = v.clone();
                }
            }
        }
        self.http.post("/api/v1/private/pipelines", &body).await
    }

    /// Get a pipeline by name, optionally scoped to a project.
    ///
    /// Returns an ENVELOPE `{ name, client, config, code, docs }` — see the
    /// module docs above for the "edit then update" pattern (mutate the
    /// `config` field, then pass it to [`update`](Self::update)).
    pub async fn get(&self, name: &str, project: Option<&str>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("project", project.map(str::to_owned));
        let encoded = encode_segment(name);
        self.http
            .get(&format!("/api/v1/private/pipelines/{encoded}"), &q)
            .await
    }

    /// Update a pipeline's configuration.
    ///
    /// `config` is the full pipeline config object — normally the `config`
    /// field taken from [`get`](Self::get)'s envelope and mutated in place;
    /// `project` scopes the lookup.
    pub async fn update(&self, name: &str, config: &Value, project: &str) -> Result<Value> {
        let body = json!({ "config": config, "project": project });
        let encoded = encode_segment(name);
        self.http
            .put(&format!("/api/v1/private/pipelines/{encoded}"), &body)
            .await
    }

    /// Delete a pipeline.
    pub async fn delete_pipeline(&self, name: &str, project: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("project", Some(project.to_owned()));
        let encoded = encode_segment(name);
        self.http
            .delete(&format!("/api/v1/private/pipelines/{encoded}"), &q)
            .await
    }

    /// Enqueue a pipeline run and return the run ID.
    pub async fn run(
        &self,
        name: &str,
        opts: Option<PipelineRunOptions>,
    ) -> Result<PipelineRunAccepted> {
        let body = opts
            .map(|o| serde_json::to_value(o).unwrap_or(json!({})))
            .unwrap_or(json!({}));
        let encoded = encode_segment(name);
        let raw = self
            .http
            .post(&format!("/api/v1/private/pipelines/{encoded}/run"), &body)
            .await?;
        Ok(serde_json::from_value(raw)?)
    }

    /// Upload one file for a later `run()` call.
    ///
    /// `key` names the pipeline input this file is for. Single-use; the
    /// returned `{ file_id, ... }` is valid for 24 h — pass it back as
    /// `run_inputs.values.<key> = { "file_id": ... }`.
    pub async fn upload_run_file(
        &self,
        name: &str,
        key: &str,
        file: &[u8],
        project: Option<&str>,
        filename: Option<&str>,
        content_type: Option<&str>,
    ) -> Result<Value> {
        let enc_name = encode_segment(name);
        let mut query: Vec<(&str, &str)> = vec![("key", key)];
        if let Some(p) = project {
            query.push(("project", p));
        }
        self.http
            .post_multipart(
                &format!("/api/v1/private/pipelines/{enc_name}/run-files"),
                Some(&query),
                "file",
                filename.unwrap_or("file"),
                content_type.unwrap_or("application/octet-stream"),
                file,
            )
            .await
    }

    /// List all run IDs for a pipeline.
    ///
    /// Returns `{ run_ids: [...] }`.
    pub async fn run_ids(&self, name: &str) -> Result<Value> {
        let q = HashMap::new();
        let encoded = encode_segment(name);
        self.http
            .get(&format!("/api/v1/private/pipelines/{encoded}/runs"), &q)
            .await
    }

    /// Get the status of a specific run.
    pub async fn run_status(&self, name: &str, run_id: &str) -> Result<PipelineRunStatus> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        let enc_run = encode_segment(run_id);
        let raw = self
            .http
            .get(
                &format!("/api/v1/private/pipelines/{enc_name}/runs/{enc_run}"),
                &q,
            )
            .await?;
        Ok(serde_json::from_value(raw)?)
    }

    /// Cancel a running or queued pipeline run.
    pub async fn cancel_run(&self, name: &str, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        let enc_run = encode_segment(run_id);
        self.http
            .delete(
                &format!("/api/v1/private/pipelines/{enc_name}/runs/{enc_run}"),
                &q,
            )
            .await
    }

    /// What a run was started with: `{ brief, values, files }`.
    ///
    /// `run_id` is the 16-hex-char run identifier.
    pub async fn run_inputs(&self, name: &str, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        let enc_run = encode_segment(run_id);
        self.http
            .get(
                &format!("/api/v1/private/pipelines/{enc_name}/runs/{enc_run}/inputs"),
                &q,
            )
            .await
    }

    /// Download one input file from a run by index.
    ///
    /// Returns the RAW BYTES of the file — do not JSON-parse the result.
    /// `run_id` is the 16-hex-char run identifier; `index` is 0..99.
    pub async fn run_input_file(&self, name: &str, run_id: &str, index: u32) -> Result<Vec<u8>> {
        let enc_name = encode_segment(name);
        let enc_run = encode_segment(run_id);
        self.http
            .get_bytes(&format!(
                "/api/v1/private/pipelines/{enc_name}/runs/{enc_run}/inputs/files/{index}"
            ))
            .await
    }

    /// Liveness poll for a run: whether it is still active on the executing
    /// runner/process.
    pub async fn run_active(&self, name: &str, run_id: &str) -> Result<RunActiveStatus> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        let enc_run = encode_segment(run_id);
        let raw = self
            .http
            .get(
                &format!("/api/v1/private/pipelines/{enc_name}/runs/{enc_run}/active"),
                &q,
            )
            .await?;
        Ok(serde_json::from_value(raw)?)
    }

    // ── Static-context documents (DocsTab) ───────────────────────────────────

    /// List static-context documents attached to a pipeline.
    pub async fn list_docs(&self, name: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        self.http
            .get(&format!("/api/v1/private/pipelines/{enc_name}/docs"), &q)
            .await
    }

    /// Upload one static-context document.
    ///
    /// Allowed extensions: `.txt .md .pdf .csv .json .docx .doc .pptx .xlsx`.
    pub async fn upload_doc(
        &self,
        name: &str,
        file: &[u8],
        filename: Option<&str>,
        content_type: Option<&str>,
    ) -> Result<Value> {
        let enc_name = encode_segment(name);
        self.http
            .post_multipart(
                &format!("/api/v1/private/pipelines/{enc_name}/docs"),
                None,
                "file",
                filename.unwrap_or("file"),
                content_type.unwrap_or("application/octet-stream"),
                file,
            )
            .await
    }

    /// Delete one static-context document by filename.
    pub async fn delete_doc(&self, name: &str, filename: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        let enc_file = encode_segment(filename);
        self.http
            .delete(
                &format!("/api/v1/private/pipelines/{enc_name}/docs/{enc_file}"),
                &q,
            )
            .await
    }

    // ── RAG (retrieval) documents ─────────────────────────────────────────────

    /// Upload one retrieval-mode (RAG) document.
    pub async fn upload_retrieval_doc(
        &self,
        name: &str,
        file: &[u8],
        filename: Option<&str>,
        content_type: Option<&str>,
    ) -> Result<Value> {
        let enc_name = encode_segment(name);
        self.http
            .post_multipart(
                &format!("/api/v1/private/pipelines/{enc_name}/docs/retrieval"),
                None,
                "file",
                filename.unwrap_or("file"),
                content_type.unwrap_or("application/octet-stream"),
                file,
            )
            .await
    }

    /// Embed changed retrieval documents with the pipeline's configured
    /// embedder. `body` defaults to `{}`.
    ///
    /// This can take minutes — a 300 s timeout is used regardless of the
    /// client's configured default.
    pub async fn ingest_retrieval(&self, name: &str, body: Option<&Value>) -> Result<Value> {
        let enc_name = encode_segment(name);
        let payload = body.cloned().unwrap_or_else(|| json!({}));
        self.http
            .post_with_timeout(
                &format!("/api/v1/private/pipelines/{enc_name}/docs/retrieval/ingest"),
                &payload,
                300_000,
            )
            .await
    }

    /// Delete one retrieval-mode document (and its chunks) by filename.
    pub async fn delete_retrieval_doc(&self, name: &str, filename: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_name = encode_segment(name);
        let enc_file = encode_segment(filename);
        self.http
            .delete(
                &format!("/api/v1/private/pipelines/{enc_name}/docs/retrieval/{enc_file}"),
                &q,
            )
            .await
    }

    // ── Project / run tool-call audit ledger ─────────────────────────────────

    /// Project tool-call audit ledger: every tool invocation across the
    /// project's runs (tool, invoking agent, pipeline + run, who ran it,
    /// status, latency, HITL approval provenance, truncated input/output).
    /// Keyset-paginated — pass the previous page's `nextCursor` fields back
    /// as `before_created_at` / `before_id` to continue.
    ///
    /// `limit` is 1-100 (server default 30). `status` is `"ok"` | `"error"`.
    /// `connector_source` is `"project"` | `"personal"`. `approval` is
    /// `"auto"`, `"approved"`, or `"by:<username>"`. `sort` is one of
    /// `"recent"`, `"oldest"`, `"slowest"`, `"fastest"` (server default
    /// `"recent"`).
    ///
    /// Returns `{ items, nextCursor: { beforeCreatedAt, beforeId } | null, capped }`.
    #[allow(clippy::too_many_arguments)]
    pub async fn project_tool_calls(
        &self,
        project: &str,
        before_created_at: Option<&str>,
        before_id: Option<&str>,
        limit: Option<u32>,
        tool: Option<&str>,
        agent: Option<&str>,
        run_id: Option<&str>,
        status: Option<&str>,
        search: Option<&str>,
        connector_source: Option<&str>,
        approval: Option<&str>,
        provider: Option<&str>,
        sort: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("beforeCreatedAt", before_created_at.map(str::to_owned));
        q.insert("beforeId", before_id.map(str::to_owned));
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("tool", tool.map(str::to_owned));
        q.insert("agent", agent.map(str::to_owned));
        q.insert("runId", run_id.map(str::to_owned));
        q.insert("status", status.map(str::to_owned));
        q.insert("search", search.map(str::to_owned));
        q.insert("connectorSource", connector_source.map(str::to_owned));
        q.insert("approval", approval.map(str::to_owned));
        q.insert("provider", provider.map(str::to_owned));
        q.insert("sort", sort.map(str::to_owned));
        let enc_project = encode_segment(project);
        self.http
            .get(
                &format!("/api/v1/private/projects/{enc_project}/tool-calls"),
                &q,
            )
            .await
    }

    /// Distinct tools (with call counts) and agents seen in the project's
    /// tool-call ledger — powers the audit filters UI.
    pub async fn project_tool_call_facets(&self, project: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_project = encode_segment(project);
        self.http
            .get(
                &format!("/api/v1/private/projects/{enc_project}/tool-calls/facets"),
                &q,
            )
            .await
    }

    /// Full (untruncated) arguments + result for a single tool-call span in
    /// a run (access-checked).
    pub async fn tool_call_detail(&self, run_id: &str, span_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_run = encode_segment(run_id);
        let enc_span = encode_segment(span_id);
        self.http
            .get(
                &format!("/api/v1/private/runs/{enc_run}/tool-calls/{enc_span}"),
                &q,
            )
            .await
    }

    /// List all output artifacts for a pipeline.
    pub async fn outputs(&self, name: &str) -> Result<Value> {
        let q = HashMap::new();
        let encoded = encode_segment(name);
        self.http
            .get(&format!("/api/v1/private/pipelines/{encoded}/outputs"), &q)
            .await
    }

    /// Get a specific output artifact by path.
    ///
    /// `output_path` will be split on `/` and each segment individually
    /// percent-encoded before joining, so forward slashes in segment names
    /// are preserved as path separators.
    ///
    /// Pass `download: true` to append `?download=1` (returns a download URL).
    pub async fn output(&self, name: &str, output_path: &str, download: bool) -> Result<Value> {
        let encoded_name = encode_segment(name);
        let encoded_path: String = output_path
            .split('/')
            .map(encode_segment)
            .collect::<Vec<_>>()
            .join("/");
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        if download {
            q.insert("download", Some("1".to_owned()));
        }
        self.http
            .get(
                &format!("/api/v1/private/pipelines/{encoded_name}/outputs/{encoded_path}"),
                &q,
            )
            .await
    }

    /// Preview the generated code for a pipeline config without persisting it.
    pub async fn preview_code(&self, config: &Value) -> Result<Value> {
        self.http
            .post("/api/v1/private/pipelines/preview-code", config)
            .await
    }

    /// List available agent tools in the platform registry.
    pub async fn tools(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/pipelines/tools", &q).await
    }

    /// List available subagents in the platform registry.
    pub async fn subagents(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/pipelines/subagents", &q)
            .await
    }

    /// Instantiate a pipeline from a template.
    ///
    /// `overrides` is an optional JSON object of config overrides applied on
    /// top of the template defaults.
    pub async fn instantiate_template(
        &self,
        template_id: &str,
        name: &str,
        project: &str,
        overrides: Option<&Value>,
    ) -> Result<Value> {
        let mut body = json!({ "name": name, "project": project });
        if let Some(ov) = overrides {
            body["overrides"] = ov.clone();
        }
        let enc_id = encode_segment(template_id);
        self.http
            .post(
                &format!("/api/v1/private/templates/{enc_id}/instantiate"),
                &body,
            )
            .await
    }

    /// Generate a pipeline configuration from a natural-language brief using AI.
    ///
    /// `brief` is a free-form `Value` (string or structured object) describing
    /// the pipeline to build. Returns the generated pipeline config.
    pub async fn build_with_ai(&self, brief: &Value) -> Result<Value> {
        self.http
            .post("/api/v1/private/ai/build-pipeline/sync", brief)
            .await
    }

    /// Return the current public server version.
    pub async fn server_version(&self) -> Result<Value> {
        let query = HashMap::new();
        self.http.get("/api/v1/version", &query).await
    }
}
