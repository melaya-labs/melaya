use std::collections::HashMap;

use serde_json::Value;

use crate::client::HttpClient;
use crate::error::Result;

/// REST market-data API — normalized across all 70+ venues.
pub struct MarketAPI {
    http: HttpClient,
}

impl MarketAPI {
    pub(crate) fn new(http: HttpClient) -> Self {
        Self { http }
    }

    /// List the exchanges Melaya supports right now.
    pub async fn list_exchanges(&self) -> Result<Value> {
        let q = HashMap::new();
        let r = self.http.get("/api/v1/market/list-exchanges", &q).await?;
        Ok(r["exchanges"].clone())
    }

    /// Best bid/ask, last price, and 24h aggregates for one symbol.
    pub async fn ticker(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        q.insert("symbol", Some(symbol.to_owned()));
        q.insert("market", market.map(str::to_owned));
        let r = self.http.get("/api/v1/market/ticker", &q).await?;
        Ok(r["ticker"].clone())
    }

    /// Order book to a given depth.
    pub async fn orderbook(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
        limit: Option<u32>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        q.insert("symbol", Some(symbol.to_owned()));
        q.insert("market", market.map(str::to_owned));
        q.insert("limit", limit.map(|v| v.to_string()));
        let r = self.http.get("/api/v1/market/orderbook", &q).await?;
        Ok(r["orderbook"].clone())
    }

    /// OHLCV candles for a timeframe.
    pub async fn ohlcv(
        &self,
        exchange: &str,
        symbol: &str,
        timeframe: &str,
        market: Option<&str>,
        limit: Option<u32>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        q.insert("symbol", Some(symbol.to_owned()));
        q.insert("timeframe", Some(timeframe.to_owned()));
        q.insert("market", market.map(str::to_owned));
        q.insert("limit", limit.map(|v| v.to_string()));
        let r = self.http.get("/api/v1/market/ohlcv", &q).await?;
        Ok(r["candles"].clone())
    }

    /// Recent public trades.
    pub async fn trades(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
    ) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        q.insert("symbol", Some(symbol.to_owned()));
        q.insert("market", market.map(str::to_owned));
        let r = self.http.get("/api/v1/market/trades", &q).await?;
        Ok(r["trades"].clone())
    }

    /// Tradable markets on a venue.
    pub async fn markets(&self, exchange: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        let r = self.http.get("/api/v1/market/markets", &q).await?;
        Ok(r["markets"].clone())
    }

    /// Listed currencies on a venue.
    pub async fn currencies(&self, exchange: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        let r = self.http.get("/api/v1/market/currencies", &q).await?;
        Ok(r["currencies"].clone())
    }

    /// Operational status of a venue.
    pub async fn status(&self, exchange: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        let r = self.http.get("/api/v1/market/status", &q).await?;
        Ok(r["status"].clone())
    }

    /// Exchange server time.
    pub async fn time(&self, exchange: &str) -> Result<Value> {
        let mut q: HashMap<&str, Option<String>> = HashMap::new();
        q.insert("exchange", Some(exchange.to_owned()));
        let r = self.http.get("/api/v1/market/time", &q).await?;
        Ok(r["time"].clone())
    }

    // ── Batch / derivatives (POST) ──────────────────────────────────────────

    /// Tickers for many symbols on one venue in a single call.
    pub async fn tickers(
        &self,
        exchange: &str,
        symbols: &[&str],
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbols": symbols });
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/tickers", &body).await?;
        Ok(r["tickers"].clone())
    }

    /// Latest funding rates for perpetuals.
    pub async fn funding_rates(
        &self,
        exchange: &str,
        symbols: &[&str],
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbols": symbols });
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/funding-rates", &body).await?;
        Ok(r["rates"].clone())
    }

    /// Funding-rate history.
    pub async fn funding_rate_history(
        &self,
        exchange: &str,
        symbol: &str,
        hours: Option<u32>,
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbol": symbol });
        if let Some(h) = hours {
            body["hours"] = serde_json::json!(h);
        }
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/funding-rate-history", &body).await?;
        Ok(r["history"].clone())
    }

    /// Open interest for one or more perpetuals.
    pub async fn open_interest(
        &self,
        exchange: &str,
        symbols: &[&str],
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbols": symbols });
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/open-interest", &body).await?;
        Ok(r["openInterest"].clone())
    }

    /// Open-interest history.
    pub async fn open_interest_history(
        &self,
        exchange: &str,
        symbol: &str,
        hours: Option<u32>,
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbol": symbol });
        if let Some(h) = hours {
            body["hours"] = serde_json::json!(h);
        }
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/open-interest-history", &body).await?;
        Ok(r["history"].clone())
    }

    /// Instrument list + trading constraints.
    pub async fn instruments(&self, exchange: &str, market: Option<&str>) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange });
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        self.http.post("/api/v1/market/instruments", &body).await
    }

    /// Cross-exchange liquidation events (historical query).
    pub async fn liquidation_events(
        &self,
        exchange: Option<&str>,
        symbol: Option<&str>,
        since_ms: Option<u64>,
        limit: Option<u32>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({});
        if let Some(v) = exchange {
            body["exchange"] = Value::String(v.to_owned());
        }
        if let Some(v) = symbol {
            body["symbol"] = Value::String(v.to_owned());
        }
        if let Some(v) = since_ms {
            body["sinceMs"] = serde_json::json!(v);
        }
        if let Some(v) = limit {
            body["limit"] = serde_json::json!(v);
        }
        let r = self.http.post("/api/v1/market/liquidation-events", &body).await?;
        Ok(r["events"].clone())
    }

    /// Multi-symbol OHLCV in one call.
    pub async fn ohlcv_multi(
        &self,
        exchange: &str,
        symbols: &[&str],
        timeframe: &str,
        limit: Option<u32>,
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbols": symbols, "timeframe": timeframe });
        if let Some(v) = limit {
            body["limit"] = serde_json::json!(v);
        }
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/ohlcv-multi", &body).await?;
        Ok(r["perSymbol"].clone())
    }

    /// Trading constraints for one symbol.
    pub async fn market_constraints(
        &self,
        exchange: &str,
        symbol: &str,
        market: Option<&str>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchange": exchange, "symbol": symbol });
        if let Some(m) = market {
            body["market"] = Value::String(m.to_owned());
        }
        let r = self.http.post("/api/v1/market/market-constraints", &body).await?;
        Ok(r["constraints"].clone())
    }

    /// Funding-rate history for one symbol across several venues.
    pub async fn funding_rate_history_multi(
        &self,
        exchanges: &[&str],
        symbol: &str,
        hours: Option<u32>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchanges": exchanges, "symbol": symbol });
        if let Some(h) = hours {
            body["hours"] = serde_json::json!(h);
        }
        let r = self.http.post("/api/v1/market/funding-rate-history-multi", &body).await?;
        Ok(r["perExchange"].clone())
    }

    /// Open-interest history for one symbol across several venues.
    pub async fn open_interest_history_multi(
        &self,
        exchanges: &[&str],
        symbol: &str,
        hours: Option<u32>,
    ) -> Result<Value> {
        let mut body = serde_json::json!({ "exchanges": exchanges, "symbol": symbol });
        if let Some(h) = hours {
            body["hours"] = serde_json::json!(h);
        }
        let r = self.http.post("/api/v1/market/open-interest-history-multi", &body).await?;
        Ok(r["perExchange"].clone())
    }

    /// Prediction-market listings for a venue.
    pub async fn prediction_markets(&self, venue: Option<&str>) -> Result<Value> {
        let body = serde_json::json!({ "venue": venue.unwrap_or("polymarket") });
        let r = self.http.post("/api/v1/market/pm-markets", &body).await?;
        Ok(r["markets"].clone())
    }

    /// Live platform catalog counts (agentic tools, subagents, by category). Public.
    pub async fn catalog_counts(&self) -> Result<Value> {
        let q = HashMap::new();
        self.http.get("/api/v1/public/catalog-counts", &q).await
    }
}
