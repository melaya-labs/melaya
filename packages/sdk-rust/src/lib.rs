//! # Melaya Rust SDK
//!
//! Official Rust client for the [Melaya](https://melaya.org) unified market-data,
//! strategies, backtests, and streaming API.
//!
//! ## Quick start
//!
//! ```no_run
//! use melaya::Melaya;
//!
//! #[tokio::main]
//! async fn main() {
//!     let key = std::env::var("MK").expect("MK env var not set");
//!     let m = Melaya::new(&key).unwrap();
//!
//!     let t = m.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
//!     println!("BTC last: {}", t["last"]);
//! }
//! ```

pub mod error;

mod client;
mod market;
mod account;
mod sim;
mod strategies;
mod backtest;
mod trade;
mod stream;

pub use client::{DEFAULT_BASE_URL, DEFAULT_WS_URL};
pub use error::{MelayaError, Result};
pub use market::MarketAPI;
pub use account::AccountAPI;
pub use sim::SimAPI;
pub use trade::TradeAPI;
pub use strategies::StrategiesAPI;
pub use backtest::BacktestAPI;
pub use stream::{MelayaStream, StreamAPI};

/// Configuration options for the Melaya client.
pub struct MelayaOptions {
    /// Your Melaya API key (prefixed `mk_`).
    pub api_key: String,
    /// Override the REST base URL (default: `https://api.melaya.org`).
    pub base_url: Option<String>,
    /// Override the WebSocket base URL (default: `wss://wss.melaya.org`).
    pub ws_url: Option<String>,
    /// Disable TLS certificate verification. Controlled by `MELAYA_INSECURE_TLS=1`.
    /// Secure by default; only set this in dev environments with a TLS intercept proxy.
    pub insecure_tls: bool,
}

impl MelayaOptions {
    /// Build options from an API key, reading `MELAYA_INSECURE_TLS` from env.
    pub fn from_key(api_key: &str) -> Result<Self> {
        if api_key.is_empty() {
            return Err(MelayaError::Config(
                "api_key is required (create one at melaya.org → Settings → API Keys)".into(),
            ));
        }
        if !api_key.starts_with("mk_") {
            return Err(MelayaError::Config(
                "API keys must be prefixed `mk_`".into(),
            ));
        }
        let insecure_tls = std::env::var("MELAYA_INSECURE_TLS")
            .map(|v| v == "1")
            .unwrap_or(false);
        Ok(Self {
            api_key: api_key.to_owned(),
            base_url: None,
            ws_url: None,
            insecure_tls,
        })
    }
}

/// The top-level Melaya client.
///
/// Construct with [`Melaya::new`] or [`Melaya::with_options`].
pub struct Melaya {
    /// REST market-data + reference endpoints.
    pub market: MarketAPI,
    /// Authenticated account reads: connected keys, tier limits, usage.
    pub account: AccountAPI,
    /// Paper trading (sim broker): virtual balance, positions, and orders.
    pub sim: SimAPI,
    /// Launch, control, and inspect trading strategies (paper + live).
    pub strategies: StrategiesAPI,
    /// Historical backtests + parameter sweeps on the Rust engine.
    pub backtest: BacktestAPI,
    /// Live credentialed trading on a connected exchange (real funds).
    pub trade: TradeAPI,
    /// WebSocket streaming endpoints (public market data + private feeds).
    pub stream: StreamAPI,
}

impl Melaya {
    /// Create a client from an API key. Reads `MELAYA_INSECURE_TLS` from env.
    pub fn new(api_key: &str) -> Result<Self> {
        let opts = MelayaOptions::from_key(api_key)?;
        Self::with_options(opts)
    }

    /// Create a client with full options control.
    pub fn with_options(opts: MelayaOptions) -> Result<Self> {
        let base_url = opts
            .base_url
            .unwrap_or_else(|| DEFAULT_BASE_URL.to_owned());
        let ws_url = opts
            .ws_url
            .unwrap_or_else(|| DEFAULT_WS_URL.to_owned());

        let http = client::HttpClient::new(
            opts.api_key.clone(),
            base_url,
            opts.insecure_tls,
        )?;

        Ok(Self {
            market: MarketAPI::new(http.clone()),
            account: AccountAPI::new(http.clone()),
            sim: SimAPI::new(http.clone()),
            strategies: StrategiesAPI::new(http.clone()),
            backtest: BacktestAPI::new(http.clone()),
            trade: TradeAPI::new(http.clone()),
            stream: StreamAPI::new(opts.api_key, ws_url, opts.insecure_tls, http),
        })
    }
}
