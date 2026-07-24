use serde_json::Value;
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Evals API — run evaluation results and agent memory graphs.
#[derive(Clone)]
pub struct EvalsAPI {
    http: HttpClient,
}

impl EvalsAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List eval run results for the caller's tenant.
    pub async fn list_runs(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/evals/runs", &q).await
    }

    /// Get aggregate summary of eval results.
    pub async fn summary(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/evals/summary", &q).await
    }

    /// Get detailed results for a specific eval run.
    pub async fn run_detail(&self, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/evals/runs/{run_id}"), &q)
            .await
    }

    /// Compare results across multiple eval runs.
    pub async fn compare(&self, run_ids: &[&str]) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        // Pass as comma-separated runIds query param
        q.insert("runIds", Some(run_ids.join(",")));
        self.http.get("/api/v1/private/evals/compare", &q).await
    }

    /// Get memory graph visualization data for eval runs.
    pub async fn memory_graph(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/evals/memory-graph", &q)
            .await
    }

    /// Get memory usage for a specific eval run.
    pub async fn run_memory(&self, run_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/evals/runs/{run_id}/memory"), &q)
            .await
    }

    /// Get agent crew memory for a pipeline.
    pub async fn crew_memory(
        &self,
        pipeline: Option<&str>,
        project: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("pipeline", pipeline.map(str::to_owned));
        q.insert("project", project.map(str::to_owned));
        self.http.get("/api/v1/private/evals/crew-memory", &q).await
    }

    /// Get benchmark scores across eval runs.
    pub async fn benchmarks(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/evals/benchmarks", &q).await
    }
}
