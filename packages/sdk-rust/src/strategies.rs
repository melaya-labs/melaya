use std::collections::HashMap;

use serde_json::{json, Value};

use crate::client::HttpClient;
use crate::error::Result;

/// Strategies API — launch, control, and inspect trading strategies.
pub struct StrategiesAPI {
    http: HttpClient,
}

impl StrategiesAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Every strategy you own (running, paused, paper, and live).
    pub async fn list(&self) -> Result<Value> {
        let q = HashMap::new();
        let r = self.http.get("/api/v1/strategies/list", &q).await?;
        Ok(r["strategies"].clone())
    }

    /// A single strategy by id.
    pub async fn get(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let r = self
            .http
            .get(&format!("/api/v1/strategies/{strategy_id}"), &q)
            .await?;
        Ok(r["strategy"].clone())
    }

    /// Launch a strategy. `dry_run: true` = paper; live needs `api_key_id`.
    pub async fn create(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/strategies", body).await
    }

    /// Pause a running strategy.
    pub async fn pause(&self, strategy_id: &str) -> Result<Value> {
        self.http
            .post(&format!("/api/v1/strategies/{strategy_id}/pause"), &json!({}))
            .await
    }

    /// Resume a paused strategy.
    pub async fn resume(&self, strategy_id: &str) -> Result<Value> {
        self.http
            .post(&format!("/api/v1/strategies/{strategy_id}/resume"), &json!({}))
            .await
    }

    /// Stop a strategy and tear down its runner.
    pub async fn stop(&self, strategy_id: &str) -> Result<Value> {
        self.http
            .post(&format!("/api/v1/strategies/{strategy_id}/stop"), &json!({}))
            .await
    }

    /// Soft-delete a strategy.
    pub async fn delete(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(&format!("/api/v1/strategies/{strategy_id}"), &q)
            .await
    }

    /// Update a running strategy's params.
    pub async fn update_params(&self, strategy_id: &str, params: &Value) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/strategies/{strategy_id}/update-params"),
                params,
            )
            .await
    }

    /// Live runtime status of a strategy's runner.
    pub async fn status(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/strategies/{strategy_id}/status"), &q)
            .await
    }

    /// Performance series for a strategy (equity, PnL over time).
    pub async fn performance(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let r = self
            .http
            .get(&format!("/api/v1/strategies/{strategy_id}/performance"), &q)
            .await?;
        Ok(r["rows"].clone())
    }

    /// Execution (order) rows for a strategy.
    pub async fn executions(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let r = self
            .http
            .get(&format!("/api/v1/strategies/{strategy_id}/executions"), &q)
            .await?;
        Ok(r["rows"].clone())
    }

    /// Trade (fill) rows for a strategy.
    pub async fn trades(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let r = self
            .http
            .get(&format!("/api/v1/strategies/{strategy_id}/trades"), &q)
            .await?;
        Ok(r["rows"].clone())
    }

    /// Log rows for a strategy.
    pub async fn logs(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let r = self
            .http
            .get(&format!("/api/v1/strategies/{strategy_id}/logs"), &q)
            .await?;
        Ok(r["rows"].clone())
    }

    // ── AI parameter optimizer ────────────────────────────────────────────────

    /// Kick off an AI-driven parameter optimization.
    pub async fn ai_opt_start(&self, strategy_id: &str, body: &Value) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/strategies/{strategy_id}/ai-opt/start"),
                body,
            )
            .await
    }

    /// Current optimization status for a strategy.
    pub async fn ai_opt_status(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/strategies/{strategy_id}/ai-opt/status"),
                &q,
            )
            .await
    }

    /// Approve and apply the optimizer's proposed params.
    pub async fn ai_opt_approve(&self, strategy_id: &str, body: &Value) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/strategies/{strategy_id}/ai-opt/approve"),
                body,
            )
            .await
    }

    /// Stop an in-progress optimization.
    pub async fn ai_opt_stop(&self, strategy_id: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/strategies/{strategy_id}/ai-opt/stop"),
                &json!({}),
            )
            .await
    }

    /// Past optimization runs for a strategy.
    pub async fn ai_opt_runs(&self, strategy_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/strategies/{strategy_id}/ai-opt/runs"),
                &q,
            )
            .await
    }
}
