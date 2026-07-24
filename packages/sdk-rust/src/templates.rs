use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Templates API — create, manage, and share pipeline templates.
#[derive(Clone)]
pub struct TemplatesAPI {
    http: HttpClient,
}

impl TemplatesAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List all templates visible to the caller — own + team + community + assigned.
    pub async fn list(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/user-templates", &q).await
    }

    /// List all community-visibility (global) templates.
    pub async fn list_global(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/templates/global", &q).await
    }

    /// List IDs of all validated (platform-approved) templates.
    pub async fn list_validated(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/templates/validated", &q)
            .await
    }

    /// Create a new private user template.
    pub async fn save(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/private/user-templates", body).await
    }

    /// Update name/description/category/payload of a private user template.
    pub async fn update(&self, template_id: &str, body: &Value) -> Result<Value> {
        self.http
            .patch(
                &format!("/api/v1/private/user-templates/{template_id}"),
                body,
            )
            .await
    }

    /// Duplicate a readable template into the caller's private library.
    pub async fn duplicate(&self, template_id: &str, new_name: Option<&str>) -> Result<Value> {
        let mut body = json!({});
        if let Some(n) = new_name {
            body["newName"] = json!(n);
        }
        self.http
            .post(
                &format!("/api/v1/private/user-templates/{template_id}/duplicate"),
                &body,
            )
            .await
    }

    /// Delete (or soft-demote if shared) a template.
    pub async fn delete(&self, template_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(&format!("/api/v1/private/user-templates/{template_id}"), &q)
            .await
    }

    /// Change the visibility of a template.
    /// `visibility` is one of: `"private"`, `"team"`, `"community"`, `"assigned"`.
    pub async fn share(&self, template_id: &str, visibility: &str) -> Result<Value> {
        self.http
            .put(
                &format!("/api/v1/private/user-templates/{template_id}/visibility"),
                &json!({ "visibility": visibility }),
            )
            .await
    }

    // ── Assignments ──────────────────────────────────────────────────────────

    /// List all assignments (users / projects) for a template.
    pub async fn list_assignments(&self, template_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/user-templates/{template_id}/assignments"),
                &q,
            )
            .await
    }

    /// Assign a template to a user or project.
    ///
    /// Provide exactly one of `user_id` / `project_id` (both UUIDs) — the
    /// server rejects requests carrying both or neither. The target is sent
    /// in the JSON body as `{ "userId": ... }` or `{ "projectId": ... }`.
    pub async fn assign(
        &self,
        template_id: &str,
        user_id: Option<&str>,
        project_id: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({});
        if let Some(u) = user_id {
            body["userId"] = json!(u);
        }
        if let Some(p) = project_id {
            body["projectId"] = json!(p);
        }
        self.http
            .post(
                &format!("/api/v1/private/user-templates/{template_id}/assignments"),
                &body,
            )
            .await
    }

    /// Remove an assignment from a template.
    ///
    /// Provide exactly one of `user_id` / `project_id` (both UUIDs). The
    /// target travels as query params — the REST bridge ignores DELETE
    /// bodies, so `userId` / `projectId` must be in the query string.
    pub async fn unassign(
        &self,
        template_id: &str,
        user_id: Option<&str>,
        project_id: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("userId", user_id.map(str::to_owned));
        q.insert("projectId", project_id.map(str::to_owned));
        self.http
            .delete(
                &format!("/api/v1/private/user-templates/{template_id}/assignments"),
                &q,
            )
            .await
    }

    /// List projects the caller is a member of (for use in the share target picker).
    pub async fn share_targets(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/user-templates/share-targets", &q)
            .await
    }
}
