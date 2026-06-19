# Contributing to Melaya

Thanks for your interest. This repository is the public home for Melaya's **documentation, SDKs, and reference data**, not the platform/engine source.

## What we welcome

- **SDK fixes & improvements** — bug fixes, new wrapped endpoints, types, examples, tests.
- **Docs** — clarifications, corrections, new guides, fixes to broken links or examples.
- **The awesome list** — relevant, vendor-neutral additions to [`docs/awesome-agentic-trading.md`](./docs/awesome-agentic-trading.md).
- **Bug reports** — anything inaccurate, broken, or out of date.

## Ground rules

- **No secrets, ever.** Don't include API keys (`mk_...`), tokens, internal hostnames/IPs, or credentials in code, issues, or PRs.
- **Public surface only.** The SDKs and docs describe Melaya's public API. Don't add internal/private endpoints or proprietary engine/strategy details.
- **Keep claims code-true.** Numbers and capabilities in the docs should reflect the real product.

## Developing the SDKs

Every SDK lives under [`packages/`](./packages) and ships a smoke test under its
`e2e/` directory (Rust uses `examples/`, Cargo's convention). Export your API key
as `MK=mk_...` to run a smoke test against the live API.

**TypeScript** — [`packages/sdk`](./packages/sdk)
```bash
cd packages/sdk && npm install && npx tsc --noEmit
MK=mk_... node e2e/smoke.mjs
```

**Python** — [`packages/sdk-python`](./packages/sdk-python)
```bash
cd packages/sdk-python && pip install -e ".[stream]"
python -m py_compile src/melaya/*.py
MK=mk_... python e2e/smoke.py
```

**Go** — [`packages/sdk-go`](./packages/sdk-go)
```bash
cd packages/sdk-go && go build ./...
cd e2e && go mod tidy && MK=mk_... go run .
```

**Rust** — [`packages/sdk-rust`](./packages/sdk-rust)
```bash
cd packages/sdk-rust && cargo build
MK=mk_... cargo run --example e2e
```

**Java** (JDK 21+) — [`packages/sdk-java`](./packages/sdk-java)
```bash
cd packages/sdk-java && gradle build
MK=mk_... gradle run
```

**Kotlin** (JDK 21+) — [`packages/sdk-kotlin`](./packages/sdk-kotlin)
```bash
cd packages/sdk-kotlin && gradle build
MK=mk_... gradle run
```

**C# / .NET** (.NET 8+) — [`packages/sdk-csharp`](./packages/sdk-csharp)
```bash
cd packages/sdk-csharp && dotnet build
MK=mk_... dotnet run --project e2e
```

**Ruby** — [`packages/sdk-ruby`](./packages/sdk-ruby)
```bash
cd packages/sdk-ruby && ruby -Ilib -e 'require "melaya"'   # loads (stdlib only)
MK=mk_... ruby e2e/smoke.rb
```

**PHP** (8.1+) — [`packages/sdk-php`](./packages/sdk-php)
```bash
cd packages/sdk-php && composer install   # or rely on the bundled autoload.php
MK=mk_... php e2e/smoke.php
```

All nine SDKs expose the same surface (market data, account, paper + live
trading, backtesting, and public + private streaming) and are validated by their
e2e smoke. Live order-placement methods (`trade.createOrder`, `cancelOrder`, …)
move real funds, so test with the paper `sim` broker or a `dryRun` strategy.

## Pull requests

1. Fork and branch from `main`.
2. Keep changes focused; one logical change per PR.
3. Make sure the SDK typechecks/compiles and examples still run.
4. Describe what changed and why.

By contributing, you agree your contributions are licensed under [Apache-2.0](./LICENSE).
