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

Crews let you decompose a workflow — research, analysis, execution, review — into specialized roles instead of one over-loaded prompt. The flagship example is an [AI agentic trading](./agentic-trading.md) crew: Macro → TA → Risk → Execution personas trading with human approval.

## Scheduling & triggers

Crews don't only run on demand. Each can run on a **cadence**: a **time** schedule (e.g. daily, or every few minutes), an **event** trigger that wakes the crew on a real-world or market condition (e.g. a sharp price drop or a liquidation cascade on a watched symbol), or a **hybrid** of both — a routine timer plus event preemption, whichever fires first.

## Tools

**Tools** are the typed functions an agent can call: web search, code execution, file I/O, market data, order placement, email, calendar, social, RAG retrieval, and an MCP bridge to external tool servers. Each tool has a schema, a permission level, and optional human-approval gating. Agents are scoped to a subset of tools so they can only do what the workflow intends.

## Connectors

**Connectors** hold the credentials an agent needs to reach an external service (an exchange, a mailbox, a CRM). Credentials are encrypted with AES-256-GCM envelope encryption, isolated per user and per project, and managed through a secrets vault. Agents never see raw secrets — the runtime resolves them at call time inside the sandbox.

## Human-in-the-loop (HITL)

Any tool can be marked as **requiring approval**. When an agent tries to call it, the run pauses and surfaces an approval card to the operator with the exact arguments. The operator approves, edits, or rejects. This is how Melaya keeps autonomous runs safe: the agent proposes, the human disposes, and every decision is audited.

## Bring-your-own-model

Melaya is not tied to one LLM vendor. Each agent picks its own provider and model — Anthropic Claude, OpenAI, Google Gemini, NVIDIA, Ollama, LM Studio, and others. Local providers (Ollama, LM Studio) run on your own hardware via a lightweight runner, so private data never leaves your machine.

## RAG

**Retrieval-augmented generation** gives an agent a private knowledge base. Each workflow gets an isolated vector store with hybrid BM25 + dense retrieval, so agents answer from your documents instead of hallucinating.

## The trading engine

Underneath the trading tools is an in-house **Rust engine** that normalizes market data and order routing across 70+ venues. Every venue's quirks — symbol formats, rate limits, settlement suffixes, funding intervals — are absorbed by the engine and presented as one consistent schema. The same engine powers the [public market & trading API](./exchanges.md) that the SDKs wrap.

## Observability

Every run records its messages, tool calls, tool results, model invocations (with token and cost accounting), and error reasons. Nothing is a black box: you can see exactly what each agent did, what it spent, and why it stopped.
