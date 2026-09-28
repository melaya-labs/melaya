//! Triggers API: read, diagnose, and dry-run event triggers.
//!
//! Maps to `/api/v1/private/triggers/*`. An event trigger starts work on a
//! pipeline when something happens elsewhere: a signed webhook, a WebSocket
//! or SSE stream, an exchange event, a poll of a connected app, or a push
//! from a connected app. This module is for inspecting triggers and finding
//! out why one did or did not fire: list and read triggers, read delivery
//! receipts, verdict stats and the live event log, list the approvals a
//! trigger is waiting on, and run dry tests.
//!
//! Creating, editing, deleting and rotating the secret of a trigger are done
//! in the Melaya app; they are not on the REST surface. Secrets are never
//! returned by any method here.
//!
//! [`test`](TriggersAPI::test) and [`poll_test`](TriggersAPI::poll_test) are
//! dry runs: nothing is published and the action never executes.
//! [`poll_now`](TriggersAPI::poll_now) queues a real poll.
//!
//! ```no_run
//! # use melaya::{Melaya, TriggerEventsQuery};
//! # #[tokio::main] async fn main() -> Result<(), Box<dyn std::error::Error>> {
//! # let m = Melaya::new(&std::env::var("MK")?)?;
//! let triggers = m.triggers.list(Some("acme"), None).await?;
//! let id = triggers[0]["id"].as_str().unwrap_or_default().to_owned();
//!
//! // Why did nothing run? Read the newest events, including those that
//! // wrote no receipt (filtered, shed, ingress rejections).
//! let events = m
//!     .triggers
//!     .events(&TriggerEventsQuery {
//!         trigger_id: Some(id.clone()),
//!         limit: Some(20),
//!         ..Default::default()
//!     })
//!     .await?;
//! for e in events["events"].as_array().into_iter().flatten() {
//!     println!("{} {}", e["verdict"], e["detail"]);
//! }
//!
//! // Dry run one event: the action never executes.
//! m.triggers.test(&id, Some(&serde_json::json!({"amount": 42}))).await?;
//! # Ok(()) }
//! ```

use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Base path of the event triggers REST surface.
const TRIGGERS_BASE: &str = "/api/v1/private/triggers";

/// Every delivery verdict the server reports, in pipeline order. Use these
/// values in [`TriggerEventsQuery::verdicts`].
pub const TRIGGER_VERDICTS: [&str; 9] = [
    "accepted",
    "duplicate",
    "rejected",
    "rate_limited",
    "filtered",
    "decided",
    "dispatched",
    "skipped",
    "failed",
];

/// Filters for [`TriggersAPI::events`]. `None` fields are not sent.
#[derive(Debug, Clone, Default)]
pub struct TriggerEventsQuery {
    /// Narrow the log to one trigger.
    pub trigger_id: Option<String>,
    /// Only events newer than this epoch millisecond time. Pass the newest
    /// `at` already seen to poll for new events.
    pub since: Option<u64>,
    /// Keep only these verdicts (see [`TRIGGER_VERDICTS`]). Sent as a comma
    /// list; an empty list is not sent.
    pub verdicts: Vec<String>,
    /// Cap on the number of events, 1-200 (server default 50).
    pub limit: Option<u32>,
}

/// Triggers API: read, diagnose, and dry-run event triggers (list, get,
/// deliveries, stats, live events, pending approvals, dry tests, and poll
/// runtime state).
#[derive(Clone)]
pub struct TriggersAPI {
    http: HttpClient,
}

impl TriggersAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List the caller's event triggers, optionally filtered by project and
    /// pipeline.
    ///
    /// Returns `TriggerRecord[]`: `{ id, publicId, name, kind, project,
    /// pipelineName, enabled, pausedReason, signingScheme, sourceId, config,
    /// maxEventsPerMin, maxRunsPerDay, maxConcurrentRuns,
    /// consecutiveFailures, lastEventAt, createdAt, updatedAt, webhookUrl,
    /// projectAccess }`. `kind` is `"webhook"`, `"wss"` (a WebSocket or SSE
    /// stream), `"engine"`, `"poll"` or `"push"`. Secrets are never included.
    pub async fn list(&self, project: Option<&str>, pipeline_name: Option<&str>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("project", project.map(str::to_owned));
        q.insert("pipelineName", pipeline_name.map(str::to_owned));
        self.http.get(TRIGGERS_BASE, &q).await
    }

    /// One trigger with its config (a `TriggerRecord`, see
    /// [`list`](Self::list)). Secrets are never included.
    ///
    /// Returns [`crate::MelayaError::Api`] (404, code `"trigger_not_found"`)
    /// when the trigger does not exist or is not yours.
    pub async fn get(&self, id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http.get(&format!("{TRIGGERS_BASE}/{id}"), &q).await
    }

    /// Most recent delivery receipts of a trigger, newest first.
    ///
    /// `limit` is 1-200 (server default 50). Each receipt is `{ id,
    /// triggerId, eventId, source, receivedAt, verdict, decision, action,
    /// runId, detail, latencyMs, timings, resultExcerpt, redelivered }`.
    pub async fn deliveries(&self, id: &str, limit: Option<u32>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("limit", limit.map(|v| v.to_string()));
        self.http
            .get(&format!("{TRIGGERS_BASE}/{id}/deliveries"), &q)
            .await
    }

    /// Delivery counts and latency percentiles by verdict over the last
    /// `hours` (1-168, server default 24).
    ///
    /// Returns `{ hours, byVerdict: { [verdict]: { n, p50, p95 } }, filtered,
    /// sampled }`. `filtered` counts events dropped by the prefilter (they
    /// write no receipt) and is `null` when the counter is unavailable.
    pub async fn stats(&self, id: &str, hours: Option<u32>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("hours", hours.map(|v| v.to_string()));
        self.http
            .get(&format!("{TRIGGERS_BASE}/{id}/stats"), &q)
            .await
    }

    /// Tool calls of this trigger still waiting on a human decision, oldest
    /// first. Approve or reject them in the Melaya app.
    ///
    /// Returns `[{ requestId, triggerId, deliveryId, eventId, source,
    /// service, tool, argsPreview, createdAt, expiresAt }]` (times in epoch
    /// milliseconds).
    pub async fn pending_approvals(&self, id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("{TRIGGERS_BASE}/{id}/approvals"), &q)
            .await
    }

    /// Send one synthetic event through the trigger as a dry run: the
    /// prefilter, decision and routing run for real, the action never
    /// executes. `payload` defaults to an empty object. Test fires count
    /// against the trigger's rate limit.
    ///
    /// Returns `{ accepted, eventId, reason? }`; `reason` is set when
    /// `accepted` is false (`"disabled"`, `"project_access_lost"`,
    /// `"rate_limited"`, `"unavailable"`, ...). Follow the result with
    /// [`events`](Self::events) or [`deliveries`](Self::deliveries).
    pub async fn test(&self, id: &str, payload: Option<&Value>) -> Result<Value> {
        let body = json!({ "payload": payload.cloned().unwrap_or_else(|| json!({})) });
        self.http
            .post(&format!("{TRIGGERS_BASE}/{id}/test"), &body)
            .await
    }

    /// Recent live trigger events, newest first. Includes outcomes that
    /// wrote no receipt: filtered, shed, ingress rejections (signature, rate
    /// limit, duplicate, size), feed refusals and push lifecycle notes.
    ///
    /// Returns `{ events: TriggerLiveEvent[], scanned, retention: {
    /// maxEvents, ttlSec } }` where each event is `{ triggerId, deliveryId,
    /// eventId, source, verdict, action?, runId?, detail?, latencyMs?,
    /// resultExcerpt?, at }`.
    pub async fn events(&self, query: &TriggerEventsQuery) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("triggerId", query.trigger_id.clone());
        q.insert("since", query.since.map(|v| v.to_string()));
        q.insert(
            "verdicts",
            if query.verdicts.is_empty() {
                None
            } else {
                Some(query.verdicts.join(","))
            },
        );
        q.insert("limit", query.limit.map(|v| v.to_string()));
        self.http.get(&format!("{TRIGGERS_BASE}/events"), &q).await
    }

    /// Runtime state of a poll trigger.
    ///
    /// Returns `{ synced, status, lastError, lastPolledAt, nextPollAt, armed,
    /// baselinePending, seenCount, itemsPublished, consecutiveErrors,
    /// requestedIntervalSec, effectiveIntervalSec, tierFloorSec }`. `synced`
    /// is false when the poller has no runtime row yet (see
    /// [`poll_sync`](Self::poll_sync)).
    pub async fn poll_status(&self, id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("{TRIGGERS_BASE}/{id}/poll"), &q)
            .await
    }

    /// Run the poll once as a dry run: call the tool and return what it
    /// found and would publish. Nothing is published and no state changes.
    /// The server allows one dry poll every 10 seconds per user.
    ///
    /// Returns `{ dry: true, ok: true, found, baseline, wouldPublish, items:
    /// [{ id, preview }], samplePayload }`. A poll whose tool call fails is
    /// returned as a normal result, `{ dry: true, ok: false, error }`, not as
    /// an error; check `ok`. HTTP errors (for example 429 when dry polls are
    /// throttled) still return [`crate::MelayaError::Api`].
    pub async fn poll_test(&self, id: &str) -> Result<Value> {
        let body = json!({ "dry": true });
        self.http
            .post_dry_run(&format!("{TRIGGERS_BASE}/{id}/poll/test"), &body)
            .await
    }

    /// Make an enabled poll trigger due now: a real poll is queued and new
    /// items are published as events. Returns `{ dry: false, queued }`.
    pub async fn poll_now(&self, id: &str) -> Result<Value> {
        let body = json!({ "dry": false });
        self.http
            .post(&format!("{TRIGGERS_BASE}/{id}/poll/test"), &body)
            .await
    }

    /// Re-create a poll trigger's runtime row from its saved config, to
    /// re-arm a trigger whose poller never started. Returns `{ result }`.
    pub async fn poll_sync(&self, id: &str) -> Result<Value> {
        let body = json!({});
        self.http
            .post(&format!("{TRIGGERS_BASE}/{id}/poll/sync"), &body)
            .await
    }

    /// Trigger presets available to the caller.
    ///
    /// Returns `{ tier, tierFloorSec, presets: [...], beta: { allowed,
    /// minTier } }`.
    pub async fn presets(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get(&format!("{TRIGGERS_BASE}/presets"), &q).await
    }

    /// Plan caps and current usage.
    ///
    /// Returns `{ tierClass, beta: { allowed, minTier }, triggers: { used,
    /// cap }, sources: { used, cap }, eventsPerMin: { perUser,
    /// perTriggerMax, perTriggerDefault }, pollIntervalFloorSec,
    /// approvalTtlSec: { min, max, default } }`.
    pub async fn limits(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get(&format!("{TRIGGERS_BASE}/limits"), &q).await
    }

    /// The caller's WebSocket and SSE stream sources.
    ///
    /// Returns `[{ id, name, url, authHeaderName, hasAuth, subscribeFrame,
    /// eventIdPath, enabled, status, lastError, lastConnectedAt,
    /// droppedFrames, createdAt, connectorService, transport }]`. The auth
    /// value is never returned.
    pub async fn sources(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get(&format!("{TRIGGERS_BASE}/sources"), &q).await
    }
}
