# frozen_string_literal: true

module Melaya
  # ── Domain namespace objects ─────────────────────────────────────────────────
  #
  # Three read-only namespace objects hang off every +Melaya::Client+ instance,
  # grouping the flat module accessors into logical planes:
  #
  #   melaya.trading  — market data, account, sim, strategies, backtest, stream, trade
  #   melaya.agents   — pipelines/runs, hitl, assistant, phone, evals, models
  #   melaya.platform — projects, credentials, connectors, billing, team, templates,
  #                     overview (via pipelines), runner, auth, mfa (via auth),
  #                     accounts, bugs, events
  #
  # Every attribute on these objects is the *same* instance that is also reachable
  # via the flat accessor on the client, so there is no duplication of state and
  # no double HTTP calls.
  #
  # @example
  #   melaya = Melaya::Client.new(api_key: ENV["MELAYA_API_KEY"])
  #
  #   # Namespaced (primary API)
  #   melaya.trading.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")
  #   melaya.agents.pipelines.list(project: "my-project")
  #   melaya.platform.projects.list
  #
  #   # Flat aliases still work (backward-compatible)
  #   melaya.market.ticker(exchange: "binance", symbol: "BTC/USDT", market: "spot")

  # Namespace grouping all trading-plane modules.
  #
  # Modules:
  # - +market+     — REST market-data + reference endpoints (public + authenticated).
  # - +account+    — Authenticated account reads: connected keys, tier limits, usage.
  # - +sim+        — Paper trading (sim broker): virtual balance, positions, and orders.
  # - +strategies+ — Launch, control, and inspect trading strategies (paper + live).
  # - +backtest+   — Historical backtests + parameter sweeps on the Rust engine.
  # - +stream+     — WebSocket streaming endpoints (public market data + private feeds).
  # - +trade+      — Live trading — credentialed order placement on a connected exchange.
  TradingNamespace = Struct.new(
    :market,
    :account,
    :sim,
    :strategies,
    :backtest,
    :stream,
    :trade,
    keyword_init: true
  )

  # Namespace grouping all agent-plane modules.
  #
  # Modules:
  # - +pipelines+  — Pipeline runs, traces, schedules, and overview dashboard.
  #                  (also aliased as +runs+ for ergonomics)
  # - +hitl+       — Human-in-the-loop approval queue: list pending, approve, reject.
  # - +assistant+  — Assistant onboarding profile (get + set).
  # - +phone+      — Phone device control: pair, list, screen-tree, apps.
  # - +evals+      — Agent evaluation runs and benchmarks.
  # - +models+     — AI model list (reached via credentials#list_models; this is
  #                  the CredentialsAPI instance filtered by convention — call
  #                  +models.list_models(provider: "anthropic")+ etc.)
  AgentsNamespace = Struct.new(
    :pipelines,
    :hitl,
    :assistant,
    :phone,
    :evals,
    :models,
    keyword_init: true
  ) do
    # +runs+ is an ergonomic alias for +pipelines+ (agents call them "runs").
    def runs
      pipelines
    end
  end

  # Namespace grouping all platform-plane modules.
  #
  # Modules:
  # - +projects+     — Create and list agent projects.
  # - +credentials+  — User-scoped credential storage (services, OAuth, env handles, models).
  # - +connectors+   — Project-scoped connector credentials.
  # - +billing+      — Subscription, Stripe checkout/portal, pricing plans, credit balances.
  # - +team+         — Project team management: members, roles, invite links.
  # - +templates+    — Pipeline templates: create, share, assign, and manage visibility.
  # - +overview+     — Pipeline overview dashboard (same object as +agents.pipelines+,
  #                    exposed here for discoverability on the platform plane).
  # - +runner+       — Runner tokens: mint, list, revoke +mel_run_+ tokens.
  # - +auth+         — Login, MFA, registration, password management, session tokens.
  # - +mfa+          — Alias for +auth+ (MFA operations live on the same AuthAPI object).
  # - +accounts+     — Account management: GDPR export, CEX key removal, profile updates.
  # - +bugs+         — Bug reports: submit, track, and comment.
  # - +events+       — Platform real-time events over Socket.IO.
  PlatformNamespace = Struct.new(
    :projects,
    :credentials,
    :connectors,
    :billing,
    :team,
    :templates,
    :overview,
    :runner,
    :auth,
    :accounts,
    :bugs,
    :events,
    keyword_init: true
  ) do
    # +mfa+ is an ergonomic alias for +auth+ (MFA methods live on AuthAPI).
    def mfa
      auth
    end
  end
end
