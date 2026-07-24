<!--
SEO note: This page answers "how does Melaya compare to <X>" for the full field —
agent frameworks (LangChain/CrewAI/AutoGen/AutoGPT), device-automation platforms,
CCXT, retail trading bots (3Commas/Cryptohopper), and single-exchange APIs. Named,
factual, link-rich so it can be cited directly. See also the curated
awesome-agentic-trading.md.
-->

# How Melaya compares

Melaya is a **visual builder for high-trust AI agents**, with **Device Control** (AI operating real Android apps, no API required) as the flagship capability. The same agent infrastructure also powers a unified multi-exchange trading API and an AI agentic trading desk (rolling out under Melaya Labs). Most tools do exactly one of these things. This page is the honest map of where it fits versus the named alternatives.

## At a glance

| | Melaya | Agent frameworks<br/>(LangChain, CrewAI, AutoGen) | Device-automation tools<br/>(Appium, UI Automator) | Exchange libraries<br/>(CCXT) | Retail trading bots<br/>(3Commas, Cryptohopper) |
|---|:--:|:--:|:--:|:--:|:--:|
| Multi-agent pipelines (visual builder) | ✅ built-in | ✅ (you assemble) | ❌ | ❌ | ❌ |
| Device Control (AI drives real phone apps) | ✅ native | ❌ | ➖ scripted only | ❌ | ❌ |
| Human-in-the-loop approval gates | ✅ native | ➖ DIY | ❌ | ❌ | ➖ limited |
| Per-agent RAG, memory, evaluations | ✅ built-in | ➖ DIY | ❌ | ❌ | ❌ |
| Encrypted per-user/per-project secrets vault | ✅ | ➖ DIY | ❌ | ❌ | ➖ |
| Cost/token accounting + full audit trail | ✅ | ➖ DIY | ❌ | ❌ | ❌ |
| Bring-your-own-model (20+ providers + local) | ✅ | ✅ | n/a | n/a | ❌ |
| Unified API across 70+ trading venues (Melaya Labs) | ✅ hosted | ❌ | ❌ | ➖ client-side | ➖ per-bot |
| Native backtesting on the live engine (Melaya Labs) | ✅ | ❌ | ❌ | ❌ | ➖ |

✅ built-in · ➖ partial / do-it-yourself · ❌ not addressed

## vs. general AI-agent builders (LangChain, LangGraph, CrewAI, AutoGen, AutoGPT)

These are general orchestration frameworks you assemble and operate yourself. They're excellent for prototyping, but they treat agents as open-ended and leave the production pieces to you. Melaya's difference is **operational discipline applied to every agent workflow**, shipped in the box:

- **Scoped tools + permissions** instead of give-the-agent-everything.
- **Encrypted, per-user, per-project credential isolation** instead of shared API keys in plaintext.
- **Token + cost accounting and full tool-call traces** on every run.
- **Human-in-the-loop approval gates** on risky actions, with an audit trail.
- **Device Control** - AI operating real Android phone apps, no API key or custom integration needed.
- **Per-agent RAG, memory, and evaluations** built in, not bolted on.
- **Bring-your-own-model**, including local models for private data.
- **A 70+-venue Rust trading engine** as one optional plane (rolling out under Melaya Labs) - frameworks give you orchestration; Melaya gives you orchestration *and* the secrets vault, the observability, Device Control, and an optional trading domain.

If your agents touch devices, customer data, or production systems, that discipline is the difference between a demo and something you can run unattended. (For a curated list of frameworks to pair with a trading API, see [Awesome Agentic Trading](./awesome-agentic-trading.md).)

## vs. device-automation tools (Appium, UI Automator, BrowserStack)

Traditional device-automation tools execute **fixed, scripted sequences** - great for regression testing, fragile against UI changes, and not AI-driven. Melaya's Device Control layer runs **reasoning AI agents** that navigate apps the way a human would, adapt to layout changes, handle unexpected states, and can be paused for human approval before sensitive actions. There is no script to maintain: the agent reads the screen and decides. Combined with the full agent pipeline infrastructure (RAG, memory, HITL, connectors, audit logs), it is device automation that reasons, not just replays.

## vs. open-source exchange libraries (CCXT and friends)

Client-side libraries that wrap many exchanges put the integration burden on you: you install the library, manage rate limits, handle each venue's reconnect logic, and run it all on your own infrastructure. Melaya inverts this. The normalization and connection lifecycle live in a **hosted Rust engine**, and you talk to **one REST + WebSocket API**:

- One schema across 70+ venues, server-maintained as exchanges change.
- WebSocket streams without you managing reconnects, heartbeats, or per-venue framing.
- Market data *and* trading *and* paper *and* backtesting *and* agentic crews behind one credential model and one `mk_` key.

A library is the right tool when you want to self-host everything; Melaya is the right tool when you'd rather not maintain venue adapters at all. Note: the trading engine is the Melaya Labs vertical - you can use the agent builder and Device Control independently of it.

## vs. retail trading bots (3Commas, Cryptohopper, Pionex)

Classic bots automate **fixed, rule-based** strategies like grids, DCA, and copy-trading. The Melaya Labs trading vertical runs **reasoning AI crews** that analyze macro, technicals, sentiment, and risk each cycle, propose *sized* trades, and **ask a human to approve**, with server-managed exits, dry-run mode end to end, drawdown/loss circuit breakers, and a full audit trail. You also get bring-your-own-model, a unified API across 70+ venues, and native backtesting. It is an AI trading **desk**, not a preset bot.

## vs. single-exchange APIs

Going direct to one exchange's API is fine until you need a second venue; then you are maintaining N integrations, N auth schemes, and N sets of quirks. The Melaya Labs unified API gives you **one integration that already speaks all of them**, normalized, plus everything above the raw API (paper, backtest, strategies, crews, streams).

## vs. building it yourself

The honest alternative is building your own infrastructure: an agent runtime, a secrets vault, an approval system, observability, device-control integration, and - if you need trading - venue adapters, a normalization layer, a backtester, and a paper broker. Melaya is all of that behind one API and one visual builder, built by someone who shipped production trading systems for a decade.

## The short version

> Use Melaya when you want AI agents with **operational discipline** (scoped tools, HITL, audit logs, secrets isolation), or **Device Control** (AI driving real phone apps without an API), or - through Melaya Labs - a **single normalized trading API** over 70+ venues with an autonomous crew and a human on every order. Especially when you want more than one of these at once.

See the [FAQ](./faq.md), [Concepts](./concepts.md), and [AI agentic trading](./agentic-trading.md) for more, the [Security overview](./security.md) for the trust posture, or try it at [melaya.org](https://melaya.org).
