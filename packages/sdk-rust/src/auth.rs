use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Auth API — login, registration, MFA, and session management.
#[derive(Clone)]
pub struct AuthAPI {
    http: HttpClient,
}

impl AuthAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Password + username login; returns session JWT + optional MFA challenge token.
    pub async fn login(&self, username: &str, password: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/login",
                &json!({ "username": username, "password": password }),
            )
            .await
    }

    /// Resolve MFA challenge after login.
    pub async fn verify_mfa(&self, challenge_token: &str, code: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/mfa/verify",
                &json!({ "challengeToken": challenge_token, "code": code }),
            )
            .await
    }

    /// Create a new user account; sends email verification.
    pub async fn register(&self, body: &Value) -> Result<Value> {
        self.http.post("/api/v1/private/auth/register", body).await
    }

    /// Confirm email address from signup link token.
    pub async fn verify_signup(&self, token: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/verify-signup",
                &json!({ "token": token }),
            )
            .await
    }

    /// Re-send email verification message.
    pub async fn resend_verification(&self, email: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/resend-verification",
                &json!({ "email": email }),
            )
            .await
    }

    /// Return current authenticated user profile.
    pub async fn me(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/auth/me", &q).await
    }

    /// Lightweight session validity check; returns `{ ok: true }`.
    pub async fn check(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/auth/check", &q).await
    }

    /// Change password (re-hashes, invalidates other sessions).
    pub async fn change_password(&self, current: &str, new: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/change-password",
                &json!({ "currentPassword": current, "newPassword": new }),
            )
            .await
    }

    /// Initiate password reset flow; sends email with reset token.
    pub async fn forgot_password(&self, email: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/forgot-password",
                &json!({ "email": email }),
            )
            .await
    }

    /// Complete password reset using token from email.
    pub async fn reset_password(&self, token: &str, new_password: &str) -> Result<Value> {
        self.http
            .post(
                "/api/v1/private/auth/reset-password",
                &json!({ "token": token, "newPassword": new_password }),
            )
            .await
    }

    /// Create a short-lived handoff token for mobile app deep-link auth.
    pub async fn create_mobile_handoff(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/auth/mobile-handoff", &json!({}))
            .await
    }

    /// Return caller's permission flags (capabilities, tier, feature gates).
    pub async fn my_permissions(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/auth/permissions", &q).await
    }

    /// Rotate session JWT (sliding expiry); returns new token.
    pub async fn refresh(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/auth/refresh", &json!({}))
            .await
    }
}

/// MFA API — TOTP enrollment and verification.
#[derive(Clone)]
pub struct MfaAPI {
    http: HttpClient,
}

impl MfaAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Return MFA enrollment status for the caller.
    pub async fn status(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/private/mfa/status", &q).await
    }

    /// Initiate TOTP setup; returns QR / secret.
    pub async fn setup(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/mfa/setup", &json!({}))
            .await
    }

    /// Confirm TOTP setup with first valid code; activates MFA.
    pub async fn confirm_setup(&self, code: &str) -> Result<Value> {
        self.http
            .post("/api/v1/private/mfa/confirm", &json!({ "code": code }))
            .await
    }
}
