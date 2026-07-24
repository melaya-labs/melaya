use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// HITL (Human-in-the-Loop) API — list, approve, and reject pending tool-call approvals.
#[derive(Clone)]
pub struct HitlAPI {
    http: HttpClient,
}

impl HitlAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List all pending HITL tool-call approvals for the authenticated user.
    pub async fn pending(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/hitl/approvals/pending", &q)
            .await
    }

    /// List historical (decided) HITL approval records.
    pub async fn history(&self, limit: Option<u32>, offset: Option<u32>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("offset", offset.map(|v| v.to_string()));
        self.http
            .get("/api/v1/private/hitl/approvals/history", &q)
            .await
    }

    /// Approve a pending HITL tool-call approval.
    pub async fn approve(&self, request_id: &str, comment: Option<&str>) -> Result<Value> {
        let mut body = json!({});
        if let Some(c) = comment {
            body["comment"] = json!(c);
        }
        self.http
            .post(
                &format!("/api/v1/private/hitl/approvals/{request_id}/approve"),
                &body,
            )
            .await
    }

    /// Reject a pending HITL tool-call approval.
    pub async fn reject(&self, request_id: &str, comment: Option<&str>) -> Result<Value> {
        let mut body = json!({});
        if let Some(c) = comment {
            body["comment"] = json!(c);
        }
        self.http
            .post(
                &format!("/api/v1/private/hitl/approvals/{request_id}/reject"),
                &body,
            )
            .await
    }

    /// Bulk approve or reject multiple pending approvals in one call.
    ///
    /// `decision` must be `"approved"` or `"rejected"`.
    pub async fn bulk_decide(
        &self,
        request_ids: &[&str],
        decision: &str,
        comment: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({
            "requestIds": request_ids,
            "decision": decision,
        });
        if let Some(c) = comment {
            body["comment"] = json!(c);
        }
        self.http
            .post("/api/v1/private/hitl/approvals/bulk", &body)
            .await
    }

    // ── Run-level inspection ─────────────────────────────────────────────────

    /// Get tool-call statistics for a specific pipeline run.
    pub async fn run_tool_stats(&self, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/hitl/runs/{run_id}/tool-stats"),
                &q,
            )
            .await
    }

    /// Get paginated messages for a pipeline run.
    pub async fn run_messages(
        &self,
        run_id: &str,
        limit: Option<u32>,
        cursor: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("cursor", cursor.map(str::to_owned));
        self.http
            .get(&format!("/api/v1/private/hitl/runs/{run_id}/messages"), &q)
            .await
    }

    /// Get tool-call stats broken down by agent for a run.
    pub async fn run_tool_stats_by_agent(&self, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/hitl/runs/{run_id}/tool-stats/by-agent"),
                &q,
            )
            .await
    }

    /// Get all tool calls for a pipeline run.
    pub async fn run_tool_calls(&self, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/hitl/runs/{run_id}/tool-calls"),
                &q,
            )
            .await
    }
}
