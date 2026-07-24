//! Domain-grouped namespace accessors for the Melaya SDK.
//!
//! These three structs expose the same API objects that live on the flat
//! [`Melaya`](crate::Melaya) client, but grouped into crystal-clear domains:
//!
//! | Accessor | Modules |
//! |---|---|
//! | `melaya.trading` | `market`, `account`, `sim`, `strategies`, `trade`, `backtest`, `stream` |
//! | `melaya.agents`  | `pipelines`, `hitl`, `assistant`, `phone`, `evals` |
//! | `melaya.platform`| `projects`, `credentials`, `connectors`, `billing`, `team`, `templates`, `runner`, `auth`, `mfa`, `accounts`, `bugs`, `events` |
//!
//! The underlying API structs are `Clone`, so the namespace fields hold cheap
//! clones of the same `HttpClient` (one `Arc<reqwest::Client>` under the hood).
//! No extra heap cost.
//!
//! # Example
//!
//! ```no_run
//! use melaya::Melaya;
//!
//! #[tokio::main]
//! async fn main() {
//!     let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
//!
//!     // Namespaced access (primary API)
//!     let ticker = m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
//!     let projects = m.agents.pipelines.list(None, None, None, None, None, None).await.unwrap();
//!     let subs = m.platform.billing.subscription().await.unwrap();
//!
//!     // Flat access still works (backward compat)
//!     let ticker2 = m.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
//!     assert_eq!(ticker, ticker2);
//! }
//! ```

use crate::{
    AccountAPI, AccountsPlatformAPI, AssistantAPI, AuthAPI, BacktestAPI, BillingAPI, BugsAPI,
    ConnectorsAPI, CredentialsAPI, EvalsAPI, HitlAPI, MarketAPI, MelayaEvents, MfaAPI, PhoneAPI,
    PipelinesAPI, ProjectsAPI, RunnerAPI, SimAPI, StrategiesAPI, StreamAPI, TeamAPI, TemplatesAPI,
    TradeAPI,
};

// ── Trading namespace ─────────────────────────────────────────────────────────

/// All trading-plane modules grouped under a single accessor.
///
/// Access via `melaya.trading.<module>`:
///
/// ```no_run
/// # use melaya::Melaya;
/// # #[tokio::main] async fn main() {
/// # let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
/// // Market data
/// m.trading.market.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
/// // Paper trading
/// m.trading.sim.balance("strat-id", None).await.unwrap();
/// // Live order
/// // m.trading.trade.create_order(...).await?;
/// // Backtest
/// m.trading.backtest.list(Some(10), None).await.unwrap();
/// // WebSocket stream
/// let mut feed = m.trading.stream.ticker("binance", "BTC/USDT", Some("spot")).await.unwrap();
/// # }
/// ```
pub struct TradingNamespace {
    /// REST market-data + reference endpoints (public plane).
    pub market: MarketAPI,
    /// Authenticated CEX account reads: connected keys, tier limits, usage.
    pub account: AccountAPI,
    /// Paper trading (sim broker): virtual balance, positions, and orders.
    pub sim: SimAPI,
    /// Launch, control, and inspect trading strategies (paper + live).
    pub strategies: StrategiesAPI,
    /// Live credentialed trading on a connected exchange (real funds).
    pub trade: TradeAPI,
    /// Historical backtests + parameter sweeps on the Rust engine.
    pub backtest: BacktestAPI,
    /// WebSocket streaming endpoints (public market data + private feeds).
    pub stream: StreamAPI,
}

// ── Agents namespace ──────────────────────────────────────────────────────────

/// All agent-plane modules grouped under a single accessor.
///
/// Access via `melaya.agents.<module>`:
///
/// ```no_run
/// # use melaya::Melaya;
/// # #[tokio::main] async fn main() {
/// # let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
/// // Pipeline run list
/// m.agents.pipelines.list(None, None, None, None, None, None).await.unwrap();
/// // HITL approval queue
/// m.agents.hitl.pending().await.unwrap();
/// // Assistant profile
/// m.agents.assistant.get_profile().await.unwrap();
/// // Phone device control
/// m.agents.phone.list_devices().await.unwrap();
/// // Eval results
/// m.agents.evals.summary().await.unwrap();
/// # }
/// ```
pub struct AgentsNamespace {
    /// Pipeline run overview, traces, and cron schedules.
    pub pipelines: PipelinesAPI,
    /// Human-in-the-loop approval queue: list pending, approve, reject.
    pub hitl: HitlAPI,
    /// Assistant onboarding profile (get + set).
    pub assistant: AssistantAPI,
    /// Phone device control: pair, list, screen-tree, apps.
    pub phone: PhoneAPI,
    /// Eval run results and memory graphs.
    pub evals: EvalsAPI,
}

// ── Platform namespace ────────────────────────────────────────────────────────

/// All platform-plane modules grouped under a single accessor.
///
/// Access via `melaya.platform.<module>`:
///
/// ```no_run
/// # use melaya::Melaya;
/// # #[tokio::main] async fn main() {
/// # let m = Melaya::new(&std::env::var("MK").unwrap()).unwrap();
/// // Project management
/// m.platform.projects.list().await.unwrap();
/// // Credential store
/// m.platform.credentials.list().await.unwrap();
/// // Connector credentials
/// m.platform.connectors.env_handle("my-project").await.unwrap();
/// // Billing
/// m.platform.billing.subscription().await.unwrap();
/// // Team
/// m.platform.team.list_members("my-project").await.unwrap();
/// // Templates
/// m.platform.templates.list().await.unwrap();
/// // Runner tokens
/// m.platform.runner.list_tokens().await.unwrap();
/// // Auth + MFA
/// m.platform.auth.me().await.unwrap();
/// m.platform.mfa.status().await.unwrap();
/// // Platform account (credits, keys, GDPR)
/// m.platform.accounts.credits().await.unwrap();
/// // Bug reports
/// m.platform.bugs.list_mine().await.unwrap();
/// // Real-time Socket.IO events
/// let mut ev = m.platform.events.subscribe_hitl();
/// # }
/// ```
pub struct PlatformNamespace {
    /// Create and list agent projects.
    pub projects: ProjectsAPI,
    /// User-scoped credential storage (services, OAuth, env handles).
    pub credentials: CredentialsAPI,
    /// Project-scoped connector credentials (per-project service keys).
    pub connectors: ConnectorsAPI,
    /// Billing: subscription status, Stripe checkout / portal, pricing plans.
    pub billing: BillingAPI,
    /// Project team management: members, roles, and invite links.
    pub team: TeamAPI,
    /// Pipeline templates: create, share, assign, and manage visibility.
    pub templates: TemplatesAPI,
    /// Runner token management: mint, list, revoke `mel_run_` tokens.
    pub runner: RunnerAPI,
    /// Auth: login, register, MFA, session management.
    pub auth: AuthAPI,
    /// MFA: TOTP enrollment and verification.
    pub mfa: MfaAPI,
    /// Platform account management: profile, credits, keys, GDPR export.
    pub accounts: AccountsPlatformAPI,
    /// Bug reports: create, comment, notifications.
    pub bugs: BugsAPI,
    /// Platform real-time events over Socket.IO at `/api/v1/events`.
    pub events: MelayaEvents,
}
