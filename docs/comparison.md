# How Melaya compares

Melaya sits at an unusual intersection: it is both an **agentic automation platform** and a **unified multi-exchange trading API**. Most tools do one or the other. Here is how to think about where it fits.

## vs. general AI-agent builders

General agent builders (no-code flow tools, multi-agent frameworks, assistant builders) are great for prototyping but tend to treat agents as open-ended chatbots wired to a few integrations. Melaya's difference is **operational discipline borrowed from trading**:

- **Scoped tools + permissions** instead of give-the-agent-everything.
- **Encrypted, per-user, per-project credential isolation** instead of shared API keys in plaintext.
- **Token + cost accounting and full tool-call traces** on every run.
- **Human-in-the-loop approval gates** on risky actions, with an audit trail.
- **Bring-your-own-model**, including local models for private data.

If your agents touch money, customer data, or production systems, that discipline is the difference between a demo and something you can run unattended.

## vs. open-source exchange libraries

Client-side libraries that wrap many exchanges put the integration burden on you: you install the library, manage rate limits, handle each venue's reconnect logic, and run it all on your own infrastructure. Melaya inverts this — the normalization and connection lifecycle live in a **hosted Rust engine**, and you talk to **one REST + WebSocket API**:

- One schema across 70+ venues, server-maintained as exchanges change.
- WebSocket streams without you managing reconnects, heartbeats, or per-venue framing.
- Market data *and* trading *and* agentic workflows behind one credential model.

## vs. single-exchange APIs

Going direct to one exchange's API is fine until you need a second venue — then you are maintaining N integrations, N auth schemes, and N sets of quirks. Melaya gives you **one integration that already speaks all of them**, normalized.

## The short version

> Use Melaya when you want agents with **trading-desk discipline**, or a **single normalized API** over many exchanges — and especially when you want both in one place, built by someone who shipped production trading systems for a decade.

See the [FAQ](./faq.md) and [Concepts](./concepts.md) for more, or try it at [melaya.org](https://melaya.org).
