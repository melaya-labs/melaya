use serde_json::{json, Value};
use std::collections::HashMap;

use crate::client::HttpClient;
use crate::error::Result;

/// Billing API — subscription status, Stripe checkout/portal sessions, and
/// public pricing plans.
#[derive(Clone)]
pub struct BillingAPI {
    http: HttpClient,
}

impl BillingAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Get the caller's current Stripe subscription status and tier.
    pub async fn subscription(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http
            .get("/api/v1/private/billing/subscription", &q)
            .await
    }

    /// Create a Stripe Checkout session for a tier upgrade.
    /// Returns a URL to redirect the user to.
    pub async fn create_checkout(
        &self,
        price_id: Option<&str>,
        tier: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({});
        if let Some(p) = price_id {
            body["priceId"] = json!(p);
        }
        if let Some(t) = tier {
            body["tier"] = json!(t);
        }
        self.http
            .post("/api/v1/private/billing/checkout", &body)
            .await
    }

    /// Create a Stripe Customer Portal session for managing the subscription.
    /// Returns a URL to redirect the user to.
    pub async fn create_portal(&self) -> Result<Value> {
        self.http
            .post("/api/v1/private/billing/portal", &json!({}))
            .await
    }

    /// Return public pricing plan details (price IDs for forge/bastion/citadel tiers).
    pub async fn plans(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/billing/plans", &q).await
    }
}
