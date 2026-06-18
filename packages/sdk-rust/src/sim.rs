use std::collections::HashMap;

use serde_json::{json, Value};

use crate::client::HttpClient;
use crate::error::Result;

/// Paper-trading (sim broker) API.
///
/// Scoped to a strategy created with `dryRun: true`. No venue-side state
/// ever changes; fills are synthesised from the live ticker tape.
pub struct SimAPI {
    http: HttpClient,
}

impl SimAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// Paper accounts (one virtual wallet per paper strategy).
    pub async fn list_accounts(&self) -> Result<Value> {
        let q = HashMap::new();
        let r = self.http.get("/api/v1/private/sim/list-accounts", &q).await?;
        // May be a bare array or { accounts: [...] }
        if r.is_array() {
            Ok(r)
        } else {
            Ok(r["accounts"].clone())
        }
    }

    /// Virtual balance for a paper strategy.
    pub async fn balance(&self, strategy_id: &str, asset: Option<&str>) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("strategy_id", Some(strategy_id.to_owned()));
        q.insert("asset", asset.map(str::to_owned));
        self.http.get("/api/v1/private/sim/balance", &q).await
    }

    /// Open paper positions for a strategy.
    pub async fn positions(&self, strategy_id: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("strategy_id", Some(strategy_id.to_owned()));
        let r = self.http.get("/api/v1/private/sim/positions", &q).await?;
        if r.is_array() {
            Ok(r)
        } else {
            Ok(r["positions"].clone())
        }
    }

    /// Resting paper orders for a strategy.
    pub async fn open_orders(&self, strategy_id: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("strategy_id", Some(strategy_id.to_owned()));
        let r = self.http.get("/api/v1/private/sim/open-orders", &q).await?;
        if r.is_array() {
            Ok(r)
        } else {
            Ok(r["orders"].clone())
        }
    }

    /// Filled paper trades for a strategy.
    pub async fn my_trades(&self, strategy_id: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("strategy_id", Some(strategy_id.to_owned()));
        let r = self.http.get("/api/v1/private/sim/my-trades", &q).await?;
        if r.is_array() {
            Ok(r)
        } else {
            Ok(r["trades"].clone())
        }
    }

    /// Place a paper order. Fills synthesise from the live ticker.
    #[allow(clippy::too_many_arguments)]
    pub async fn create_order(
        &self,
        strategy_id: &str,
        exchange: &str,
        symbol: &str,
        side: &str,
        amount: f64,
        order_type: Option<&str>,
        price: Option<f64>,
        market: Option<&str>,
        leverage: Option<f64>,
        reduce_only: Option<bool>,
        sl_price: Option<f64>,
        tp_price: Option<f64>,
        client_order_id: Option<&str>,
    ) -> Result<Value> {
        let ot = order_type.unwrap_or("market");
        let mut body = json!({
            "strategy_id": strategy_id,
            "exchange": exchange,
            "symbol": symbol,
            "side": side,
            "amount": amount,
            "order_type": ot,
            "orderType": ot,
        });
        if let Some(v) = price {
            body["price"] = json!(v);
        }
        if let Some(v) = market {
            body["market"] = json!(v);
            body["market_type"] = json!(v);
        }
        if let Some(v) = leverage {
            body["leverage"] = json!(v);
        }
        if let Some(v) = reduce_only {
            body["reduceOnly"] = json!(v);
        }
        if let Some(v) = sl_price {
            body["slPrice"] = json!(v);
        }
        if let Some(v) = tp_price {
            body["tpPrice"] = json!(v);
        }
        if let Some(v) = client_order_id {
            body["client_order_id"] = json!(v);
            body["clientOrderId"] = json!(v);
        }
        self.http.post("/api/v1/private/sim/create-order", &body).await
    }

    /// Cancel a resting paper order.
    pub async fn cancel_order(
        &self,
        strategy_id: &str,
        order_id: &str,
        symbol: Option<&str>,
        exchange: Option<&str>,
    ) -> Result<Value> {
        let mut body = json!({
            "strategy_id": strategy_id,
            "order_id": order_id,
            "orderId": order_id,
        });
        if let Some(v) = symbol {
            body["symbol"] = json!(v);
        }
        if let Some(v) = exchange {
            body["exchange"] = json!(v);
        }
        self.http.post("/api/v1/private/sim/cancel-order", &body).await
    }
}
