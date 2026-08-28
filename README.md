# Melaya

> **Build high-trust AI agents that can do real work.** Melaya combines a visual Agent Builder with flagship Mobile Device Control: bring your preferred cloud or local model, give each agent only the tools it needs, and require human approval for consequential actions. Melaya Trading is planned for a later public release.

[Website](https://melaya.org) · [Documentation](https://melaya.org/documentation) · [Discord](https://discord.gg/2BBMUUdnkj)

[![SDKs](https://img.shields.io/badge/SDKs-9_languages-6E56CF)](#official-sdks)
[![License](https://img.shields.io/badge/license-Apache--2.0-blue)](./LICENSE)

This public repository contains the official SDKs and public developer documentation. It does not contain the proprietary Melaya platform, agent runtime, Device Control service, or trading engine.

## The products

1. **Agent Builder — available now.** Compose agents, models, scoped tools, knowledge, evaluations, schedules, cost limits, and human approvals as a pipeline.
2. **Mobile Device Control — flagship, available now.** Pair an Android phone and let an authorized agent read and operate approved apps through the visible interface.
3. **Melaya Trading — coming later.** Trading SDK namespaces — market data, streaming, strategies, backtesting, simulation, and live trading endpoints — are included as preview surface only. They are not a generally available production commitment, and live-trading endpoints must not be used with real funds.

Current generated catalogs contain more than **1,500 scoped tools**, **100+ specialized subagents**, and **20+ model providers**, including supported local-model options. Runtime catalog endpoints remain the source of truth.

## Why teams use Melaya

- Minimum-tool agent permissions instead of an unlimited toolbox
- Encrypted Connectors instead of credentials in prompts or pipeline JSON
- Tenant-scoped projects and API keys
- Human approval for sensitive tools
- Run status, traces, costs, evaluations, outputs, and cancellation
- Cloud execution or a user-controlled local runner
- Mobile workflows for apps that do not expose a suitable vendor API
- Nine official SDKs with the same public Agent Builder and Device Control lifecycle

## Quick start: pair a phone

~~~ts
import { Melaya } from "@melaya/sdk";

const melaya = new Melaya({
  apiKey: process.env.MELAYA_API_KEY!
});

const { code, expiresInSeconds } =
  await melaya.agents.phone.pair();

console.log({ code, expiresInSeconds });

const devices = await melaya.agents.phone.listDevices();
const apps = await melaya.agents.phone.listApps();

await melaya.agents.phone.setAllowedApps([
  "com.android.chrome"
]);
~~~

A Melaya platform key is required. “No app API required” means Device Control operates the target app through its user interface; it does not mean the Melaya SDK is unauthenticated.

## Quick start: run an agent pipeline

Configure provider credentials through Melaya Connectors first. Never include a provider key in pipeline configuration or per-run overrides.

~~~ts
await melaya.agents.pipelines.create({
  name: "mobile-review",
  project: "Operations",
  model_provider: "anthropic",
  model_name: "claude-sonnet-4-6",
  agents: [{
    name: "mobile-operator",
    role: "Careful mobile operator",
    instruction: "Read before acting. Never send, publish, or delete.",
    agent_tools: [
      "phone_get_screen_tree",
      "phone_current_app",
      "phone_open_app",
      "phone_click_text",
      "phone_back",
      "phone_wait"
    ]
  }],
  steps: [{
    kind: "agent",
    agent: { name: "mobile-operator" }
  }],
  maxCostUsd: 1.00
});

const { run_id } = await melaya.agents.pipelines.run(
  "mobile-review",
  { project: "Operations" }
);

await melaya.agents.phone.registerActiveRun(run_id);

const status = await melaya.agents.pipelines.runStatus(
  "mobile-review",
  run_id
);
~~~

See [Agent Builder](./docs/agent-builder.md) and [Mobile Device Control](./docs/device-control.md) for the complete lifecycle.

## Official SDKs

| Language | Package |
|---|---|
| TypeScript / JavaScript | [@melaya/sdk](./packages/sdk) |
| Python | [melaya](./packages/sdk-python) |
| Go | [melaya-go](./packages/sdk-go) |
| Rust | [melaya](./packages/sdk-rust) |
| Java | [org.melaya:melaya-sdk](./packages/sdk-java) |
| Kotlin | [org.melaya:melaya-sdk-kotlin](./packages/sdk-kotlin) |
| C# / .NET | [Melaya.SDK](./packages/sdk-csharp) |
| Ruby | [melaya](./packages/sdk-ruby) |
| PHP | [melaya/sdk](./packages/sdk-php) |

The supported public surface includes authentication, projects, Connectors, credentials, pipelines, templates, Agent Builder tools, Device Control management, HITL, evaluations, events, billing, account operations, runner management, team management, MFA, the assistant, bug reports, and the memory graph.

Internal operator APIs are intentionally absent.

## Security rules for SDK users

- Keep **MELAYA_API_KEY** in a secret manager or server environment.
- Store model and external-service credentials in Connectors.
- Do not put secrets in source, prompts, RAG documents, artifacts, or **env_overrides**.
- Give agents the smallest practical tool and app allowlists.
- Require human approval for consequential writes.
- Validate errors and final state before reporting success.
- Revoke unused platform keys and paired devices.
- Redact credentials from proxy, APM, and WebSocket query logs.

See [Security and trust](./docs/security.md).

## Documentation

- [Agent Builder](./docs/agent-builder.md)
- [Mobile Device Control](./docs/device-control.md)
- [MCP Server](./docs/mcp.md)
- [Concepts](./docs/concepts.md)
- [Security and trust](./docs/security.md)
- [FAQ](./docs/faq.md)
- [Comparison](./docs/comparison.md)
- [Melaya Trading preview](./docs/agentic-trading.md)

Full product documentation and interactive API reference: [melaya.org/documentation](https://melaya.org/documentation).

## Trading preview status

Melaya Trading is planned for a later public release. The preview namespaces cover market data, streaming, strategies, backtesting, simulation, and live trading endpoints — all preview; live-trading endpoints must not be used with real funds. Preview namespace and design documentation may change or remain unavailable by account, region, venue, or release stage. They are not authorization to trade real funds and are not covered by the current Agent Builder and Device Control readiness statement.

## License

The SDK code and public documentation are licensed under [Apache-2.0](./LICENSE). The hosted platform and engines are proprietary and are not included in this repository.

Third-party names and logos are used only for identification and do not imply endorsement.
