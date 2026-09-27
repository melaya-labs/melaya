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

    /// Copy the caller's own personal connector credential into the
    /// project's shared pool (requires editor or owner). Values stay
    /// server-side. `google_capabilities` scopes which Google capabilities
    /// to share when `service` is `"google"`.
    pub async fn apply_personal(
        &self,
        project: &str,
        service: &str,
        google_capabilities: Option<&[&str]>,
    ) -> Result<Value> {
        let mut body = json!({});
        if let Some(caps) = google_capabilities {
            body["googleCapabilities"] = json!(caps);
        }
        self.http
            .post(
                &format!("/api/v1/private/projects/{project}/connectors/{service}/apply-personal"),
                &body,
            )
            .await
    }

    /// Which member shared each connected project connector (usernames
    /// only — never credential values).
    pub async fn shared_by(&self, project: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/projects/{project}/connectors/shared-by"),
                &q,
            )
            .await
    }

    /// List the Google OAuth capabilities actually granted to a project.
    pub async fn google_status(&self, project: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/projects/{project}/connectors/google/status"),
                &q,
            )
            .await
    }

    /// Select the project's connected Google account used by one capability.
    pub async fn google_set_default(
        &self,
        project: &str,
        capability: &str,
        account_id: &str,
    ) -> Result<Value> {
        let body = json!({ "capability": capability, "accountId": account_id });
        self.http
            .put(
                &format!("/api/v1/private/projects/{project}/connectors/google/default"),
                &body,
            )
            .await
    }

    /// Disconnect one Google product, or an entire project Google account
    /// when `capability` is omitted.
    pub async fn google_disconnect(
        &self,
        project: &str,
        account_id: &str,
        capability: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({ "accountId": account_id });
        if let Some(c) = capability {
            body["capability"] = json!(c);
        }
        self.http
            .delete_with_body(
                &format!("/api/v1/private/projects/{project}/connectors/google/access"),
                &body,
            )
            .await
    }

    /// Test a project database connector from the user's own runner
    /// (reaches IP-allow-listed / VPC hosts). Returns `{ sessionId, ... }`.
    pub async fn db_test_start(
        &self,
        project: &str,
        service: &str,
        credentials: Option<&Value>,
    ) -> Result<Value> {
        let mut body = json!({ "service": service });
        if let Some(c) = credentials {
            body["credentials"] = c.clone();
        }
        self.http
            .post(
                &format!("/api/v1/private/projects/{project}/connectors/db-test"),
                &body,
            )
            .await
    }

    /// Poll a project database connector runner-test result.
    pub async fn db_test_status(&self, project: &str, session_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/projects/{project}/connectors/db-test/{session_id}"),
                &q,
            )
            .await
    }
}
