use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Project Connectors API — manage credentials at project scope.
#[derive(Clone)]
pub struct ConnectorsAPI {
    http: HttpClient,
}

impl ConnectorsAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List connected services for a project.
    pub async fn connected_services(&self, project: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/projects/{project}/connectors/services"),
                &q,
            )
            .await
    }

    /// Store a connector credential at project scope.
    pub async fn set(
        &self,
        project: &str,
        service: &str,
        value: &str,
        key: Option<&str>,
        label: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({ "value": value });
        if let Some(k) = key {
            body["key"] = json!(k);
        }
        if let Some(l) = label {
            body["label"] = json!(l);
        }
        self.http
            .put(
                &format!("/api/v1/private/projects/{project}/connectors/{service}"),
                &body,
            )
            .await
    }

    /// Delete a project-scoped connector credential.
    pub async fn delete(&self, project: &str, service: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(
                &format!("/api/v1/private/projects/{project}/connectors/{service}"),
                &q,
            )
            .await
    }

    /// Get a short-lived env-handle token for project-scoped credentials.
    pub async fn env_handle(&self, project: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/projects/{project}/connectors/env-handle"),
                &json!({}),
            )
            .await
    }

    /// Start Google OAuth flow for project-scoped connector.
    pub async fn google_oauth_start(&self, project: &str, body: &Value) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/projects/{project}/connectors/google/oauth"),
                body,
            )
            .await
    }
}
