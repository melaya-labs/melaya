# Contributing to Melaya

Thanks for your interest. This repository is the public home for Melaya's **documentation, SDKs, and reference data** — not the platform/engine source.

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

TypeScript (`packages/sdk`):

```bash
cd packages/sdk
npm install
npx tsc --noEmit       # typecheck
MELAYA_API_KEY=mk_... npx tsx ../../examples/typescript.ts   # smoke test
```

Python (`packages/sdk-python`):

```bash
cd packages/sdk-python
pip install -e ".[stream]"
python -m py_compile src/melaya/*.py
MELAYA_API_KEY=mk_... python ../../examples/python.py
```

## Pull requests

1. Fork and branch from `main`.
2. Keep changes focused; one logical change per PR.
3. Make sure the SDK typechecks/compiles and examples still run.
4. Describe what changed and why.

By contributing, you agree your contributions are licensed under [Apache-2.0](./LICENSE).
