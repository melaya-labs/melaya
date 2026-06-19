# Concepts — the AI agentic orchestration platform

A short tour of the ideas behind Melaya, the **AI agentic orchestration platform**. For the full product docs and the interactive API reference, see [melaya.org/docs](https://melaya.org/docs). For the flagship trading application of these ideas, see [AI agentic trading](./agentic-trading.md).

## Agents

An **agent** is an LLM given a scoped toolkit, a clear instruction, and a model. Unlike an open-ended chatbot, a Melaya agent can only call the tools it has been granted, and only with the permissions its role allows. Agents run in a sandbox, emit a full trace of every tool call, and can be paused for human approval before any risky action.

## Crews and pipelines

A **crew** (or **pipeline**) is a directed graph of agents. Output flows from one step to the next, with four step kinds:

- **agent** — one agent runs.
- **parallel** — several agents fan out, then their results merge.
- **loop** — an agent iterates until a stop condition or an iteration cap.
- **condition** — branch to a different agent based on the previous output.

Crews let you decompose a workflow (research, analysis, execution, review) into specialized roles instead of one over-loaded prompt. The flagship example is an [AI agentic trading](./agentic-trading.md) crew: Macro → TA → Risk → Execution personas trading with human approval.

## Scheduling & triggers

Crews don't only run on demand. Each can run on a **cadence**: a **time** schedule (e.g. daily, or every few minutes), an **event** trigger that wakes the crew on a real-world or market condition (e.g. a sharp price drop or a liquidation cascade on a watched symbol), or a **hybrid** of both: a routine timer plus event preemption, whichever fires first. Event triggers fire on the rising edge and are debounced, and sub-minute cadences use an event-mode cooldown floor rather than firing faster than a reasoning cycle can finish. The trading-specific details (presets, the trigger composer, deterministic jitter) are in the [AI agentic trading guide](./agentic-trading.md#5-cadence--event-triggers).

## Tools

**Tools** are the typed functions an agent can call: web search, code execution, file I/O, market data, order placement, email, calendar, social, RAG retrieval, and an MCP bridge to external tool servers. Each tool has a schema, a permission level, and optional human-approval gating. Agents are scoped to a subset of tools so they can only do what the workflow intends.

## Connectors

**Connectors** hold the credentials an agent needs to reach an external service (an exchange, a mailbox, a CRM). Credentials are encrypted with AES-256-GCM envelope encryption, isolated per user and per project, and managed through a secrets vault. Agents never see raw secrets — the runtime resolves them at call time inside the sandbox.

## Human-in-the-loop (HITL)

Any tool can be marked as **requiring approval**. When an agent tries to call it, the run pauses and surfaces an approval card to the operator with the exact arguments. The operator approves, edits, or rejects. This is how Melaya keeps autonomous runs safe: the agent proposes, the human disposes, and every decision is audited. For trading crews, approval on **every order** is currently always-on; the option to selectively disable it for fully-autonomous live crews is on the roadmap. (Approvals are actioned in the Studio queue today; a programmatic approvals API ships with the [agent API in v2](./trading.md#strategies).)

## Bring-your-own-model

Melaya is not tied to one LLM vendor. Each agent picks its own provider and model: Anthropic Claude, OpenAI, Google Gemini, NVIDIA, Ollama, LM Studio, and others. Local providers (Ollama, LM Studio) run on your own hardware via a lightweight runner, so private data never leaves your machine.

## RAG

**Retrieval-augmented generation** gives an agent a private knowledge base. Each workflow gets an isolated vector store with hybrid BM25 + dense retrieval, so agents answer from your documents instead of hallucinating.

## The trading engine

Underneath the trading tools is an in-house **Rust engine** that normalizes market data and order routing across 70+ venues: CEX, perpetuals, and prediction markets on one schema. Every venue's quirks (symbol formats, rate limits, settlement suffixes, funding intervals) are absorbed by the engine and presented as one consistent shape. The same engine powers the public API the SDKs wrap: [market data & streaming](./market-data.md), [trading & strategies](./trading.md), and the [venue catalog](./exchanges.md).

## Three ways to trade

Melaya exposes the same trading engine three ways, from safest to most direct:

- **Paper (sim broker)** — orders fill synthetically against the live tape. No venue, no credentials, no capital. The proving ground for a strategy or a crew.
- **Managed strategies** — you launch a strategy and the engine runs the loop, manages server-side stop-loss / take-profit, and gates writes behind approval. Two launchable types via the same API: a **`custom`** Rhai script, or a full **`agent_crew`** trading crew (see [launching a crew](./trading.md#launching-a-trading-crew)). Run it paper, then flip the same definition to live. (The broader agent-builder API for arbitrary non-trading agents arrives in v2.)
- **Direct live trading** — the `trade` plane places, amends, and cancels real orders on a connected exchange, and reads balances, positions, and fills — one normalized call shape across every venue.

The first two need only your `mk_` key; live order placement additionally binds a connected exchange key.

## Backtesting

Before any capital moves, a strategy can be replayed against historical data on the same **Rust engine** that runs it live, so the backtest and the live loop share one execution model. A backtest can be a single run, a **grid sweep** over a parameter space, or a **random sample**, and it reports trades, equity curve, and summary statistics. Funding and fees are modeled for perpetuals.

## AI parameter optimization

A strategy's parameters don't have to be hand-tuned. The **AI optimizer** proposes parameter sets, evaluates them by backtest, and surfaces the strongest candidates for you to **approve** before they touch a live run. It's optimization with a human gate, not a black box.

## The SDKs

Everything above is reachable from **nine official SDKs** (TypeScript/JavaScript, Python, Go, Rust, Java, Kotlin, C#/.NET, Ruby, and PHP), each exposing one identical surface (market data, account, paper + live trading, strategies, backtesting, and public/private streams) over the unified API. The SDKs are thin; the engine, the normalization, and the safety rails live server-side.

## Observability

Every run records its messages, tool calls, tool results, model invocations (with token and cost accounting), and error reasons. Nothing is a black box: you can see exactly what each agent did, what it spent, and why it stopped.
