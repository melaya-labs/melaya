use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Bugs API — submit and track bug reports.
#[derive(Clone)]
pub struct BugsAPI {
    http: HttpClient,
}

impl BugsAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Submit a bug report.
    pub async fn create(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/private/bugs", body).await
    }

    /// List bug reports submitted by the caller.
    pub async fn list_mine(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/bugs/mine", &q).await
    }

    /// Get a single bug report by ID.
    pub async fn get(&self, bug_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/bugs/{bug_id}"), &q)
            .await
    }

    /// Add a comment to a bug report.
    pub async fn add_comment(&self, bug_id: &str, comment: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/bugs/{bug_id}/comments"),
                &json!({ "comment": comment }),
            )
            .await
    }

    /// List unread bug-related notifications for the caller.
    pub async fn list_notifications(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/bugs/notifications", &q)
            .await
    }

    /// Mark bug notifications as read.
    pub async fn mark_notifications_read(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/bugs/notifications/read", &json!({}))
            .await
    }
}
