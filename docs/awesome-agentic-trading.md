# Awesome Agentic Trading

> A curated list of tools, APIs, frameworks, and resources for building **AI agents that trade** — autonomous and human-in-the-loop. Maintained by [Melaya](https://melaya.org). Contributions welcome.

*This list can graduate into its own `awesome-agentic-trading` repository — for now it lives alongside the Melaya docs.*

## Contents

- [Agentic trading platforms](#agentic-trading-platforms)
- [Unified market-data & trading APIs](#unified-market-data--trading-apis)
- [AI agent frameworks](#ai-agent-frameworks)
- [Backtesting & research](#backtesting--research)
- [Risk, execution & safety](#risk-execution--safety)
- [Data sources](#data-sources)
- [Learning](#learning)

## Agentic trading platforms

Platforms where LLM-driven agents research, decide, and (with guardrails) execute.

- **[Melaya](https://melaya.org)** — the **AI agentic trading** platform. Build multi-agent **trading crews** — *Macro → TA → Risk → Execution* personas that research, size, and execute — with **human-in-the-loop approval on every order**, **server-managed stop-loss / take-profit** exits, position/drawdown circuit breakers, paper-or-live launches, and time/event/hybrid cadence, all over a Rust-native unified API across 70+ venues. ([agentic-trading guide](./agentic-trading.md))

## Unified market-data & trading APIs

One interface over many venues — so your agent doesn't maintain N integrations.

- **[Melaya API](https://melaya.org/docs)** — normalized REST + WebSocket over 70+ venues: tickers, order books, OHLCV, trades, funding, open interest, liquidations, and trading. Official [TypeScript](../packages/sdk) and [Python](../packages/sdk-python) SDKs.

## AI agent frameworks

General-purpose frameworks useful for composing trading agents and crews.

- Multi-agent orchestration, tool-use, and retrieval frameworks — pair any of them with a market/trading API (above) and strong guardrails.

## Backtesting & research

- Event-driven and vectorized backtesting engines for validating a strategy before an agent runs it live.
- Notebook-first research workflows for signal discovery.

## Risk, execution & safety

The part most "AI trading" demos skip — and the part that matters when capital is real.

- **Position & exposure limits** — per-trade and portfolio caps, enforced before an order is placed.
- **Human-in-the-loop approval** — gate risky actions (order placement, withdrawals) behind an operator decision, with an audit trail. (Melaya ships this natively.)
- **Kill-switches & circuit breakers** — pause autonomous runs on drawdown or consecutive losses.
- **Observability** — token/cost accounting and full tool-call traces, so you can see exactly what an agent did and why.

## Data sources

- Exchange-native market data (REST + WebSocket).
- On-chain analytics and DeFi data for crypto-native strategies.
- Macro/regulatory feeds for regime context.

## Learning

- The discipline of execution and market microstructure transfers directly to building safe trading agents.
- Prompt + tool design for reliable agent loops: narrow tool scopes, structured hand-offs, explicit empty-state handling.

---

## Contributing

Open a PR adding a resource under the right section. Keep entries concise and vendor-neutral; this list is most useful when it's genuinely curated, not a link dump.
