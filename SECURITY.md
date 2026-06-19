# Security Policy

## Reporting a vulnerability

If you discover a security issue in this repository (the docs or the SDKs) or in the Melaya platform, please report it privately. Do **not** open a public issue.

- Email: **info@melaya.org**
- Or use GitHub's [private vulnerability reporting](https://github.com/melaya-labs/melaya/security/advisories/new).

Please include steps to reproduce, the affected component, and any relevant logs. We aim to acknowledge reports within 72 hours.

## Scope

This repository contains documentation, SDKs, and reference data; it does **not** contain the Melaya engine or platform source. SDK issues (incorrect auth handling, credential leakage, dependency vulnerabilities) are in scope. Platform/API vulnerabilities are also welcome here and will be routed internally.

## Handling your API key

- Melaya API keys are prefixed `mk_`. Treat them as secrets.
- Never commit a key, paste it into an issue, or embed it in client-side code you ship.
- Pass it via an environment variable (`MELAYA_API_KEY`); the SDK reads it from your own configuration, never from this repo.
- Rotate a key immediately if it is exposed (dashboard → Settings → API Keys).
