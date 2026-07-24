//! # Melaya Rust SDK v0.2.0
//!
//! Official Rust client for the [Melaya](https://melaya.org) platform:
//! market data, trading, strategies, backtests, WebSocket streaming,
//! agent projects, HITL approvals, credentials, billing, phone control,
//! templates, evals, and real-time Socket.IO events.
//!
//! ## Domain namespaces (primary API)
//!
//! Three top-level namespaces group every module by domain:
//!
//! | Namespace | Modules |
//! |---|---|
//! | `melaya.trading` | `market`, `account`, `sim`, `strategies`, `trade`, `backtest`, `stream` |
//! | `melaya.agents`  | `pipelines`, `hitl`, `assistant`, `phone`, `evals` |
//! | `melaya.platform`| `projects`, `credentials`, `connectors`, `billing`, `team`, `templates`, `runner`, `auth`, `mfa`, `accounts`, `bugs`, `events` |
//!
//! Flat accessors (`melaya.market`, `melaya.pipelines`, …) remain available for
//! backward compatibility.
//!
//! ## Quick start
//!
//! ```no_run
//! use melaya::Melaya;
//!
//! #[tokio::main]
//! async fn main() {
//!     let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
//!
//!     // ── Namespaced access (recommended) ───────────────────────────────────
//!     let ticker = m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
//!     println!("BTC last: {}", ticker["last"]);
//!
//!     let projects = m.platform.projects.list().await.unwrap();
//!     println!("Projects: {projects}");
//!
//!     let pending = m.agents.hitl.pending().await.unwrap();
//!     println!("Pending approvals: {pending}");
//!
//!     let mut ev = m.platform.events.subscribe_run("run-abc");
//!     while let Some(frame) = ev.recv().await {
//!         println!("run event: {frame}");
//!     }
//!
//!     // ── Flat access (backward compat) ─────────────────────────────────────
//!     let ticker2 = m.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
//!     assert_eq!(ticker, ticker2);
//! }
//! ```

pub mod error;

mod client;

// ── Trading plane ─────────────────────────────────────────────────────────────
mod account;
mod backtest;
mod market;
mod sim;
mod strategies;
mod stream;
mod trade;

// ── Platform / agents plane ───────────────────────────────────────────────────
mod accounts_platform;
mod assistant;
mod auth;
mod billing;
mod bugs;
mod connectors;
mod credentials;
mod evals;
mod events;
mod hitl;
mod phone;
mod pipelines;
mod projects;
mod runner;
mod team;
mod templates;

// ── Domain namespaces ─────────────────────────────────────────────────────────
pub mod namespaces;
pub use namespaces::{AgentsNamespace, PlatformNamespace, TradingNamespace};

// ── Public re-exports ─────────────────────────────────────────────────────────
pub use client::{DEFAULT_BASE_URL, DEFAULT_WS_URL};
pub use error::{MelayaError, Result};

// Trading plane
pub use account::AccountAPI;
pub use backtest::BacktestAPI;
pub use market::MarketAPI;
pub use sim::SimAPI;
pub use strategies::StrategiesAPI;
pub use stream::{MelayaStream, StreamAPI};
pub use trade::TradeAPI;

// Platform / agents plane
pub use accounts_platform::AccountsPlatformAPI;
pub use assistant::AssistantAPI;
pub use auth::{AuthAPI, MfaAPI};
pub use billing::BillingAPI;
pub use bugs::BugsAPI;
pub use connectors::ConnectorsAPI;
pub use credentials::CredentialsAPI;
pub use evals::EvalsAPI;
pub use events::{EventSubscription, MelayaEvents};
pub use hitl::HitlAPI;
pub use phone::PhoneAPI;
pub use pipelines::{
    DeleteTracesResult, PipelineRunAccepted, PipelineRunOptions, PipelineRunStatus, PipelinesAPI,
    TraceSummary, TracesPage,
};
pub use projects::ProjectsAPI;
pub use runner::RunnerAPI;
pub use team::TeamAPI;
pub use templates::TemplatesAPI;

// ── MelayaOptions ────────────────────────────────────────────────────────────

/// Configuration options for the Melaya client.
pub struct MelayaOptions {
    /// Your Melaya API key (prefixed `mk_`). Create one at melaya.org → Settings → API Keys.
    pub api_key: String,
    /// Override the REST base URL (default: `https://api.melaya.org`).
    pub base_url: Option<String>,
    /// Override the WebSocket base URL (default: `wss://wss.melaya.org`).
    pub ws_url: Option<String>,
}

impl MelayaOptions {
    /// Build options from an API key.
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
        Ok(Self {
            api_key: api_key.to_owned(),
            base_url: None,
            ws_url: None,
        })
    }
}

// ── Melaya ───────────────────────────────────────────────────────────────────

/// The top-level Melaya client.
///
/// Construct with [`Melaya::new`] or [`Melaya::with_options`].
///
/// ## Domain namespaces (primary API)
///
/// Three namespace structs group every module by domain so the API surface is
/// immediately discoverable:
///
/// ```no_run
/// # use melaya::Melaya;
/// # #[tokio::main] async fn main() {
/// # let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
/// // Trading — market data, sim, strategies, live trade, backtest, WS streams
/// m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
/// m.trading.backtest.list(Some(10), None).await.unwrap();
///
/// // Agents — pipeline runs, HITL, assistant, phone, evals
/// m.agents.pipelines.list(None, None, None, None, None, None).await.unwrap();
/// m.agents.hitl.pending().await.unwrap();
///
/// // Platform — projects, credentials, billing, team, templates, auth, …
/// m.platform.projects.list().await.unwrap();
/// m.platform.billing.subscription().await.unwrap();
/// # }
/// ```
///
/// All pre-existing flat fields (`m.market`, `m.pipelines`, …) remain
/// unchanged for backward compatibility.
///
/// # Security
/// REST requests send the API key only as `Authorization: Bearer` — never in
/// a query string. Public WebSocket streams authenticate with `?apiKey=` in
/// the `wss://` URL (server protocol); private WebSocket streams use a
/// short-lived `?wsTicket=` minted over REST instead of the API key.
/// Credentials are never logged. TLS is enforced by default.
pub struct Melaya {
    // ── Domain namespaces (primary API) ──────────────────────────────────────
    /// Trading-plane namespace: `market`, `account`, `sim`, `strategies`,
    /// `trade`, `backtest`, `stream`.
    pub trading: TradingNamespace,

    /// Agents-plane namespace: `pipelines`, `hitl`, `assistant`, `phone`,
    /// `evals`.
    pub agents: AgentsNamespace,

    /// Platform-plane namespace: `projects`, `credentials`, `connectors`,
    /// `billing`, `team`, `templates`, `runner`, `auth`, `mfa`, `accounts`,
    /// `bugs`, `events`.
    pub platform: PlatformNamespace,

    // ── Flat accessors (backward compat) ─────────────────────────────────────
    /// REST market-data + reference endpoints (public plane).
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

    /// Auth: login, register, MFA, session management.
    pub auth: AuthAPI,
    /// MFA: TOTP enrollment and verification.
    pub mfa: MfaAPI,
    /// Platform account management: profile, credits, keys, GDPR export.
    pub accounts: AccountsPlatformAPI,
    /// Billing: subscription status, Stripe checkout / portal, pricing plans.
    pub billing: BillingAPI,
    /// Runner token management: mint, list, revoke `mel_run_` tokens.
    pub runner: RunnerAPI,
    /// Create and list agent projects.
    pub projects: ProjectsAPI,
    /// Pipeline run overview, traces, and cron schedules.
    pub pipelines: PipelinesAPI,
    /// Human-in-the-loop approval queue: list pending, approve, reject.
    pub hitl: HitlAPI,
    /// User-scoped credential storage (services, OAuth, env handles).
    pub credentials: CredentialsAPI,
    /// Project-scoped connector credentials (per-project service keys).
    pub connectors: ConnectorsAPI,
    /// Phone device control: pair, list, screen-tree, apps.
    pub phone: PhoneAPI,
    /// Project team management: members, roles, and invite links.
    pub team: TeamAPI,
    /// Pipeline templates: create, share, assign, and manage visibility.
    pub templates: TemplatesAPI,
    /// Assistant onboarding profile (get + set).
    pub assistant: AssistantAPI,
    /// Eval run results and memory graphs.
    pub evals: EvalsAPI,
    /// Bug reports: create, comment, notifications.
    pub bugs: BugsAPI,
    /// Platform real-time events over Socket.IO at `/api/v1/events`.
    /// Subscribe to run updates, init-phase progress, HITL notifications,
    /// and pipeline CRUD events.
    pub events: MelayaEvents,
}

impl Melaya {
    /// Create a client from an API key.
    ///
    /// # Panics / Errors
    /// Returns an error if the key is empty or does not start with `mk_`.
    pub fn new(api_key: &str) -> Result<Self> {
        let opts = MelayaOptions::from_key(api_key)?;
        Self::with_options(opts)
    }

    /// Create a client with full options control.
    pub fn with_options(opts: MelayaOptions) -> Result<Self> {
        let base_url = opts.base_url.unwrap_or_else(|| DEFAULT_BASE_URL.to_owned());
        let ws_url = opts.ws_url.unwrap_or_else(|| DEFAULT_WS_URL.to_owned());

        let http = client::HttpClient::new(opts.api_key.clone(), base_url.clone())?;

        // ── Build every API once (flat fields) ────────────────────────────────
        let market = MarketAPI::new(http.clone());
        let account = AccountAPI::new(http.clone());
        let sim = SimAPI::new(http.clone());
        let strategies = StrategiesAPI::new(http.clone());
        let backtest = BacktestAPI::new(http.clone());
        let trade = TradeAPI::new(http.clone());
        let stream = StreamAPI::new(opts.api_key.clone(), ws_url, http.clone());
        let auth = AuthAPI::new(http.clone());
        let mfa = MfaAPI::new(http.clone());
        let accounts = AccountsPlatformAPI::new(http.clone());
        let billing = BillingAPI::new(http.clone());
        let runner = RunnerAPI::new(http.clone());
        let projects = ProjectsAPI::new(http.clone());
        let pipelines = PipelinesAPI::new(http.clone());
        let hitl = HitlAPI::new(http.clone());
        let credentials = CredentialsAPI::new(http.clone());
        let connectors = ConnectorsAPI::new(http.clone());
        let phone = PhoneAPI::new(http.clone());
        let team = TeamAPI::new(http.clone());
        let templates = TemplatesAPI::new(http.clone());
        let assistant = AssistantAPI::new(http.clone());
        let evals = EvalsAPI::new(http.clone());
        let bugs = BugsAPI::new(http.clone());
        let events = MelayaEvents::new(opts.api_key.clone(), base_url);

        // ── Wire domain namespaces (cheap Clone — reqwest::Client is Arc) ─────
        let trading = TradingNamespace {
            market: market.clone(),
            account: account.clone(),
            sim: sim.clone(),
            strategies: strategies.clone(),
            trade: trade.clone(),
            backtest: backtest.clone(),
            stream: stream.clone(),
        };
        let agents = AgentsNamespace {
            pipelines: pipelines.clone(),
            hitl: hitl.clone(),
            assistant: assistant.clone(),
            phone: phone.clone(),
            evals: evals.clone(),
        };
        let platform = PlatformNamespace {
            projects: projects.clone(),
            credentials: credentials.clone(),
            connectors: connectors.clone(),
            billing: billing.clone(),
            team: team.clone(),
            templates: templates.clone(),
            runner: runner.clone(),
            auth: auth.clone(),
            mfa: mfa.clone(),
            accounts: accounts.clone(),
            bugs: bugs.clone(),
            events: events.clone(),
        };

        Ok(Self {
            // Domain namespaces
            trading,
            agents,
            platform,
            // Flat accessors (backward compat)
            market,
            account,
            sim,
            strategies,
            backtest,
            trade,
            stream,
            auth,
            mfa,
            accounts,
            billing,
            runner,
            projects,
            pipelines,
            hitl,
            credentials,
            connectors,
            phone,
            team,
            templates,
            assistant,
            evals,
            bugs,
            events,
        })
    }
}
