use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Accounts Platform API — profile, credits, keys, and GDPR data export.
///
/// This module covers the platform-level account management endpoints
/// (distinct from `AccountAPI` which covers the trading-plane account reads).
#[derive(Clone)]
pub struct AccountsPlatformAPI {
    http: HttpClient,
}

impl AccountsPlatformAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// GDPR Art 15/20 data export; returns JSON blob of all user data.
    pub async fn export_my_data(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/accounts/export", &json!({}))
            .await
    }

    /// Remove a stored CEX API key.
    pub async fn remove_key(&self, key_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(&format!("/api/v1/private/keys/{key_id}"), &q)
            .await
    }

    /// Update user display name, avatar, or settings.
    pub async fn update_profile(&self, body: &Value) -> Result<Value> {
        self.http
            .patch("/api/v1/private/accounts/profile", body)
            .await
    }

    /// Return current credit balance and transaction history.
    pub async fn credits(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/accounts/credits", &q).await
    }

    /// Return AI/LLM credit balance.
    pub async fn ai_credits(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/accounts/credits/ai", &q)
            .await
    }

    /// Return portfolio-ideas feature credit balance.
    pub async fn portfolio_ideas_credits(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/accounts/credits/portfolio-ideas", &q)
            .await
    }

    /// Return risk-monitoring feature credit balance.
    pub async fn risk_monitoring_credits(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/accounts/credits/risk-monitoring", &q)
            .await
    }
}
