// Package melaya — domain namespace groupings.
//
// Three top-level namespace structs expose the SDK surface as a clear
// three-plane taxonomy:
//
//	m.Trading.Market.Ticker(…)
//	m.Agents.Pipelines.List(…)
//	m.Platform.Projects.List(…)
//
// The flat accessors on Client (m.Market, m.Pipelines, …) remain available
// and are equivalent — they are not deprecated, just secondary in docs.
package melaya

// ── Namespace mapping ────────────────────────────────────────────────────────
//
// TRADING
//   Market    → MarketAPI      (tickers, orderbook, OHLCV, MDD, liquidations)
//   Account   → AccountAPI     (connected exchange keys, tier limits, usage)
//   Sim       → SimAPI         (paper-trading / sim broker)
//   Strategies→ StrategiesAPI  (launch/control/inspect strategies)
//   Trade     → TradeAPI       (live credentialed trading, real funds)
//   Backtest  → BacktestAPI    (historical backtests + param sweeps)
//   Stream    → StreamAPI      (WebSocket market-data + private feeds)
//
// AGENTS
//   Pipelines → PipelinesAPI   (run overview, traces, cron schedules)
//   Hitl      → HitlAPI        (human-in-the-loop approval queue)
//   Assistant → AssistantAPI   (onboarding profile)
//   Phone     → PhoneAPI       (phone device control)
//   Evals     → EvalsAPI       (eval run listing + comparison)
//
// PLATFORM
//   Projects    → ProjectsAPI    (create / list agent projects)
//   Credentials → CredentialsAPI (user-scoped credential storage, RAG, OAuth)
//   Connectors  → ConnectorsAPI  (project-scoped connector credentials)
//   Billing     → BillingAPI     (subscription, Stripe checkout / portal)
//   Team        → TeamAPI        (project team management)
//   Templates   → TemplatesAPI   (pipeline template management)
//   Overview    → OverviewAPI    (dashboard overview, model pricing)
//   Runner      → RunnerAPI      (mel_run_ token management)
//   Auth        → AuthAPI        (login, MFA, JWT rotation, credits)
//   Accounts    → AuthAPI        (alias of Auth — platform account / keys / credits)
//   Bugs        → BugsAPI        (user bug-report feedback surface)
//   Events      → EventsClient   (Socket.IO real-time platform events)

// TradingNamespace groups every module that concerns market-data, exchange
// connectivity, and live/paper/backtest trading.
//
// Access via Client.Trading:
//
//	m.Trading.Market.Ticker(ctx, melaya.SymbolQuery{Exchange: "binance", Symbol: "BTC/USDT"})
//	m.Trading.Sim.Balance(ctx)
//	m.Trading.Stream.Orderbook(ctx, …)
type TradingNamespace struct {
	// Market provides normalized market-data endpoints (tickers, orderbook,
	// OHLCV, MDD screener, price history, on-chain yields/liquidity, liquidations).
	Market *MarketAPI
	// Account provides authenticated reads of connected exchange keys,
	// tier limits, and API-usage counters.
	Account *AccountAPI
	// Sim provides the paper-trading (sim broker) API: virtual balance,
	// positions, and orders on any connected venue.
	Sim *SimAPI
	// Strategies provides launch, control, and inspection of trading strategies
	// (both paper and live).
	Strategies *StrategiesAPI
	// Trade provides live credentialed trading on a connected exchange (real
	// funds). Always double-check parameters before calling.
	Trade *TradeAPI
	// Backtest provides historical backtests and parameter-sweep optimisation
	// on the Rust engine.
	Backtest *BacktestAPI
	// Stream provides WebSocket streaming endpoints: public market data
	// (orderbook, trades, ticker) and private feeds (orders, positions).
	Stream *StreamAPI
}

// AgentsNamespace groups every module that manages agentic pipeline execution,
// human oversight, and AI/assistant tooling.
//
// Access via Client.Agents:
//
//	runs, _ := m.Agents.Pipelines.List(ctx, nil)
//	pending, _ := m.Agents.Hitl.Pending(ctx)
//	m.Agents.Evals.Summary(ctx)
type AgentsNamespace struct {
	// Pipelines provides pipeline-run overview, trace inspection, and cron
	// schedule management.
	Pipelines *PipelinesAPI
	// Hitl provides the Human-in-the-Loop approval queue: list pending
	// requests, approve, reject, and bulk-decide.
	Hitl *HitlAPI
	// Assistant provides the assistant onboarding profile (get + set).
	Assistant *AssistantAPI
	// Phone provides phone-device control: pairing, screen-tree, app listing,
	// and run registration.
	Phone *PhoneAPI
	// Evals provides eval-run listing, detail, comparison, and memory-graph
	// inspection.
	Evals *EvalsAPI
}

// PlatformNamespace groups every module that concerns the Melaya platform
// itself — identity, projects, billing, infrastructure tokens, and observability.
//
// Access via Client.Platform:
//
//	projects, _ := m.Platform.Projects.List(ctx)
//	m.Platform.Credentials.Set(ctx, "openai", melaya.CredentialSetBody{Value: "sk-…"})
//	m.Platform.Auth.Me(ctx)
type PlatformNamespace struct {
	// Projects provides creation and listing of agent projects.
	Projects *ProjectsAPI
	// Credentials provides user-scoped credential storage, operator profiles,
	// RAG ingest/retrieve, and OAuth connection flows.
	Credentials *CredentialsAPI
	// Connectors provides project-scoped connector credentials (per-project
	// service keys and env-handle management).
	Connectors *ConnectorsAPI
	// Billing provides subscription status, Stripe checkout, and portal links.
	Billing *BillingAPI
	// Team provides project team management: members, roles, and invite links.
	Team *TeamAPI
	// Templates provides pipeline template management: create, share, assign,
	// duplicate, delete.
	Templates *TemplatesAPI
	// Overview provides dashboard overview data and model pricing tables.
	Overview *OverviewAPI
	// Runner provides runner-token management: mint, list, and revoke
	// mel_run_ tokens used by self-hosted runner processes.
	Runner *RunnerAPI
	// Auth provides authentication and MFA endpoints: login, MFA setup/verify,
	// JWT rotation, credits, and account management.
	Auth *AuthAPI
	// Accounts is an alias for Auth — exposes the same platform-account,
	// API-key, and credit-management surface under a discoverable name.
	Accounts *AuthAPI
	// Bugs provides the user bug-report and notification surface.
	Bugs *BugsAPI
	// Events is a Socket.IO client for real-time platform events: run updates,
	// init-phase progress, HITL notifications, and pipeline CRUD events.
	Events *EventsClient
}
