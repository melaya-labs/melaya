use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::credentials::account_body;
use crate::error::Result;
use crate::pipelines::encode_segment;

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

    // ── Several accounts per project connector (owner only for writes) ───────
    //
    // Same model as the personal-scope account methods on `credentials`:
    // agents use the DEFAULT account unless a tool call names another one,
    // and only labels and ids come back (never credential values). Each
    // method returns the account list `[{ id, label, isDefault, createdAt }]`.

    /// Accounts connected to one project connector (labels and ids only).
    pub async fn accounts(&self, project: &str, service: &str) -> Result<Value> {
        let q = HashMap::new();
        let enc_project = encode_segment(project);
        let enc_service = encode_segment(service);
        self.http
            .get(
                &format!(
                    "/api/v1/private/projects/{enc_project}/connectors/{enc_service}/accounts"
                ),
                &q,
            )
            .await
    }

    /// Add another account to a project connector (owner; the connection is
    /// tested first). `fields` is a JSON object of strings (the connector's
    /// credential fields); `current_label` names the existing single
    /// connection when it is adopted as the first account. `None` values are
    /// omitted. Returns the updated list.
    pub async fn add_account(
        &self,
        project: &str,
        service: &str,
        fields: &Value,
        label: Option<&str>,
        current_label: Option<&str>,
        make_default: Option<bool>,
    ) -> Result<Value> {
        let body = account_body(fields, label, current_label, make_default);
        let enc_project = encode_segment(project);
        let enc_service = encode_segment(service);
        self.http
            .post(
                &format!(
                    "/api/v1/private/projects/{enc_project}/connectors/{enc_service}/accounts"
                ),
                &body,
            )
            .await
    }

    /// Choose which account the project connector uses (owner). Returns the
    /// updated list.
    pub async fn set_default_account(
        &self,
        project: &str,
        service: &str,
        account_id: &str,
    ) -> Result<Value> {
        let enc_project = encode_segment(project);
        let enc_service = encode_segment(service);
        self.http
            .put(
                &format!(
                    "/api/v1/private/projects/{enc_project}/connectors/{enc_service}/accounts/default"
                ),
                &json!({ "accountId": account_id }),
            )
            .await
    }

    /// Rename one account of a project connector (owner, max 80 chars).
    /// Returns the updated list.
    pub async fn rename_account(
        &self,
        project: &str,
        service: &str,
        account_id: &str,
        label: &str,
    ) -> Result<Value> {
        let enc_project = encode_segment(project);
        let enc_service = encode_segment(service);
        let enc_account = encode_segment(account_id);
        self.http
            .put(
                &format!(
                    "/api/v1/private/projects/{enc_project}/connectors/{enc_service}/accounts/{enc_account}"
                ),
                &json!({ "label": label }),
            )
            .await
    }

    /// Remove one account from a project connector (owner). Returns the
    /// remaining list.
    pub async fn remove_account(
        &self,
        project: &str,
        service: &str,
        account_id: &str,
    ) -> Result<Value> {
        let q = HashMap::new();
        let enc_project = encode_segment(project);
        let enc_service = encode_segment(service);
        let enc_account = encode_segment(account_id);
        self.http
            .delete(
                &format!(
                    "/api/v1/private/projects/{enc_project}/connectors/{enc_service}/accounts/{enc_account}"
                ),
                &q,
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
