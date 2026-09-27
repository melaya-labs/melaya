use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Credentials API — store, retrieve, test, and delete secrets and third-party
/// service connections at user scope.
#[derive(Clone)]
pub struct CredentialsAPI {
    http: HttpClient,
}

impl CredentialsAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List all stored credentials (services, OAuth connections, env handles).
    pub async fn list(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/credentials", &q).await
    }

    /// List connected third-party services.
    pub async fn connected_services(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/services", &q)
            .await
    }

    /// Get a stored credential value by service name.
    pub async fn get(&self, service: &str, key: Option<&str>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("key", key.map(str::to_owned));
        self.http
            .get(&format!("/api/v1/private/credentials/{service}"), &q)
            .await
    }

    /// Store or update a credential (envelope-encrypted at rest).
    pub async fn set(
        &self,
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
            .put(&format!("/api/v1/private/credentials/{service}"), &body)
            .await
    }

    /// Delete a stored credential by service name.
    pub async fn delete(&self, service: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(&format!("/api/v1/private/credentials/{service}"), &q)
            .await
    }

    /// Test a stored credential (e.g. validate an API key against its target service).
    pub async fn test(&self, service: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/credentials/{service}/test"),
                &json!({}),
            )
            .await
    }

    // ── Operator profile ─────────────────────────────────────────────────────

    /// Get the operator profile (persona config injected into agent context).
    pub async fn get_operator_profile(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/operator-profile", &q)
            .await
    }

    /// Save the operator profile.
    pub async fn set_operator_profile(&self, profile: &Value) -> Result<Value> {
        self.http
            .put("/api/v1/private/credentials/operator-profile", profile)
            .await
    }

    // ── AI models ─────────────────────────────────────────────────────────────

    /// List available AI models across all configured providers.
    pub async fn list_models(
        &self,
        provider: Option<&str>,
        capability: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("provider", provider.map(str::to_owned));
        q.insert("capability", capability.map(str::to_owned));
        self.http
            .get("/api/v1/private/credentials/models", &q)
            .await
    }

    // ── RAG (ingestion + retrieval) ──────────────────────────────────────────

    /// Start a RAG document ingestion job.
    pub async fn rag_ingest_start(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/private/rag/ingest", body).await
    }

    /// Poll RAG ingestion job status.
    pub async fn rag_ingest_status(&self, session_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/rag/ingest/{session_id}"), &q)
            .await
    }

    /// Start a RAG retrieval query.
    pub async fn rag_retrieve_start(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/private/rag/retrieve", body).await
    }

    /// Poll RAG retrieval result.
    pub async fn rag_retrieve_status(&self, session_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/rag/retrieve/{session_id}"), &q)
            .await
    }

    // ── Folder picker ────────────────────────────────────────────────────────

    /// Initiate native folder picker for file ingestion.
    pub async fn pick_folder_start(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/rag/pick-folder", &json!({}))
            .await
    }

    /// Poll folder picker result.
    pub async fn pick_folder_status(&self, session_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/rag/pick-folder/{session_id}"), &q)
            .await
    }

    // ── LinkedIn OAuth ───────────────────────────────────────────────────────

    /// Start LinkedIn OAuth flow.
    pub async fn linkedin_connect_start(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/credentials/linkedin/connect", &json!({}))
            .await
    }

    /// Cancel an in-progress LinkedIn OAuth flow.
    pub async fn linkedin_connect_cancel(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete("/api/v1/private/credentials/linkedin/connect", &q)
            .await
    }

    /// Poll LinkedIn OAuth connection status.
    pub async fn linkedin_connect_status(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/linkedin/connect/status", &q)
            .await
    }

    // ── Luma OAuth ──────────────────────────────────────────────────────────

    /// Start Luma OAuth flow.
    pub async fn luma_connect_start(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/credentials/luma/connect", &json!({}))
            .await
    }

    /// Poll Luma OAuth connection status.
    pub async fn luma_connect_status(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/luma/connect/status", &q)
            .await
    }

    /// Cancel an in-progress Luma OAuth flow.
    pub async fn luma_connect_cancel(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete("/api/v1/private/credentials/luma/connect", &q)
            .await
    }

    /// Get the Luma event registration form schema for a given event.
    pub async fn get_luma_registration_schema(&self, event_id: Option<&str>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("eventId", event_id.map(str::to_owned));
        self.http
            .get("/api/v1/private/credentials/luma/schema", &q)
            .await
    }

    // ── Google OAuth ─────────────────────────────────────────────────────────

    /// Start Google OAuth flow for credential storage.
    pub async fn google_oauth_start(&self, body: &Value) -> Result<Value> {
        self.http
            .post("/api/v1/private/credentials/google/oauth", body)
            .await
    }

    // ── CLI auth ─────────────────────────────────────────────────────────────

    /// Start CLI authentication flow (device-code style).
    pub async fn cli_auth_start(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/credentials/cli-auth", &json!({}))
            .await
    }

    // ── NotebookLM ───────────────────────────────────────────────────────────

    /// Store NotebookLM credentials.
    pub async fn notebooklm_login(&self, body: &Value) -> Result<Value> {
        self.http
            .post("/api/v1/private/credentials/notebooklm/login", body)
            .await
    }

    /// Check NotebookLM connection status.
    pub async fn notebooklm_status(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/notebooklm/status", &q)
            .await
    }

    // ── Telegram auth ────────────────────────────────────────────────────────

    /// Start Telegram user auth (phone number step).
    pub async fn telegram_auth_start(&self, phone: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/credentials/telegram/auth",
                &json!({ "phone": phone }),
            )
            .await
    }

    /// Submit Telegram SMS verification code.
    pub async fn telegram_auth_code(&self, code: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/credentials/telegram/auth/code",
                &json!({ "code": code }),
            )
            .await
    }

    /// Submit Telegram 2FA password.
    pub async fn telegram_auth_2fa(&self, password: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/credentials/telegram/auth/2fa",
                &json!({ "password": password }),
            )
            .await
    }

    // ── Melaya accounts ──────────────────────────────────────────────────────

    /// List Melaya sub-accounts available to the caller.
    pub async fn melaya_accounts(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/melaya-accounts", &q)
            .await
    }

    // ── Google OAuth (account status) ────────────────────────────────────────

    /// List the Google OAuth capabilities actually granted to the caller.
    pub async fn google_status(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/google/status", &q)
            .await
    }

    /// Select the connected Google account used by one capability.
    pub async fn google_set_default(&self, capability: &str, account_id: &str) -> Result<Value> {
        let body = json!({ "capability": capability, "accountId": account_id });
        self.http
            .put("/api/v1/private/credentials/google/default", &body)
            .await
    }

    /// Disconnect one Google product, or an entire Google account when
    /// `capability` is omitted.
    pub async fn google_disconnect(
        &self,
        account_id: &str,
        capability: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({ "accountId": account_id });
        if let Some(c) = capability {
            body["capability"] = json!(c);
        }
        self.http
            .delete_with_body("/api/v1/private/credentials/google/access", &body)
            .await
    }

    // ── Database connector test ───────────────────────────────────────────────

    /// Test a database connector from the user's own runner (reaches
    /// IP-allow-listed / VPC hosts). Returns `{ sessionId, ... }`.
    pub async fn db_test_start(&self, service: &str, credentials: Option<&Value>) -> Result<Value> {
        let mut body = json!({ "service": service });
        if let Some(c) = credentials {
            body["credentials"] = c.clone();
        }
        self.http
            .post("/api/v1/private/credentials/db-test", &body)
            .await
    }

    /// Poll a database connector runner-test result.
    pub async fn db_test_status(&self, session_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/credentials/db-test/{session_id}"),
                &q,
            )
            .await
    }

    // ── Telegram QR login ────────────────────────────────────────────────────

    /// Start Telegram user QR login.
    pub async fn telegram_qr_start(&self, api_id: i64, api_hash: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/credentials/telegram/auth/qr/start",
                &json!({ "api_id": api_id, "api_hash": api_hash }),
            )
            .await
    }

    /// Poll Telegram user QR login. `handle` starts with `"tgauth_"`.
    pub async fn telegram_qr_poll(&self, handle: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/credentials/telegram/auth/qr/poll",
                &json!({ "handle": handle }),
            )
            .await
    }

    // ── WhatsApp Embedded Signup ─────────────────────────────────────────────

    /// Get the WhatsApp Embedded Signup config (`appId` / `configId`).
    pub async fn whatsapp_signup_config(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                "/api/v1/private/credentials/whatsapp/embedded-signup/config",
                &q,
            )
            .await
    }

    /// Complete WhatsApp Embedded Signup by exchanging the returned code.
    pub async fn whatsapp_signup_exchange(
        &self,
        code: &str,
        phone_number_id: &str,
        waba_id: &str,
        project: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({
            "code": code,
            "phoneNumberId": phone_number_id,
            "wabaId": waba_id,
        });
        if let Some(p) = project {
            body["project"] = json!(p);
        }
        self.http
            .post(
                "/api/v1/private/credentials/whatsapp/embedded-signup/exchange",
                &body,
            )
            .await
    }

    // ── TikTok ────────────────────────────────────────────────────────────────

    /// Get the connected TikTok account's creator info (nickname, allowed
    /// privacy levels, interaction availability) for the compliant
    /// Post-to-TikTok approval UI.
    pub async fn tiktok_creator_info(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/credentials/tiktok/creator-info", &q)
            .await
    }

    // ── Substack ──────────────────────────────────────────────────────────────

    /// Ask Substack to email a sign-in link.
    pub async fn substack_email_link_send(&self, email: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/credentials/substack/email-link",
                &json!({ "email": email }),
            )
            .await
    }

    /// Finish Substack sign-in with the emailed link.
    pub async fn substack_email_link_redeem(
        &self,
        link: &str,
        email: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({ "link": link });
        if let Some(e) = email {
            body["email"] = json!(e);
        }
        self.http
            .post(
                "/api/v1/private/credentials/substack/email-link/redeem",
                &body,
            )
            .await
    }
}
