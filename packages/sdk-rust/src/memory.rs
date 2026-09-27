use serde::Serialize;
use serde_json::{json, Value};

use crate::client::HttpClient;
use crate::error::Result;

/// Patch fields for [`MemoryAPI::edit_entry`]. All fields are optional; only
/// the ones present are applied to the stored entry.
#[derive(Debug, Clone, Default, Serialize)]
pub struct CrewMemoryPatch {
    #[serde(skip_serializing_if = "Option::is_none")]
    pub topic: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub content: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub tags: Option<Vec<String>>,
}

/// Agent Memory API — edit and delete one persisted cross-run crew-memory
/// entry, keyed by pipeline name (not a run id). Editor/owner-gated and
/// tenant-scoped server-side.
///
/// Read access to crew memory — the memory-constellation graph, one run's
/// agent memory, and the crew-memory listing itself — lives on
/// [`crate::EvalsAPI`] (`memory_graph`, `run_memory`, `crew_memory`).
#[derive(Clone)]
pub struct MemoryAPI {
    http: HttpClient,
}

impl MemoryAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Edit one persisted crew-memory entry. `project` disambiguates the
    /// pipeline name when needed.
    pub async fn edit_entry(
        &self,
        pipeline: &str,
        entry_id: &str,
        project: Option<&str>,
        patch: &CrewMemoryPatch,
    ) -> Result<Value> {
        let mut body = json!({
            "pipeline": pipeline,
            "entryId": entry_id,
            "patch": serde_json::to_value(patch).unwrap_or_else(|_| json!({})),
        });
        if let Some(p) = project {
            body["project"] = json!(p);
        }
        self.http
            .post("/api/v1/private/memory/crew/edit", &body)
            .await
    }

    /// Delete one persisted crew-memory entry. `project` disambiguates the
    /// pipeline name when needed.
    pub async fn delete_entry(
        &self,
        pipeline: &str,
        entry_id: &str,
        project: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({ "pipeline": pipeline, "entryId": entry_id });
        if let Some(p) = project {
            body["project"] = json!(p);
        }
        self.http
            .post("/api/v1/private/memory/crew/delete", &body)
            .await
    }
}
