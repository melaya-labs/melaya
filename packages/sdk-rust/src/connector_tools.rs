//! Connector Tools API — discover and call connector tools directly, the
//! same surface the MCP server exposes to models
//! (`services/mcp/tools/connectors.ts`).
//!
//! Maps to `/api/v1/private/connector-tools/*`. Not to be confused with
//! [`crate::ConnectorsAPI`] (project-scoped credential storage): this module
//! discovers and CALLS tools once a service is connected — it never accepts
//! or returns a credential value.
//!
//! Reads run immediately. Writes default to an approval card raised in the
//! Melaya app (`approval: "required"`, the same card the Assistant raises);
//! `approval: "none"` runs the write immediately and is audit-logged. Tools
//! that move money or trade are always refused, under both approval modes.
//!
//! ```no_run
//! # use melaya::Melaya;
//! # #[tokio::main] async fn main() -> Result<(), Box<dyn std::error::Error>> {
//! # let m = Melaya::new(&std::env::var("MK")?)?;
//! m.connector_tools.services().await?;
//! m.connector_tools.search("unread email", None).await?;
//! m.connector_tools
//!     .call("gmail_list_messages", Some(&serde_json::json!({"max_results": 5})), None)
//!     .await?;
//! let outcome = m
//!     .connector_tools
//!     .call_and_wait(
//!         "gmail_send",
//!         Some(&serde_json::json!({"to": "a@b.c", "subject": "Hi", "body": "..."})),
//!         None,
//!         None,
//!         None,
//!     )
//!     .await?;
//! # Ok(()) }
//! ```

use serde_json::{json, Value};
use std::collections::HashMap;
use std::time::Duration;
use tokio::time::{sleep, Instant};

use crate::client::HttpClient;
use crate::error::Result;

/// Default poll interval for [`ConnectorToolsAPI::call_and_wait`] (3 s).
const DEFAULT_POLL_INTERVAL_MS: u64 = 3_000;
/// Default timeout for [`ConnectorToolsAPI::call_and_wait`] (10 min).
const DEFAULT_TIMEOUT_MS: u64 = 600_000;

/// Connector Tools API — search, describe, test, connect, and call connector
/// tools directly (the same surface the MCP server exposes), with staged
/// approval for writes.
#[derive(Clone)]
pub struct ConnectorToolsAPI {
    http: HttpClient,
}

impl ConnectorToolsAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List connected services + tool counts.
    ///
    /// Returns `{ services: string[], builtIn: "melaya_core", toolCounts:
    /// { [service]: { readTools, writeTools } } }`.
    pub async fn services(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/connector-tools/services", &q)
            .await
    }

    /// Discover tools by plain business keywords (e.g. `"unread email"`).
    ///
    /// `limit` is 1-50, default 15 (clamped server-side). Returns
    /// `{ query, services, tools: ToolInfo[] }` where each `ToolInfo` is
    /// `{ name, service, description, readOnly, movesMoney, params }`.
    pub async fn search(&self, q: &str, limit: Option<u32>) -> Result<Value> {
        let mut query: HashMap<&str, Option<String>> = HashMap::new();
        query.insert("q", Some(q.to_owned()));
        query.insert("limit", limit.map(|v| v.to_string()));
        self.http
            .get("/api/v1/private/connector-tools/search", &query)
            .await
    }

    /// Full description + parameters for one tool (a `ToolInfo`).
    ///
    /// Returns [`crate::MelayaError::Api`] (404) when the tool is unknown, or
    /// not unlocked by any of your connected services.
    pub async fn describe(&self, tool: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/connector-tools/tools/{tool}"), &q)
            .await
    }

    /// Test the STORED credential for a connected service.
    ///
    /// Returns `{ service, success, message }`. Returns
    /// [`crate::MelayaError::Api`] (504, code `"timeout"`) if the service
    /// does not answer within 30 s.
    pub async fn test(&self, service: &str) -> Result<Value> {
        let body = json!({ "service": service });
        self.http
            .post("/api/v1/private/connector-tools/test", &body)
            .await
    }

    /// Start connecting a service. Never accepts a secret.
    ///
    /// Returns `{ service, kind: "oauth" | "oauth_unavailable" |
    /// "interactive_login" | "api_key", authorizationUrl?, connectUrl?,
    /// message }`. For `"oauth"` the user opens `authorizationUrl`; the
    /// other kinds finish on the Melaya Connectors page.
    pub async fn connect(&self, service: &str) -> Result<Value> {
        let body = json!({ "service": service });
        self.http
            .post("/api/v1/private/connector-tools/connect", &body)
            .await
    }

    /// Call a connector tool.
    ///
    /// A read tool (or a write with `approval: "none"`) runs immediately and
    /// returns `{ status: "done", tool, readOnly, result }`.
    ///
    /// A write with `approval: "required"` (the default) is staged as the
    /// same approval card the Assistant raises in the Melaya app; the call
    /// returns HTTP **202** — `{ status: "pending_approval", tool,
    /// requestId, message }` — which is returned here like any other
    /// successful response, NOT raised as an error. Poll the outcome with
    /// [`call_status`](Self::call_status), or use
    /// [`call_and_wait`](Self::call_and_wait) to block until it settles.
    ///
    /// Tools that move money or trade are refused under BOTH approval modes
    /// and return [`crate::MelayaError::Api`] (403, code
    /// `"money_moving_requires_app_approval"`); they run only from the
    /// Melaya app.
    pub async fn call(
        &self,
        tool: &str,
        args: Option<&Value>,
        approval: Option<&str>,
    ) -> Result<Value> {
        let body = json!({
            "tool": tool,
            "args": args.cloned().unwrap_or_else(|| json!({})),
            "approval": approval.unwrap_or("required"),
        });
        self.http
            .post("/api/v1/private/connector-tools/call", &body)
            .await
    }

    /// Outcome of a staged write, by the `requestId` returned from
    /// [`call`](Self::call).
    ///
    /// Returns one of:
    /// - `{ requestId, tool, status: "pending" | "running" | "expired" }`
    /// - `{ requestId, tool, status: "done", ok, result?, error? }`
    /// - `{ requestId, tool, status: "rejected", reason? }`
    ///
    /// Returns [`crate::MelayaError::Api`] (404) when `request_id` is
    /// unknown or expired beyond recall.
    pub async fn call_status(&self, request_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/connector-tools/calls/{request_id}"),
                &q,
            )
            .await
    }

    /// [`call`](Self::call) a tool and block until a staged write settles.
    ///
    /// A read (or a write with `approval: "none"`) returns immediately,
    /// exactly like `call()`. A write with `approval: "required"` (the
    /// default) is staged; this then polls
    /// [`call_status`](Self::call_status) — every `poll_interval_ms`
    /// (default 3 000) — until the status is `"done"`, `"rejected"`, or
    /// `"expired"`, or until `timeout_ms` (default 600 000 = 10 min) has
    /// elapsed, whichever comes first, and returns that final
    /// `call_status()` outcome.
    ///
    /// This never returns an error on a settled `"rejected"` / `"expired"`
    /// status or an `ok: false` result — those are meaningful return values,
    /// not transport errors. On timeout it returns the last-seen
    /// `call_status()` outcome (still `"pending"` or `"running"`).
    pub async fn call_and_wait(
        &self,
        tool: &str,
        args: Option<&Value>,
        approval: Option<&str>,
        poll_interval_ms: Option<u64>,
        timeout_ms: Option<u64>,
    ) -> Result<Value> {
        let outcome = self.call(tool, args, approval).await?;
        if outcome.get("status").and_then(Value::as_str) != Some("pending_approval") {
            return Ok(outcome);
        }
        let request_id = outcome
            .get("requestId")
            .and_then(Value::as_str)
            .unwrap_or_default()
            .to_owned();

        let poll_interval =
            Duration::from_millis(poll_interval_ms.unwrap_or(DEFAULT_POLL_INTERVAL_MS));
        let deadline =
            Instant::now() + Duration::from_millis(timeout_ms.unwrap_or(DEFAULT_TIMEOUT_MS));

        let mut status_out = self.call_status(&request_id).await?;
        loop {
            let status = status_out
                .get("status")
                .and_then(Value::as_str)
                .unwrap_or("");
            if matches!(status, "done" | "rejected" | "expired") {
                break;
            }
            if Instant::now() >= deadline {
                break;
            }
            sleep(poll_interval).await;
            status_out = self.call_status(&request_id).await?;
        }
        Ok(status_out)
    }
}
