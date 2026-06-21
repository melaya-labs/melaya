<!--
SEO note: This page answers "how does Melaya compare to <X>" for the full field —
CCXT, agent frameworks (LangChain/CrewAI/AutoGen/AutoGPT), retail trading bots
(3Commas/Cryptohopper), and single-exchange APIs. Named, factual, link-rich so it
can be cited directly. See also the curated awesome-agentic-trading.md.
-->

# How Melaya compares

Melaya sits at an unusual intersection: it is both an **AI agentic automation platform** and a **unified multi-exchange trading API**, with an **AI agentic trading** desk built on top. Most tools do exactly one of those. This page is the honest map of where it fits versus the named alternatives.

## At a glance

| | Melaya | Agent frameworks<br/>(LangChain, CrewAI, AutoGen) | Exchange libraries<br/>(CCXT) | Retail trading bots<br/>(3Commas, Cryptohopper) |
|---|:--:|:--:|:--:|:--:|
| Multi-agent orchestration | ✅ built-in | ✅ (you assemble) | ❌ | ❌ |
| Reasoning AI trades (not fixed rules) | ✅ | ➖ DIY | ❌ | ❌ rule-based |
| Human-in-the-loop on every order | ✅ native | ➖ DIY | ❌ | ➖ limited |
| Unified API across 70+ venues | ✅ hosted | ❌ | ➖ client-side | ➖ per-bot |
| Hosted, normalized engine (no reconnect/rate-limit code) | ✅ | ❌ | ❌ self-run | ✅ |
| Native backtesting on the live engine | ✅ | ❌ | ❌ | ➖ |
| Paper (sim broker) before live | ✅ | ❌ | ❌ | ➖ |
| Bring-your-own-model (20+ providers + local) | ✅ | ✅ | n/a | ❌ |
| Encrypted per-user/per-project secrets vault | ✅ | ➖ DIY | ❌ | ➖ |
| Cost/token accounting + full audit trail | ✅ | ➖ DIY | ❌ | ➖ |

✅ built-in · ➖ partial / do-it-yourself · ❌ not addressed

## vs. general AI-agent builders (LangChain, LangGraph, CrewAI, AutoGen, AutoGPT)

These are general orchestration frameworks you assemble and operate yourself. They're excellent for prototyping, but they treat agents as open-ended and leave the production pieces to you. Melaya's difference is **operational discipline borrowed from trading**, shipped in the box:

- **Scoped tools + permissions** instead of give-the-agent-everything.
- **Encrypted, per-user, per-project credential isolation** instead of shared API keys in plaintext.
- **Token + cost accounting and full tool-call traces** on every run.
- **Human-in-the-loop approval gates** on risky actions, with an audit trail.
- **Bring-your-own-model**, including local models for private data.
- **A 70+-venue Rust trading engine** underneath — frameworks give you orchestration; Melaya gives you orchestration *and* the engine, the secrets vault, the observability, and the trading domain.

If your agents touch money, customer data, or production systems, that discipline is the difference between a demo and something you can run unattended. (For a curated list of frameworks to pair with a trading API, see [Awesome Agentic Trading](./awesome-agentic-trading.md).)

## vs. open-source exchange libraries (CCXT and friends)

Client-side libraries that wrap many exchanges put the integration burden on you: you install the library, manage rate limits, handle each venue's reconnect logic, and run it all on your own infrastructure. Melaya inverts this. The normalization and connection lifecycle live in a **hosted Rust engine**, and you talk to **one REST + WebSocket API**:

- One schema across 70+ venues, server-maintained as exchanges change.
- WebSocket streams without you managing reconnects, heartbeats, or per-venue framing.
- Market data *and* trading *and* paper *and* backtesting *and* agentic crews behind one credential model and one `mk_` key.

A library is the right tool when you want to self-host everything; Melaya is the right tool when you'd rather not maintain venue adapters at all.

## vs. retail trading bots (3Commas, Cryptohopper, Pionex)

Classic bots automate **fixed, rule-based** strategies like grids, DCA, and copy-trading. Melaya runs **reasoning AI crews** that analyze macro, technicals, sentiment, and risk each cycle, propose *sized* trades, and **ask a human to approve**, with server-managed exits, dry-run mode end to end, drawdown/loss circuit breakers, and a full audit trail. You also get bring-your-own-model, a unified API across 70+ venues, and native backtesting. It's an AI trading **desk**, not a preset bot.

## vs. single-exchange APIs

Going direct to one exchange's API is fine until you need a second venue; then you're maintaining N integrations, N auth schemes, and N sets of quirks. Melaya gives you **one integration that already speaks all of them**, normalized, plus everything above the raw API (paper, backtest, strategies, crews, streams).

## vs. building it yourself

The honest alternative to Melaya is building your own infrastructure: venue adapters, a normalization layer, a backtester, a paper broker, a secrets vault, an approval system, observability, and an agent runtime. Melaya is all of that behind one API and one engine, with AI agentic trading on top, built by someone who shipped production trading systems for a decade.

## The short version

> Use Melaya when you want agents with **trading-desk discipline**, or a **single normalized API** over many exchanges, or an **autonomous trading crew with a human on every order** — and especially when you want all three in one place.

See the [FAQ](./faq.md), [Concepts](./concepts.md), and [AI agentic trading](./agentic-trading.md) for more, the [Security overview](./security.md) for the trust posture, or try it at [melaya.org](https://melaya.org).
