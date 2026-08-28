# Concepts — the Melaya agent platform

A short tour of the ideas behind Melaya, the **platform for building, running, and orchestrating high-trust AI agents** — and for putting those agents in control of real mobile **devices**. Everything here is live today. For the full product docs and the interactive API reference, see [melaya.org/documentation](https://melaya.org/documentation). Two deeper guides sit alongside this one: the [agent builder](./agent-builder.md) and the flagship [Device Control](./device-control.md). Trading is one vertical built on this platform; it is covered last.

## Agents

An **agent** is an LLM given a scoped toolkit, a clear instruction, and a model. Unlike an open-ended chatbot, a Melaya agent can only call the tools it has been granted, and only with the permissions its role allows. Agents run in a sandbox, emit a full trace of every tool call, and can be paused for human approval before any risky action. Each agent carries its own model, prompt, tools, retrieval, static context, and memory — the building block for everything below.

## Crews and pipelines

A **crew** (or **pipeline**) is a directed graph of agents. Output flows from one step to the next, with four step kinds:

- **agent** — one agent runs.
- **parallel** — several agents fan out, then their results merge.
- **loop** — an agent iterates until a stop condition or an iteration cap.
- **condition** — branch to a different agent based on the previous output.

Crews let you decompose a workflow (research, analysis, execution, review) into specialized roles instead of one over-loaded prompt. Build them visually in the [agent builder](./agent-builder.md) or from **templates** you clone and adapt.

## Tools

**Tools** are the typed functions an agent can call: web search, code execution, file I/O, RAG retrieval, email, calendar, social, device actions, market data, order placement, and an MCP bridge to external tool servers. Each tool has a schema, a permission level, and optional human-approval gating. Agents are scoped to a subset of tools so they can only do what the workflow intends.

## Sub-agents

An agent can delegate to a **sub-agent**: a nested agent (with its own model, prompt, and tools) exposed to the parent as a callable tool. This keeps a lead agent's context lean while letting it hand off self-contained work — research, extraction, verification — to a specialist, and compose specialists into larger behaviors.

## RAG

**Retrieval-augmented generation** gives an agent a private knowledge base. Each workflow gets an isolated vector store with hybrid BM25 + dense retrieval, so agents answer from your documents instead of hallucinating.

## Static context

Beyond retrieval, an agent can be given **static context**: fixed reference material — playbooks, schemas, policy, style guides — pinned into every run. It's always present (not retrieved on demand), so the behavior you rely on doesn't depend on a search hit.

## Memory

Agents can carry **memory** across steps and across runs, so a crew remembers earlier findings, decisions, and state instead of starting cold each cycle. Memory is budgeted per agent so a pinned brief survives even when the working context is trimmed.

## Evaluations

Any loop step can run an **evaluation** that judges the agent's output and decides whether to continue, retry, or stop. Evaluations are governed by a **`loopPolicy`** with three modes:

- **`off`** — no evaluation; the loop runs on its own stop condition.
- **`observe_only`** — the evaluator scores and records, but never blocks; use it to measure quality without changing behavior.
- **`enforce`** — the evaluator gates the loop; a failing output triggers a retry or halt.

This lets you dial a crew from measured to strictly-gated without rewriting it.

## Human-in-the-loop (HITL)

Any tool can be marked as **requiring approval**. When an agent tries to call it, the run pauses and surfaces an approval card to the operator with the exact arguments. The operator approves, edits, or rejects. This is how Melaya keeps autonomous runs safe: the agent proposes, the human disposes, and every decision is audited. Approvals are actioned in the Studio queue today and are also exposed programmatically through the API and SDKs.

## Scheduling & triggers

Crews don't only run on demand. Each can run on a **cadence**: a **time** schedule (e.g. daily, or every few minutes), an **event** trigger that wakes the crew on a real-world condition, or a **hybrid** of both — a routine timer plus event preemption, whichever fires first. Event triggers fire on the rising edge and are debounced, and sub-minute cadences use an event-mode cooldown floor rather than firing faster than a reasoning cycle can finish.

## Connectors

**Connectors** hold the credentials an agent needs to reach an external service (a mailbox, a CRM, a device, an exchange). Credentials are encrypted with AES-256-GCM envelope encryption, isolated per user and per project, and managed through a secrets vault. Agents never see raw secrets — the runtime resolves them at call time inside the sandbox.

## Bring-your-own-model

Melaya is not tied to one LLM vendor. Each agent picks its own provider and model: Anthropic Claude, OpenAI, Google Gemini, NVIDIA, Ollama, LM Studio, and others. Local providers (Ollama, LM Studio) run on your own hardware via a lightweight runner, so private data never leaves your machine.

## Observability

Every run records its messages, tool calls, tool results, model invocations (with token and cost accounting), and error reasons. Nothing is a black box: you can see exactly what each agent did, what it spent, and why it stopped.

## Device Control (flagship)

Melaya's flagship capability turns an agent into an operator of a **real mobile device**. The agent perceives the screen and acts like a human — tapping, typing, scrolling, and navigating apps — driven by the same agent infrastructure (tools, memory, HITL, scheduling) as any crew. Sensitive actions are HITL-gated, and per-app playbooks give the agent the navigation knowledge it needs. This is what lets an agent do real work in apps that have no API. See [Device Control](./device-control.md).

## The SDKs

Everything above is reachable from **nine official SDKs** (TypeScript/JavaScript, Python, Go, Rust, Java, Kotlin, C#/.NET, Ruby, and PHP), each exposing one identical surface across the platform's planes:

- **Agents** — build and run pipelines, sub-agents, tools, RAG, static context, memory, evaluations, HITL approvals, scheduling/triggers, and templates.
- **Device control** — enroll devices, drive on-screen actions, and run device crews.
- **Platform** — projects, credentials/connectors, teams, and observability.
- **Trading (upcoming)** — the Melaya Labs vertical below: market data, account, paper + live trading, strategies, and backtesting.

The SDKs are thin; the engine, the normalization, and the safety rails live server-side.

---

## Trading vertical (Melaya Labs, upcoming)

Trading is **one application** built on the platform above, rolling out under **Melaya Labs**. It pairs agent trading crews with an in-house market-data and execution engine. The agent concepts are unchanged — these sections just describe the trading-specific pieces. Full guide: [AI agentic trading](./agentic-trading.md).

### The trading engine

Underneath the trading tools is an in-house **Rust engine** that normalizes market data and order routing across 70+ venues: CEX, perpetuals, and prediction markets on one schema. Every venue's quirks (symbol formats, rate limits, settlement suffixes, funding intervals) are absorbed by the engine and presented as one consistent shape. The same engine powers the public API the SDKs wrap: [market data & streaming](./market-data.md), [trading & strategies](./trading.md), and the [venue catalog](./exchanges.md).

### Three ways to trade

Melaya exposes the same trading engine three ways, from safest to most direct:

- **Paper (sim broker)** — orders fill synthetically against the live tape. No venue, no credentials, no capital. The proving ground for a strategy or a crew.
- **Managed strategies** — you launch a strategy and the engine runs the loop, manages server-side stop-loss / take-profit, and gates writes behind approval. Two launchable types via the same API: a **`custom`** Rhai script, or a full **`agent_crew`** trading crew (see [launching a crew](./trading.md#launching-a-trading-crew)). Run it paper, then flip the same definition to live.
- **Direct live trading** — the `trade` plane places, amends, and cancels real orders on a connected exchange, and reads balances, positions, and fills — one normalized call shape across every venue.

The first two need only your `mk_` key; live order placement additionally binds a connected exchange key. For trading crews, approval on **every order** is currently always-on.

### Backtesting

Before any capital moves, a strategy can be replayed against historical data on the same **Rust engine** that runs it live, so the backtest and the live loop share one execution model. A backtest can be a single run, a **grid sweep** over a parameter space, or a **random sample**, and it reports trades, equity curve, and summary statistics. Funding and fees are modeled for perpetuals.

### AI parameter optimization

A strategy's parameters don't have to be hand-tuned. The **AI optimizer** proposes parameter sets, evaluates them by backtest, and surfaces the strongest candidates for you to **approve** before they touch a live run. It's optimization with a human gate, not a black box.
