<!--
SEO note: canonical public security & trust overview for Melaya — encryption,
credential isolation, authentication, tenant isolation, audit logging, HITL,
trading-specific safety rails, supply-chain security, disclosure, and SOC 2
readiness. Written for enterprise buyers and security reviewers; quotable in
isolation. Internal control IDs, file paths, and hostnames are intentionally omitted.
-->

# Security & trust

Melaya runs autonomous agents that can touch money, credentials, and production systems, so security is the product, not an afterthought. This page is the public overview of how Melaya protects your data and bounds what agents can do. It is organized by control domain (mapped internally to the SOC 2 Trust Service Criteria).

> **Reporting a vulnerability:** see [`SECURITY.md`](../SECURITY.md). Email **info@melaya.org** or use GitHub [private vulnerability reporting](https://github.com/melaya-labs/melaya/security/advisories/new). We aim to acknowledge within 72 hours.

## Data protection & encryption

- **Secrets at rest** — exchange keys, model keys, and connector credentials are encrypted with **AES-256-GCM envelope encryption** and stored in an encrypted vault, never in plaintext.
- **In transit** — TLS 1.2+ everywhere.
- **Agents never see raw secrets** — the runtime resolves a credential at call time inside the sandbox and references it by id (`apiKeyId`), so a prompt, a log, or a tool output never carries the secret value.

## Credential & tenant isolation

- **Per-user, per-project isolation** — credentials and data are scoped to the owning user and project; cross-tenant access is denied at the data layer (row-level security), not just in application code.
- **Scoped tools & permissions** — an agent can only call the tools it's been granted, and only with the permissions its role allows. There is no "give the agent everything" mode.
- **Multi-tenant execution** — concurrent runs are isolated so one tenant's approvals, state, or order flow can never leak into another's.

## Authentication & access

- **Passwords** — hashed with bcrypt (work factor 12); plaintext is never stored or logged.
- **Multi-factor authentication** — TOTP (RFC 6238) with recovery codes, **required** before sensitive operations such as connecting an exchange key or generating an API key.
- **Brute-force protection** — fail-closed rate limiting on authentication endpoints.
- **API keys** — prefixed `mk_`, treated as secrets, rotatable from the dashboard. Reads, paper trading, and backtesting need only the key; live order placement additionally binds a connected exchange key.

## Human-in-the-loop & autonomy bounds

- **Approval gates** — any risky tool can require human approval; the run pauses and surfaces the exact arguments to an operator who approves, edits, or rejects. For trading crews, **every order** is gated (always-on today; selective opt-out for fully-autonomous live crews is on the roadmap).
- **Trading safety rails** — per-cycle write caps, per-tier daily order quotas, daily LLM cost caps, drawdown and consecutive-loss circuit breakers, and a paper-soak gate before any crew can trade live. See the [AI agentic trading guide](./agentic-trading.md#11-safety-rails-trading-grade-discipline).
- **Egress & tool allowlists** — a trading crew can't reach exchanges or arbitrary URLs directly; all order flow goes through Melaya's engine, and a server-enforced tool allowlist bounds what any crew can invoke — defense-in-depth against prompt injection.

## Observability & audit

- **Full run traces** — every run records its messages, tool calls, tool results, model invocations (with token and cost accounting), and error reasons. Nothing is a black box.
- **Tamper-evident audit log** — security-relevant events are written to a hash-chained audit log.
- **Trading audit chain** — every approved order produces a complete chain: the approval request, who decided and what they changed, and the resulting fill — so any cycle reconstructs to "what did this crew do, and by whose authority?"

## Bring-your-own-model & data residency

- **Local execution** — local model providers (Ollama, LM Studio) run on your own hardware via a lightweight runner, so prompts and your model key never reach Melaya.
- **Egress posture** — in local-runner mode the only outbound paths are to your model provider and to Melaya's API for the approval gate and audit chain; venue access flows through Melaya's engine, never directly from your machine to an exchange. You whitelist **Melaya's** egress IP on your exchange, not your laptop.

## Change management & supply chain

- **Change control** — security-relevant changes are reviewed before merge, with code owners on security-sensitive areas.
- **Continuous scanning** — nightly secret scanning (full history), dependency auditing, and container image scanning in CI.
- **Verification** — security-critical modules (encryption, JWT handling, MFA, rate limiting) ship with verification checks that assert their invariants.

## Privacy & data handling

- **Retention** — data is retained per category with automated retention sweeps; see the Privacy Policy.
- **Breach notification** — affected customers and the relevant authority are notified within 72 hours of awareness, per GDPR Article 33.
- **Subprocessors** — a current subprocessor list is maintained and published. Third-party model providers you route through Melaya are subprocessors under **your** configuration; the exchanges you connect are independent venues with their own programs.

## Compliance posture

Melaya is **building toward SOC 2 Type II**, with an internal, continuously-maintained control matrix mapping each Trust Service Criterion (CC1–CC9) to the concrete control that implements it. **As of this writing Melaya does not yet hold a third-party SOC 2 Type I / Type II or ISO/IEC 27001 attestation.** This is a readiness posture, stated honestly rather than overclaimed. Enterprise and Citadel-tier customers can request the current control summary and roadmap under NDA.

## See also

- [Concepts](./concepts.md) — connectors, HITL, and the security model in context.
- [AI agentic trading](./agentic-trading.md) — the full safety-rail and reactive-sidecar design.
- [`SECURITY.md`](../SECURITY.md) — vulnerability reporting and API-key handling.
