use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Runner API — mint, list, and revoke runner tokens (`mel_run_` prefix).
#[derive(Clone)]
pub struct RunnerAPI {
    http: HttpClient,
}

impl RunnerAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Mint a new runner token.
    /// The plaintext token is returned only in this response — store it securely.
    pub async fn create_token(&self, label: Option<&str>) -> Result<Value> {
        let mut body = json!({});
        if let Some(l) = label {
            body["label"] = json!(l);
        }
        self.http.post("/api/v1/private/runner/tokens", &body).await
    }

    /// List all runner tokens for the caller (masked, with last_seen).
    pub async fn list_tokens(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/runner/tokens", &q).await
    }

    /// Revoke a runner token by ID.
    pub async fn revoke_token(&self, token_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(&format!("/api/v1/private/runner/tokens/{token_id}"), &q)
            .await
    }
}
