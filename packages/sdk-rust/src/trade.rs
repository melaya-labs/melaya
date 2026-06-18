//! Live trading API — credentialed order placement, account state, and
//! position management on a CONNECTED exchange.
//!
//! Every method POSTs to `/api/v1/private/<op>`; the server resolves the
//! connected key (by `apiKeyId` in the body) and forwards to the venue via the
//! in-house Rust engine. Responses share the envelope
//! `{ ok, exchange, operation, orderId, clientOrderId, payload, data, ... }`.
//!
//! ⚠️ The write methods (create_order, cancel_order, amend_order,
//! cancel_all_orders, cancel_plan_orders, close_position, set_leverage,
//! set_margin_mode, set_position_mode) move REAL funds. Use the sim broker for
//! risk-free testing. Each method takes a `serde_json::Value` body (build it
//! with `serde_json::json!({ "exchange": ..., "apiKeyId": ..., ... })`).

use crate::client::HttpClient;
use crate::error::Result;
use serde_json::Value;

pub struct TradeAPI {
    http: HttpClient,
}

impl TradeAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    async fn op(&self, op: &str, body: Value) -> Result<Value> {
        self.http.post(&format!("/api/v1/private/{op}"), &body).await
    }

    // ── Account state (reads) ─────────────────────────────────────────────
    pub async fn balance(&self, body: Value) -> Result<Value> { self.op("balance", body).await }
    pub async fn positions(&self, body: Value) -> Result<Value> { self.op("positions", body).await }
    pub async fn positions_history(&self, body: Value) -> Result<Value> { self.op("positions-history", body).await }
    pub async fn open_orders(&self, body: Value) -> Result<Value> { self.op("open-orders", body).await }
    pub async fn orders(&self, body: Value) -> Result<Value> { self.op("orders", body).await }
    pub async fn closed_orders(&self, body: Value) -> Result<Value> { self.op("closed-orders", body).await }
    pub async fn my_trades(&self, body: Value) -> Result<Value> { self.op("my-trades", body).await }
    pub async fn my_trades_history(&self, body: Value) -> Result<Value> { self.op("my-trades-history", body).await }
    pub async fn plan_orders(&self, body: Value) -> Result<Value> { self.op("plan-orders", body).await }
    pub async fn leverage(&self, body: Value) -> Result<Value> { self.op("leverage", body).await }
    pub async fn leverage_tiers(&self, body: Value) -> Result<Value> { self.op("leverage-tiers", body).await }

    // ── Order placement & management (LIVE writes — real funds) ───────────
    pub async fn create_order(&self, body: Value) -> Result<Value> { self.op("create-order", body).await }
    pub async fn cancel_order(&self, body: Value) -> Result<Value> { self.op("cancel-order", body).await }
    pub async fn amend_order(&self, body: Value) -> Result<Value> { self.op("amend-order", body).await }
    pub async fn cancel_all_orders(&self, body: Value) -> Result<Value> { self.op("cancel-all-orders", body).await }
    pub async fn cancel_plan_orders(&self, body: Value) -> Result<Value> { self.op("cancel-plan-orders", body).await }
    pub async fn close_position(&self, body: Value) -> Result<Value> { self.op("close-position", body).await }
    pub async fn set_leverage(&self, body: Value) -> Result<Value> { self.op("set-leverage", body).await }
    pub async fn set_margin_mode(&self, body: Value) -> Result<Value> { self.op("set-margin-mode", body).await }
    pub async fn set_position_mode(&self, body: Value) -> Result<Value> { self.op("set-position-mode", body).await }
}
