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
    /// Where to execute: local-runner or cloud-spawn.
    #[serde(skip_serializing_if = "Option::is_none")]
    pub execution_target: Option<String>,
    /// Studio URL used for cloud executions (injected by the Studio).
    #[serde(rename = "studio_url", skip_serializing_if = "Option::is_none")]
    pub studio_url: Option<String>,
    /// Per-run environment variable overrides.
    #[serde(rename = "env_overrides", skip_serializing_if = "Option::is_none")]
    pub env_overrides: Option<Value>,
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
    /// `config` is the full pipeline config object; `project` scopes the lookup.
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
