use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Team API — manage project team membership, roles, and invitations.
#[derive(Clone)]
pub struct TeamAPI {
    http: HttpClient,
}

impl TeamAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List members of a project team.
    pub async fn list_members(&self, project: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(&format!("/api/v1/private/projects/{project}/members"), &q)
            .await
    }

    /// Invite a user to a project team by username.
    pub async fn invite(&self, project: &str, username: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/projects/{project}/members/invite"),
                &json!({ "username": username }),
            )
            .await
    }

    /// Create a shareable invite link for a project.
    pub async fn create_invite_link(&self, project: &str) -> Result<Value> {
        self.http
            .post(
                &format!("/api/v1/private/projects/{project}/invite-link"),
                &json!({}),
            )
            .await
    }

    /// Accept a project invite using the token from an invite link.
    pub async fn accept_invite(&self, token: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/team/invite/accept",
                &json!({ "token": token }),
            )
            .await
    }

    /// Update a team member's role in a project.
    pub async fn update_member_role(
        &self,
        project: &str,
        user_id: &str,
        role: &str,
    ) -> Result<Value> {
        self.http
            .patch(
                &format!("/api/v1/private/projects/{project}/members/{user_id}"),
                &json!({ "role": role }),
            )
            .await
    }

    /// Remove a member from a project team.
    pub async fn remove_member(&self, project: &str, user_id: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .delete(
                &format!("/api/v1/private/projects/{project}/members/{user_id}"),
                &q,
            )
            .await
    }

    // ── Pipeline visibility ──────────────────────────────────────────────────

    /// Get visibility settings for a pipeline within a project.
    pub async fn get_pipeline_visibility(&self, project: &str, pipeline: &str) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get(
                &format!("/api/v1/private/projects/{project}/pipelines/{pipeline}/visibility"),
                &q,
            )
            .await
    }

    /// Set pipeline visibility within a project.
    pub async fn set_pipeline_visibility(
        &self,
        project: &str,
        pipeline: &str,
        body: &Value,
    ) -> Result<Value> {
        self.http
            .put(
                &format!("/api/v1/private/projects/{project}/pipelines/{pipeline}/visibility"),
                body,
            )
            .await
    }
}
