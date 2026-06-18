use std::collections::HashMap;

use serde_json::{json, Value};

use crate::client::HttpClient;
use crate::error::Result;

/// Backtest API — run strategies against historical data on the Rust engine.
pub struct BacktestAPI {
    http: HttpClient,
}

impl BacktestAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Start a backtest. Returns the job id(s) — poll with [`BacktestAPI::job`].
    pub async fn start(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/private/backtest/start", body).await
    }

    /// Job status + progress.
    pub async fn job(&self, job_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/backtest/jobs/{job_id}"), &q)
            .await
    }

    /// Metrics, equity curve, and OHLCV for a completed job.
    pub async fn results(&self, job_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let r = self
            .http
            .get(&format!("/api/v1/private/backtest/results/{job_id}"), &q)
            .await?;
        Ok(r["result"].clone())
    }

    /// The trade list for a completed job.
    pub async fn trades(
        &self,
        job_id: &str,
        limit: Option<u32>,
        offset: Option<u32>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("offset", offset.map(|v| v.to_string()));
        let r = self
            .http
            .get(&format!("/api/v1/private/backtest/trades/{job_id}"), &q)
            .await?;
        Ok(r["trades"].clone())
    }

    /// Ranked children of a sweep parent.
    pub async fn sweep(
        &self,
        parent_id: &str,
        objective: Option<&str>,
        limit: Option<u32>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("objective", objective.map(str::to_owned));
        q.insert("limit", limit.map(|v| v.to_string()));
        self.http
            .get(
                &format!("/api/v1/private/backtest/sweep/{parent_id}"),
                &q,
            )
            .await
    }

    /// Your backtest jobs, newest first.
    pub async fn list(&self, limit: Option<u32>, offset: Option<u32>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("offset", offset.map(|v| v.to_string()));
        let r = self.http.get("/api/v1/private/backtest", &q).await?;
        Ok(r["data"]["jobs"].clone())
    }

    /// Your favorited backtest jobs (Forge tier and above).
    pub async fn favorites(&self, limit: Option<u32>, offset: Option<u32>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("limit", limit.map(|v| v.to_string()));
        q.insert("offset", offset.map(|v| v.to_string()));
        let r = self
            .http
            .get("/api/v1/private/backtest/favorites", &q)
            .await?;
        Ok(r["data"]["jobs"].clone())
    }

    /// Earliest funding-rate timestamp available for an exchange+symbol (ms, or null).
    pub async fn funding_range(&self, exchange: &str, symbol: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        q.insert("symbol", Some(symbol.to_owned()));
        let r = self
            .http
            .get("/api/v1/private/backtest/funding-range", &q)
            .await?;
        Ok(r["earliest_ms"].clone())
    }

    /// Cancel an in-flight job.
    pub async fn cancel(&self, job_id: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/backtest/{job_id}/cancel"),
                &json!({}),
            )
            .await
    }

    /// Soft-delete a single job.
    pub async fn delete(&self, job_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(&format!("/api/v1/private/backtest/{job_id}"), &q)
            .await
    }

    /// Soft-delete every non-favorited job.
    pub async fn delete_all(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.delete("/api/v1/private/backtest", &q).await
    }
}
