# frozen_string_literal: true

require_relative "melaya/version"
require_relative "melaya/errors"
require_relative "melaya/http_client"

# ── Trading plane ──────────────────────────────────────────────────────────────
require_relative "melaya/market"
require_relative "melaya/account"
require_relative "melaya/sim"
require_relative "melaya/strategies"
require_relative "melaya/backtest"
require_relative "melaya/stream"
require_relative "melaya/trade"

# ── Platform / agents plane ────────────────────────────────────────────────────
require_relative "melaya/auth"
require_relative "melaya/accounts"
require_relative "melaya/billing"
require_relative "melaya/runner"
require_relative "melaya/projects"
require_relative "melaya/pipelines"
require_relative "melaya/hitl"
require_relative "melaya/credentials"
require_relative "melaya/connectors"
require_relative "melaya/phone"
require_relative "melaya/team"
require_relative "melaya/templates"
require_relative "melaya/assistant"
require_relative "melaya/evals"
require_relative "melaya/bugs"
require_relative "melaya/events"

# ── Domain namespace groupings ─────────────────────────────────────────────────
require_relative "melaya/namespaces"

module Melaya
  # The unified Melaya client.
  #
  # Exposes every public endpoint in the Melaya REST API surface through three
  # domain namespaces **and** flat module accessors (for backward compatibility).
  #
  # **Primary API — namespaced** (recommended):
  #
  #   melaya.trading.market.ticker(...)         # market data
  #   melaya.trading.strategies.create(...)     # trading strategies
  #   melaya.agents.pipelines.list(...)         # agent pipeline runs
  #   melaya.agents.hitl.pending                # HITL approval queue
  #   melaya.platform.projects.list             # platform projects
  #   melaya.platform.billing.subscription      # billing
  #
  # **Flat accessors** (backward-compatible aliases):
  #
  #   melaya.market.ticker(...)    # same object as melaya.trading.market
  #   melaya.pipelines.list(...)   # same object as melaya.agents.pipelines
  #   melaya.projects.list         # same object as melaya.platform.projects
  #
  # **Namespace groupings**:
  # - +trading+  — market, account, sim, strategies, backtest, stream, trade
  # - +agents+   — pipelines (also +.runs+), hitl, assistant, phone, evals, models
  # - +platform+ — projects, credentials, connectors, billing, team, templates,
  #                overview, runner, auth (also +.mfa+), accounts, bugs, events
  #
  # Authentication: pass your +mk_*+ platform API key via +api_key:+. Every REST call
  # sends it as an Authorization: Bearer header. For session JWT workflows
  # (login/refresh) construct the client with the JWT instead.
  # Never log or expose the key — it is stored opaquely in the HTTP client.
  #
  # @example
  #   require "melaya"
  #
  #   melaya = Melaya::Client.new(api_key: ENV["MELAYA_API_KEY"])
  #
  #   # Namespaced — primary API
  #   t = melaya.trading.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")
  #   puts t["last"]
  #
  #   # Agent pipelines
  #   runs = melaya.agents.pipelines.list(project: "my-project", limit: 10)
  #   # or via the alias:
  #   runs = melaya.agents.runs.list(project: "my-project", limit: 10)
  #
  #   # HITL approvals
  #   pending = melaya.agents.hitl.pending
  #   pending.each { |r| melaya.agents.hitl.approve(r["requestId"]) }
  #
  #   # Platform
  #   projects = melaya.platform.projects.list
  #   melaya.platform.auth.login(username: "you@example.com", password: "s3cr3t")
  #
  #   # Real-time events (Socket.IO) — also on platform namespace
  #   melaya.platform.events.on_run_update("run-123") { |e| puts e["event_type"] }
  class Client
    # ── Trading plane ──────────────────────────────────────────────────────────
    # REST market-data + reference endpoints (public + authenticated).
    attr_reader :market
    # Authenticated account reads: connected keys, tier limits, usage.
    attr_reader :account
    # Paper trading (sim broker): virtual balance, positions, and orders.
    attr_reader :sim
    # Launch, control, and inspect trading strategies (paper + live).
    attr_reader :strategies
    # Historical backtests + parameter sweeps on the Rust engine.
    attr_reader :backtest
    # WebSocket streaming endpoints (public market data + private feeds).
    attr_reader :stream
    # Live trading — credentialed order placement on a connected exchange. WARNING: real funds.
    attr_reader :trade

    # ── Platform / agents plane ────────────────────────────────────────────────
    # Auth: login, MFA, registration, password management, session tokens.
    attr_reader :auth
    # Account management: GDPR export, CEX key removal, profile updates.
    attr_reader :accounts
    # Billing: subscription, Stripe checkout/portal, pricing plans, credit balances.
    attr_reader :billing
    # Runner tokens: mint, list, revoke mel_run_ tokens.
    attr_reader :runner
    # Agent projects: create and list.
    attr_reader :projects
    # Pipeline runs, traces, schedules, and overview dashboard.
    attr_reader :pipelines
    # Human-in-the-loop approval queue: list pending, approve, reject.
    attr_reader :hitl
    # User-scoped credential storage (services, OAuth, env handles, models).
    attr_reader :credentials
    # Project-scoped connector credentials.
    attr_reader :connectors
    # Phone device control: pair, list, screen-tree, apps.
    attr_reader :phone
    # Project team management: members, roles, invite links.
    attr_reader :team
    # Pipeline templates: create, share, assign, and manage visibility.
    attr_reader :templates
    # Assistant onboarding profile (get + set).
    attr_reader :assistant
    # Agent evaluation runs and benchmarks.
    attr_reader :evals
    # Bug reports: submit, track, and comment.
    attr_reader :bugs
    # Platform real-time events over Socket.IO at /api/v1/events.
    # Subscribe to run updates, init-phase progress, HITL notifications,
    # and pipeline CRUD events. Opens a background polling thread.
    attr_reader :events

    # ── Domain namespace accessors (primary API) ───────────────────────────────

    # Trading-plane namespace.
    # Groups: market, account, sim, strategies, backtest, stream, trade.
    #
    # @return [TradingNamespace]
    # @example
    #   melaya.trading.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")
    #   melaya.trading.strategies.create(name: "bot", strategy_type: "custom", ...)
    #   melaya.trading.stream.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot") { |f| ... }
    attr_reader :trading

    # Agent-plane namespace.
    # Groups: pipelines (alias: runs), hitl, assistant, phone, evals, models.
    #
    # @return [AgentsNamespace]
    # @example
    #   melaya.agents.pipelines.list(project: "my-project")
    #   melaya.agents.runs.list(project: "my-project")  # alias for pipelines
    #   melaya.agents.hitl.pending
    #   melaya.agents.assistant.get_profile
    #   melaya.agents.evals.list_runs
    #   melaya.agents.models.list_models(provider: "anthropic")
    attr_reader :agents

    # Platform-plane namespace.
    # Groups: projects, credentials, connectors, billing, team, templates,
    # overview, runner, auth (alias: mfa), accounts, bugs, events.
    #
    # @return [PlatformNamespace]
    # @example
    #   melaya.platform.projects.list
    #   melaya.platform.billing.subscription
    #   melaya.platform.auth.login(username: "u", password: "p")
    #   melaya.platform.mfa.mfa_setup           # alias for auth
    #   melaya.platform.credentials.set("openai", value: "sk-...")
    #   melaya.platform.events.on_run_update("run-123") { |e| puts e["event_type"] }
    attr_reader :platform

    # @param api_key [String] Melaya platform API key, prefixed +mk_+.
    #   Create one at melaya.org → Settings → API Keys. May also be a session
    #   JWT for auth-plane operations (login/refresh return a JWT).
    # @param base_url [String] Override the REST base URL.
    # @param ws_url [String] Override the WebSocket base URL.
    # @param verify_ssl [Boolean] Retained for compatibility; false is rejected.
    # @param connect_events [Boolean] If false, do not open a Socket.IO connection
    #   on construction. Call +events+ to connect lazily. Default: false (lazy).
    def initialize(api_key:, base_url: HttpClient::DEFAULT_BASE_URL,
                   ws_url: StreamAPI::DEFAULT_WS_URL,
                   verify_ssl: nil,
                   connect_events: false)
      raise ArgumentError,
        "Melaya: api_key is required (create one at melaya.org → Settings → API Keys)." \
        if api_key.nil? || api_key.to_s.empty?
      # Allow JWTs (which don't start with mk_) for auth-plane use cases
      # while still guiding developers who forget the prefix.
      if !api_key.to_s.start_with?("mk_") && !api_key.to_s.start_with?("ey")
        raise ArgumentError,
          "Melaya: API keys must be prefixed 'mk_' (or a Bearer JWT starting with 'ey')."
      end

      if verify_ssl == false
        raise ArgumentError, "Melaya: TLS certificate verification cannot be disabled."
      end
      ssl = true

      http = HttpClient.new(api_key: api_key, base_url: base_url, verify_ssl: ssl)

      # Trading plane
      @market     = MarketAPI.new(http)
      @account    = AccountAPI.new(http)
      @sim        = SimAPI.new(http)
      @strategies = StrategiesAPI.new(http)
      @backtest   = BacktestAPI.new(http)
      @stream     = StreamAPI.new(api_key, ws_url, http, verify_ssl: ssl)
      @trade      = TradeAPI.new(http)

      # Platform / agents plane
      @auth        = AuthAPI.new(http)
      @accounts    = AccountsAPI.new(http)
      @billing     = BillingAPI.new(http)
      @runner      = RunnerAPI.new(http)
      @projects    = ProjectsAPI.new(http)
      @pipelines   = PipelinesAPI.new(http)
      @hitl        = HitlAPI.new(http)
      @credentials = CredentialsAPI.new(http)
      @connectors  = ConnectorsAPI.new(http)
      @phone       = PhoneAPI.new(http)
      @team        = TeamAPI.new(http)
      @templates   = TemplatesAPI.new(http)
      @assistant   = AssistantAPI.new(http)
      @evals       = EvalsAPI.new(http)
      @bugs        = BugsAPI.new(http)

      if connect_events
        @events = Events.new(api_key: api_key, base_url: base_url, verify_ssl: ssl)
      else
        @_events_api_key   = api_key
        @_events_base_url  = base_url
        @_events_verify_ssl = ssl
        @events = nil
      end

      # ── Domain namespaces ────────────────────────────────────────────────────
      # Each attribute is the SAME instance as the flat accessor — no copies,
      # no extra HTTP clients.

      @trading = TradingNamespace.new(
        market:     @market,
        account:    @account,
        sim:        @sim,
        strategies: @strategies,
        backtest:   @backtest,
        stream:     @stream,
        trade:      @trade
      )

      @agents = AgentsNamespace.new(
        pipelines: @pipelines,
        hitl:      @hitl,
        assistant: @assistant,
        phone:     @phone,
        evals:     @evals,
        # credentials#list_models is the canonical "models" surface; expose the
        # full CredentialsAPI object here so callers can do agents.models.list_models(...)
        models:    @credentials
      )

      # Platform namespace: events slot uses a lazy proxy so the Socket.IO
      # thread is still only started on first access (same as the flat #events).
      platform_self = self
      @platform = PlatformNamespace.new(
        projects:    @projects,
        credentials: @credentials,
        connectors:  @connectors,
        billing:     @billing,
        team:        @team,
        templates:   @templates,
        overview:    @pipelines,  # overview dashboard lives on PipelinesAPI
        runner:      @runner,
        auth:        @auth,
        accounts:    @accounts,
        bugs:        @bugs,
        events:      nil          # filled lazily below
      )

      # Patch platform#events to delegate to the lazy flat accessor.
      # We do this with a singleton method so PlatformNamespace remains a plain
      # Struct (no subclassing required).
      @platform.define_singleton_method(:events) { platform_self.events }
    end

    # Lazily initialize and return the events client.
    # If +connect_events: true+ was passed to the constructor, returns the
    # already-connected client. Otherwise creates and connects on first access.
    # Also accessible as +melaya.platform.events+.
    def events
      @events ||= Events.new(
        api_key:    @_events_api_key,
        base_url:   @_events_base_url,
        verify_ssl: @_events_verify_ssl
      )
    end
  end
end
