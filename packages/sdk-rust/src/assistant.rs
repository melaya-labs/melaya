use serde_json::Value;
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Assistant API — get and save the caller's onboarding / persona profile.
#[derive(Clone)]
pub struct AssistantAPI {
    http: HttpClient,
}

impl AssistantAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Get the caller's assistant onboarding profile.
    pub async fn get_profile(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/assistant/profile", &q).await
    }

    /// Save the caller's assistant onboarding profile.
    pub async fn set_profile(&self, profile: &Value) -> Result<Value> {
        self.http
            .put("/api/v1/private/assistant/profile", profile)
            .await
    }
}
