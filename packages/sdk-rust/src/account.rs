use std::collections::HashMap;

use serde_json::{json, Value};

use crate::client::HttpClient;
use crate::error::Result;

/// Account API — authenticated reads about your Melaya account.
#[derive(Clone)]
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

    /// Generate a new platform API key, replacing the current one at once.
    /// Returns `{ apiKey }`; the new key is returned ONCE, so store it.
    ///
    /// **Warning:** the old key stops working immediately. If you call this
    /// with the key this client uses, every later call of this client fails
    /// until you build a new client with the returned key.
    pub async fn rotate_api_key(&self) -> Result<Value> {
        self.http.post("/api/v1/private/api-key", &json!({})).await
    }

    /// Revoke the platform API key. Returns `{ ok }`.
    ///
    /// **Warning:** if this client uses that key, it stops working
    /// immediately.
    pub async fn revoke_api_key(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.delete("/api/v1/private/api-key", &q).await
    }

    /// Request counts of your platform API key (current key, merged with
    /// your account totals).
    pub async fn api_key_usage(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/api-key/usage", &q).await
    }
}
