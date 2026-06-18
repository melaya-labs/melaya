use std::collections::HashMap;

use serde_json::Value;

use crate::client::HttpClient;
use crate::error::Result;

/// Account API — authenticated reads about your Melaya account.
pub struct AccountAPI {
    http: HttpClient,
}

impl AccountAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// The exchange API keys connected to your account.
    /// `apiKey` is masked (display-only); use `apiKeyId` as the reference.
    pub async fn keys(&self) -> Result<Value> {
        let q = HashMap::new();
        let r = self.http.get("/api/v1/private/keys", &q).await?;
        Ok(r["keys"].clone())
    }

    /// Tier, plan limits, and live usage counters.
    pub async fn usage(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/usage", &q).await
    }

    /// Status of your platform API key (tier, max concurrent connections).
    pub async fn api_key_status(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/api-key", &q).await
    }
}
