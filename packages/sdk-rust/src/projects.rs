use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Projects API — create and list agent projects.
#[derive(Clone)]
pub struct ProjectsAPI {
    http: HttpClient,
}

impl ProjectsAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List all projects the authenticated user can access (owned + team member).
    pub async fn list(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/projects", &q).await
    }

    /// Create a new agent project.
    pub async fn create(&self, name: &str, description: Option<&str>) -> Result<Value> {
        let mut body = json!({ "name": name });
        if let Some(d) = description {
            body["description"] = json!(d);
        }
        self.http.post("/api/v1/private/projects", &body).await
    }

    /// Rename a project.
    pub async fn rename(&self, old_name: &str, new_name: &str) -> Result<Value> {
        self.http
            .patch(
                "/api/v1/private/projects/rename",
                &json!({ "oldName": old_name, "newName": new_name }),
            )
            .await
    }

    /// Get projects list (runner-facing).
    pub async fn runner_projects(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/projects/runner", &q).await
    }
}
