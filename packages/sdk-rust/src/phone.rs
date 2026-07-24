use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Phone API — pair and control Android devices connected to the Melaya runner.
#[derive(Clone)]
pub struct PhoneAPI {
    http: HttpClient,
}

impl PhoneAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Start phone device pairing — generates a pairing code to enter on the Melaya APK.
    pub async fn pair(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/phone/pair", &json!({}))
            .await
    }

    /// List all paired phone devices for the authenticated user.
    pub async fn list_devices(&self) -> Result<Value> {
        let q = HashMap::new();
        let response = self.http.get("/api/v1/private/phone/devices", &q).await?;
        Ok(response
            .get("devices")
            .cloned()
            .unwrap_or_else(|| json!([])))
    }

    /// Revoke a paired phone device by ID.
    pub async fn revoke_device(&self, device_id: &str) -> Result<Value> {
        let q = HashMap::new();
        let encoded_id: String =
            url::form_urlencoded::byte_serialize(device_id.as_bytes()).collect();
        self.http
            .delete(&format!("/api/v1/private/phone/devices/{encoded_id}"), &q)
            .await
    }

    /// Get the current accessibility tree from the paired phone's screen.
    pub async fn screen_tree(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/phone/screen-tree", &q).await
    }

    /// List installed apps on the paired phone.
    pub async fn list_apps(&self) -> Result<Value> {
        let q = HashMap::new();
        let response = self.http.get("/api/v1/private/phone/apps", &q).await?;
        Ok(response
            .get("result")
            .and_then(|result| result.get("apps"))
            .cloned()
            .unwrap_or_else(|| json!([])))
    }

    /// Set the allowlist of apps that agents are permitted to interact with.
    /// Pass an array of package names.
    pub async fn set_allowed_apps(&self, package_names: &[&str]) -> Result<Value> {
        self.http
            .put(
                "/api/v1/private/phone/apps/allowed",
                &json!({
                    "apps": package_names
                        .iter()
                        .map(|package| json!({ "package": package }))
                        .collect::<Vec<_>>()
                }),
            )
            .await
    }

    /// Register the currently active pipeline run on the phone (used by agents).
    pub async fn register_active_run(&self, run_id: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/phone/active-run",
                &json!({ "runId": run_id }),
            )
            .await
    }
}
