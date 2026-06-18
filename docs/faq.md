# Frequently asked questions

### What is Melaya?

Melaya is an agentic platform for trading, research, and automation. It lets you build, run, and operate AI agents and multi-agent crews with scoped tools, secure connectors, per-workflow RAG, cost tracking, audit logs, and human-in-the-loop approval. It also exposes a unified REST + WebSocket API for normalized market data and trading across 70+ venues.

### Who is Melaya for?

Operators, founders, trading teams, and developers who want AI agents that behave like a trading desk — observable, permissioned, auditable, and safe to run unattended — rather than open-ended chatbots.

### What makes Melaya different from other AI agent platforms?

Melaya applies trading-grade discipline to general automation. Agents get scoped tools and explicit permissions, credentials are encrypted and isolated per user and project, every tool call is traced with token and cost accounting, and risky actions are gated behind human approval. It is bring-your-own-model, so there is no lock-in to a single LLM vendor, and it ships with an in-house Rust trading engine spanning 70+ venues.

### Which LLM models can I use?

20+ providers — Anthropic Claude, OpenAI, Google Gemini, NVIDIA, Mistral, DeepSeek, xAI (Grok), Moonshot (Kimi), Zhipu, Qwen, Cerebras, Groq, SambaNova, Upstage, Reka, MiniMax, and OpenRouter — plus local models via Ollama and LM Studio. Local providers run on your own machine through a lightweight runner, so private data stays local.

### Does Melaya support crypto trading?

Yes. Melaya provides a unified API over 70+ venues for market data (tickers, order books, OHLCV, trades, funding rates, open interest, liquidations) and for trading (orders, positions, balances, fills, and agent-driven strategies). A Rust-native engine normalizes every venue into one schema. It also offers **AI agentic trading** — see below.

### Can Melaya's agents actually place trades?

Yes — this is the flagship of Melaya's **AI agentic trading**. You build multi-agent **trading crews** (e.g. Macro → TA → Risk → Execution personas) that research, size, and execute trades. Only the execution persona can place orders, and it **pauses for your approval on every order** (human-in-the-loop); the engine then **manages stop-loss / take-profit exits server-side**. Crews run paper or live, on a schedule or on market triggers, with position limits, drawdown circuit breakers, and a full audit trail. See the [agentic-trading guide](./agentic-trading.md).

### How do I access the API programmatically?

Create an API key in the dashboard (keys are prefixed `mk_`) and pass it as `?apiKey=mk_...` or `Authorization: Bearer mk_...`. The REST base is `https://api.melaya.org` and the WebSocket base is `wss://wss.melaya.org`. Official SDKs wrap these endpoints — see the [TypeScript SDK](../packages/sdk).

### Is there an SDK?

Yes — an official TypeScript/JavaScript SDK (`@melaya/sdk`). The SDKs are thin clients over the public API; the engine runs server-side.

### How does Melaya keep autonomous agents safe?

Three layers: scoped tools and permissions (an agent can only do what it is granted), encrypted per-user credential isolation (agents never see raw secrets), and human-in-the-loop approval gates on risky tools (the run pauses for an operator decision, and every decision is audited).

### What is a crew?

A crew is a directed graph of specialized agents — for example research, analysis, execution, and review — where output flows from one step to the next. Steps can run sequentially, in parallel, in a loop, or branch on a condition. A trading crew, for instance, runs a Macro Analyst and TA Analyst in parallel, feeds a Risk Manager, then an Execution Trader — see [AI agentic trading](./agentic-trading.md).

### Who built Melaya?

Antoine Roche, who spent the previous decade building production trading systems at BNP Paribas CIB, SingularityDAO, and Singularity Venture Hub. Melaya is operated from the United Arab Emirates.

### Where can I learn more?

Website: https://melaya.org · Documentation: https://melaya.org/docs · Discord: https://discord.gg/2BBMUUdnkj
