<!--
SEO note: This FAQ is written to answer the full long-tail of questions about
AI agents, agentic AI, autonomous/AI crypto trading, multi-agent crews,
human-in-the-loop trading, the unified exchange API, and how Melaya compares
to CCXT, agent frameworks, and trading bots. Each answer is self-contained so
it can be quoted in isolation by search engines and LLM assistants.
Keywords: AI agentic trading, autonomous trading, AI trading agent, multi-agent
crew, human-in-the-loop, bring-your-own-model, unified crypto API, 70+ exchanges,
Rust trading engine, paper trading, backtesting, MCP, RAG, trading bot, CCXT alternative.
-->

# Frequently asked questions — AI agentic trading & automation

Everything about **Melaya**, the AI agentic orchestration platform and AI agentic trading desk: what it is, how autonomous trading crews work, how they stay safe, the unified exchange API, supported models, security, and how Melaya compares to the alternatives.

---

## About Melaya

### What is Melaya?

Melaya is an **AI agentic platform for trading, research, and automation**. It lets you build, run, and operate AI agents and multi-agent **crews** with scoped tools, secure connectors, per-workflow RAG, cost tracking, audit logs, and human-in-the-loop approval. It also exposes a unified REST + WebSocket API for normalized market data and trading across **70+ venues**, backed by an in-house Rust engine. In short: it brings **trading-desk discipline** to AI automation, with **AI agentic trading** as its flagship application.

### What is "AI agentic trading"?

AI agentic trading is autonomous, multi-agent trading run by a team of specialized AI agents (a **trading crew**) instead of a single script or a single "trading bot" prompt. On Melaya a crew researches the market, finds setups, sizes risk, and executes orders, with a **human approving every order** and trading-grade safety rails throughout. It's the difference between an open-ended chatbot that "talks about trading" and a permissioned, auditable desk that can run unattended. See the full [AI agentic trading guide](./agentic-trading.md).

### Who is Melaya for?

Operators, founders, trading teams, quants, and developers who want AI agents that behave like a trading desk (observable, permissioned, auditable, and safe to run unattended) rather than open-ended chatbots. It suits both non-coders (build a crew in a visual wizard) and developers (drive everything through nine SDKs and a public API).

### What makes Melaya different from other AI agent platforms?

Melaya applies **trading-grade discipline** to general automation:

- **Scoped tools and explicit permissions** — an agent can only do what it's granted.
- **Encrypted, per-user/per-project credential isolation** — agents never see raw secrets.
- **Human-in-the-loop gating** on every risky action, with a full audit trail.
- **Full observability** — every tool call traced with token and cost accounting.
- **Bring-your-own-model** — 20+ providers plus local models, no LLM vendor lock-in.
- **An in-house Rust trading engine** spanning 70+ venues, the same engine behind backtests and live trading.

Most agent frameworks give you orchestration and stop there. Melaya ships the orchestration **and** the safety rails, the secrets vault, the observability, and a production trading engine underneath.

### Is Melaya a no-code or a developer platform?

Both. You can build and launch a trading crew entirely in the visual Studio wizard with no code, **or** drive trading programmatically through the public API and **nine official SDKs**: market data, account, paper + live trading, backtesting, and launching strategies, including [`agent_crew` trading crews](./trading.md#launching-a-trading-crew). The broader agent-builder API (arbitrary non-trading agents, pipelines, RAG, connectors) arrives in **v2**; today the visual Studio is the way to compose general agentic workflows, and the API launches `custom` and `agent_crew` strategies.

### Who built Melaya?

Antoine Roche, who spent the previous decade building production trading systems at BNP Paribas CIB, SingularityDAO, and Singularity Venture Hub. Melaya is operated from the United Arab Emirates.

---

## AI agents & crews

### What is an AI agent?

An **agent** is an LLM given a scoped toolkit, a clear instruction, and a model. Unlike an open-ended chatbot, a Melaya agent can only call the tools it's been granted, runs in a sandbox, emits a full trace of every tool call, and can be paused for human approval before any risky action. The agent proposes; the human disposes.

### What is a crew (or pipeline)?

A **crew** is a directed graph of specialized agents (for example research → analysis → execution → review) where output flows from one step to the next. Steps can run **sequentially, in parallel, in a loop, or branch on a condition**. Crews let you decompose a workflow into specialized roles instead of one over-loaded prompt. The flagship example is a trading crew: Macro Analyst and TA Analyst in parallel → Risk Manager → Execution Trader. See [concepts](./concepts.md) and [agentic trading](./agentic-trading.md).

### What is "human-in-the-loop" (HITL)?

Any tool can be marked as **requiring approval**. When an agent tries to call it, the run pauses and surfaces an **approval card** with the exact arguments. The operator **approves, edits, or rejects**, and every decision is audited. In trading, every order-placing tool is HITL-gated: the crew can research and reason freely, but no order fires without a human. This is how Melaya makes autonomous runs safe.

### What are tools and connectors?

**Tools** are the typed functions an agent can call — web search, code execution, file I/O, market data, order placement, email, calendar, social, RAG retrieval, and an **MCP bridge** to external tool servers. Each tool has a schema, a permission level, and optional approval gating. **Connectors** hold the credentials a tool needs (an exchange, a mailbox, a CRM); credentials are encrypted with AES-256-GCM envelope encryption, isolated per user and per project, and resolved at call time inside the sandbox so agents never see raw secrets.

### What is RAG and how does Melaya use it?

**Retrieval-augmented generation** gives an agent a private knowledge base. Each workflow gets an **isolated vector store with hybrid BM25 + dense retrieval**, so agents answer from your documents instead of hallucinating. Useful for trading theses, house rules, research libraries, or any domain context a crew should reason over.

### Can agents run on a schedule or react to events?

Yes. Every crew can run on a **cadence**: a **time** schedule (daily, hourly, every few minutes), an **event** trigger that wakes the crew on a market condition (a sharp price drop, a liquidation cascade, a funding flip), or a **hybrid**: a routine timer plus event preemption, whichever fires first.

---

## AI agentic trading (deep dive)

### Can Melaya's agents actually place real trades?

Yes. This is the flagship capability. You build multi-agent **trading crews** that research, size, and execute trades. Only the **Execution Trader** persona can place orders, and it **pauses for your approval on every order**. On approval, the engine **manages stop-loss / take-profit exits server-side**, so exits don't depend on the agent staying awake. Crews run **paper or live**, on a schedule or on market triggers, with position limits, drawdown circuit breakers, and a full audit trail.

### What are the trading personas?

Melaya ships **seven** specialized trading personas you mix and match:

- **Macro Analyst** — labels the regime (RISK_ON / RISK_OFF / CHOP / VOL_EXPANSION) from rates, DXY, equities, on-chain flows, funding, and liquidations.
- **TA Analyst** — ranks setups and places tactical stop + take-profit as absolute prices.
- **Quant Analyst** — systematic edge discovery and ranking from order flow, funding skew, open interest.
- **Sentiment Analyst** — news, social, and narrative catalysts with direction and time-to-event.
- **Risk Manager** — **holds the veto** and sizes the notional; outputs go/no-go *and* a size.
- **Portfolio Manager** — exposure, correlation, and rebalancing across the whole book.
- **Execution Trader** — the **only** seat allowed to place orders; turns the approved plan into exact exchange calls.

Run any subset, sequentially or in parallel, each on its own model.

### How does a trading crew decide what to trade, step by step?

A typical cycle: the crew wakes on its cadence → safety checks run first → analysts pull live market data and produce structured signals → the Risk Manager sizes or vetoes each idea → the Execution Trader proposes each order and **pauses for approval** → on approval the order routes to the venue and a **server-side watcher manages the exits** → the crew sleeps until the next tick or trigger. Worked example in the [agentic-trading guide](./agentic-trading.md).

### Is autonomous AI trading safe?

It's only safe with guardrails, which is the entire design point. Melaya enforces layered rails, most at the platform level so they hold **regardless of what the LLM does**: scoped permissions (only the Execution seat writes), human approval on every order, a Risk Manager veto, a per-cycle write cap, a daily order quota, a daily LLM cost cap, cumulative- and consecutive-loss circuit breakers, tenant isolation, an egress allowlist (the crew can't reach exchanges directly), a server-enforced tool allowlist, and a **paper-soak gate** before any crew can go live. On top of these, four optional **reactive sidecars** watch the market in real time.

### What are the reactive sidecars?

Independent watchers that run *beside* the crew loop. Two are **blockers** that reject writes the instant their sensor trips (before you're even asked), two are **triggers** that wake the crew early:

- **Drawdown Sentinel** — flips the crew RED and blocks writes if equity drops ~5% from its session peak.
- **Macro Blackout** — blocks new entries from 30 min before to 60 min after scheduled releases (FOMC, CPI, NFP).
- **Funding Flip** — wakes the crew when funding sign flips on a held position so it can reprice.
- **Liquidation Cascade** — fires an extra cycle when forced liquidations spike, so the crew can fade or stand aside.

### What's the difference between paper and live trading?

The **same crew definition** backs both. **Paper** routes orders through a simulated broker that fills against the live tape: no venue, no credentials, no capital, but the same approval flow, cadence, and audit trail. **Live** routes to a connected exchange account. Mode is a launch-time choice. Live keys stay locked until a crew clears a **paper-soak window** (a minimum runtime and minimum number of simulated fills), so you can't trip the wrong account on day one.

### What is "server-managed stop-loss / take-profit"?

When an entry is approved, the Execution seat places a **single entry order with the stop-loss and take-profit attached**, and Melaya's engine watches the live tape and fires the close leg the moment price crosses your level, with no fragile follow-up orders and no dependence on the agent being awake. It's also why the safe default on shutdown is to **leave** positions: the venue-side SL/TP is the safety rail.

### Can I build a trading bot without writing code?

Yes. The Studio's eight-step wizard (Crew → Tools → Context → Orchestrate → Universe → Cadence → Safety → Review) lets you assemble a crew, pick a model per persona, choose the exchange and symbols, set the cadence and caps, and launch, all with no code. Developers can launch the same crew programmatically as a `strategyType: "agent_crew"` strategy ([API reference](./trading.md#launching-a-trading-crew)). Start from one of the shipped templates and adapt.

### What trading templates ship out of the box?

Three ready-to-run crews, all paper by default: **Live Demo Crew** (10-min schedule on BTC/ETH/SOL; the fastest way to watch a crew work), **Daily Majors Long** (daily, long-only on BTC/ETH/SOL/BNB/AVAX with ATR stops, R:R ≥ 1.5, exposure caps, and a release-window blackout), and **Intraday TA Reactive** (hourly pulse plus per-symbol price-drop triggers on the 5-minute charts). Clone any of them to make your own variant.

### Where does a crew run — Melaya's cloud or my machine?

Either. **Cloud pool** runs on Melaya's infrastructure; **local runner** runs on your own machine (e.g. a Mac mini or NUC) and is required when any persona uses a local model, so your prompts and model key never leave your hardware. In both modes all venue requests route through Melaya's engine from Melaya's egress IP, so you whitelist **Melaya's** IP on your exchange, never your laptop. Order intent and fills still flow through the approval gate and audit chain.

### What can I trade with a crew?

Spot and perpetual futures across the supported venues, plus prediction markets (Polymarket, Kalshi, and more) via a unified tool surface. A crew's universe is any set of symbols you pick from a connected venue.

---

## Trading engine, exchanges & the API

### Does Melaya support crypto trading?

Yes. Melaya provides a **unified API over 70+ venues** for market data (tickers, order books, OHLCV, trades, funding rates, open interest, liquidations) and trading (orders, positions, balances, fills, and agent-driven strategies). An in-house **Rust engine** normalizes every venue into one schema, so you integrate once.

### Which exchanges does Melaya support?

70+ venues are live and validated today: **60 spot exchanges**, **5 perpetual-futures venues** (binanceusdm, bingxfutures, bitgetfutures, bybitlinear, okxswap), and **6 prediction-market / DEX venues** (azuro, drift_pm, kalshi, overtime, polymarket, sxbet), including Binance, Bybit, OKX, Coinbase, Kraken, KuCoin, Bitget, MEXC, Hyperliquid, and many more. CEX, perpetuals, and prediction markets all share one normalized API surface. The engine additionally carries **integrated adapters enabled on demand** (Deribit, BitMEX, Gate, HTX, dYdX, and others) that are activated once they clear validation testing, which we prioritize when a customer wants to trade there. The live, always-current list of activated venues is the source of truth: `GET https://api.melaya.org/api/v1/market/list-exchanges`. Full breakdown in [exchanges & the unified API](./exchanges.md).

### What is the unified / normalized API?

One REST + WebSocket schema across every venue. A ticker always exposes `bid`, `ask`, `last`, `high`, `low`, `baseVolume`, `quoteVolume`, and `timestamp`, regardless of exchange. Symbol formats, rate limits, settlement suffixes, funding intervals, and connection lifecycles are absorbed by the engine. Your code does not branch per exchange.

### How do I access the API programmatically?

Create an API key in the dashboard (keys are prefixed `mk_`) and pass it as `?apiKey=mk_...` or `Authorization: Bearer mk_...`. REST base: `https://api.melaya.org`. WebSocket base: `wss://wss.melaya.org`. Reads, paper trading, and backtesting need only the `mk_` key; **live** order placement additionally binds a connected exchange key.

### Is there an SDK? Which languages?

Yes: **nine** official SDKs with one identical surface each: TypeScript/JavaScript (`@melaya/sdk`), Python (`melaya`), Rust (`melaya`), Go, Ruby, Java, Kotlin, C#/.NET, and PHP. They're thin clients over the public API (market data, account, paper (sim) trading, strategies + the AI optimizer, backtesting, the live trading plane, and public/private WebSocket streams), while the engine runs server-side.

### What are the "three ways to trade"?

From safest to most direct: **Paper (sim broker)** gives synthetic fills against the live tape, no venue or capital; **Managed strategies** launch a `custom` Rhai script or a full agentic crew and the engine runs the loop with server-side SL/TP and approval gating; **Direct live trading** uses the `trade` plane to place, amend, and cancel real orders and read balances/positions/fills, one call shape across every venue. The first two need only your `mk_` key.

### Does Melaya support backtesting?

Yes, native, on the **same Rust engine** that runs live, so the backtest and the live loop share one execution model. A backtest can be a single run, a **grid sweep** over a parameter space, or a **random sample**, and it reports trades, equity curve, and summary statistics. Funding and fees are modeled for perpetuals.

### What is the AI parameter optimizer?

Instead of hand-tuning, the **AI optimizer** proposes parameter sets, evaluates each by backtest, and surfaces the strongest candidates for you to **approve** before they touch a live run. It's optimization with a human gate, not a black box.

### Does Melaya offer real-time market data streaming?

Yes. WebSocket streams for ticker, order book, OHLCV, public trades, and liquidations (filterable to one venue or consumed as a cross-exchange firehose), plus authenticated private streams for account balance/positions/orders/fills and strategy events. The ticker stream fires only when the normalized ticker advances, with no duplicate frames.

---

## Models & bring-your-own-model

### Which LLM models can I use?

20+ providers (Anthropic Claude, OpenAI, Google Gemini, NVIDIA, Mistral, DeepSeek, xAI (Grok), Moonshot (Kimi), Zhipu, Qwen, Cerebras, Groq, SambaNova, Upstage, Reka, MiniMax, and OpenRouter), plus **local models** via Ollama and LM Studio. Each agent picks its own provider and model, so one crew can mix a strong cloud model on Risk with a fast local model on the analysts.

### Can I run AI trading with local / open-source models for free?

Yes. Run local providers (Ollama, LM Studio) through a lightweight runner on your own machine. Prompts and your model key stay local, and there's no per-token LLM cost, though the platform still tracks token usage for observability. Fully-local crews are exempt from the daily cost cap.

### Is Melaya locked to one AI vendor?

No. It's **bring-your-own-model** by design: switch providers per agent, mix cloud and local, and avoid lock-in to any single LLM vendor.

### Does my private data stay private with local models?

In local-runner mode the LLM call goes directly from your machine to your provider (or stays entirely on-device with Ollama/LM Studio), so Melaya never sees those prompts or your model key. Order intent and fills still pass through the approval gate and audit chain so the safety and compliance story holds.

---

## Safety, security & compliance

### How does Melaya keep autonomous agents safe?

Three foundational layers plus trading-specific rails: **(1)** scoped tools and permissions, so an agent can only do what it's granted; **(2)** encrypted per-user credential isolation, so agents never see raw secrets; **(3)** human-in-the-loop approval gates on risky tools, so the run pauses for an operator decision and every decision is audited. For trading, add write caps, order quotas, cost caps, loss circuit breakers, an egress allowlist, a server-enforced tool allowlist, and the paper-soak gate.

### How are my exchange API keys and secrets stored?

Credentials are encrypted with **AES-256-GCM envelope encryption**, isolated per user and per project, and managed through a secrets vault. Agents never receive raw secrets; the runtime resolves them at call time inside the sandbox. For live trading you reference a connected key by id (`apiKeyId`), never by value.

### Is there an audit trail?

Yes. Every run records its messages, tool calls, tool results, model invocations (with token and cost accounting), and error reasons. For trading, every approved write produces a complete chain (the approval request, who decided and what they changed, and the resulting fill), so any cycle can be reconstructed as "what did this crew do, and by whose authority?"

### Can a runaway AI fire hundreds of orders?

No. A **per-cycle write cap** rejects an over-limit batch before a single approval card is shown, a **daily order quota** bounds total orders, and **loss circuit breakers** auto-pause a crew on cumulative or consecutive losses (a halted crew requires a deliberate human reset, not just a resume). The Drawdown Sentinel adds a real-time block on top.

### Does Melaya prevent agents from reaching the internet or exchanges directly?

For trading crews, yes: an **egress allowlist** means the crew container can't reach exchanges or arbitrary URLs directly; all order flow goes through Melaya's engine, and venue requests originate from Melaya's own egress. Combined with a server-enforced tool allowlist, this is defense-in-depth against prompt injection trying to bypass the execution path.

### Is Melaya SOC 2 compliant? Where's the full security overview?

Melaya is **building toward SOC 2 Type II** and maintains an internal control matrix mapping every Trust Service Criterion to the control that implements it, but does **not yet hold** a third-party SOC 2 or ISO 27001 attestation; we state that honestly rather than overclaim. The full public **[Security & trust overview](./security.md)** covers encryption, credential isolation, authentication, tenant isolation, audit logging, supply-chain scanning, and disclosure. Enterprise/Citadel customers can request the current control summary under NDA.

### Can I turn off the human approval gate?

Today, no: every order from a live crew is human-in-the-loop by default, and approval is currently always-on. The ability to selectively disable it (for fully-autonomous live crews, per-crew or per-tool) is on the roadmap. Approvals are actioned in the Studio approval queue; a **programmatic approvals API** (receive an approval request, then approve / edit / reject in code) ships with the full agent API in **v2**.

---

## How Melaya compares

Melaya sits at the intersection of an agentic platform and a unified trading API, so the comparison depends on what you'd otherwise reach for: CCXT, an agent framework (LangChain / CrewAI / AutoGen), a retail trading bot (3Commas / Cryptohopper), or a single-exchange API. The full side-by-side table and per-tool breakdown lives on the dedicated **[comparison page](./comparison.md)**. The short version: use Melaya when you want **trading-desk discipline on your agents**, a **single normalized API over 70+ venues**, or an **autonomous trading crew with a human on every order**, especially all three at once.

---

## Use cases

### What can I build with Melaya?

AI agentic trading crews (paper or live), market-research and screening pipelines, signal/alerting workflows, portfolio-monitoring agents that ping Telegram/Slack/Discord on conditions, automated trade journals into Google Sheets, daily macro/market briefings, and general business automation (email, calendar, social, file generation), all with scoped tools, HITL gating, and audit logs.

### Can a crew alert me instead of (or before) trading?

Yes. Messaging tools (Telegram, Slack, Discord, WhatsApp/SMS/voice, email, Twitter) let a crew self-report: the Execution seat can ping a channel after a fill, or a research crew can send a daily briefing. You can run "analysis-only" crews with no write/order tools at all.

### Can I plug in my own data or watchlist?

Yes, via the user-data tools (Google Sheets/Drive/Docs, local Excel/Word, files, Airtable, GitHub) and per-workflow RAG. A crew can read your watchlist or target allocation and write a journal or report back.

---

## Getting started

### How do I get started?

Build your first crew from a template in the Studio, run it in **paper**, watch it reason through a few cycles, and approve a trade or two. When it's cleared its soak, flip the same definition to **live**. Developers can launch the same crew via the API. See the [AI agentic trading guide](./agentic-trading.md), [concepts](./concepts.md), [trading & strategies](./trading.md), and [exchanges](./exchanges.md).

### Do I need an exchange account to try it?

No. Paper trading and backtesting need only your `mk_` API key: no venue, no credentials, no capital. You connect an exchange key only when you're ready to trade live.

### Is there a free way to evaluate Melaya?

Paper trading is the free proving ground for any strategy or crew, and local models carry no per-token cost. For current plans and limits, see [melaya.org](https://melaya.org).

### Where can I learn more?

Website: https://melaya.org · Documentation: https://melaya.org/docs · Source & SDKs: https://github.com/melaya-labs/melaya · Discord: https://discord.gg/2BBMUUdnkj
