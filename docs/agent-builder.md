# Agent Builder

Agent Builder is Melaya's production platform for defining, running, and observing agent pipelines. A pipeline is a structured configuration containing agents, scoped tools, execution steps, model selection, limits, and optional knowledge or approval settings.

Mobile Device Control is the flagship tool family available to Agent Builder. Melaya Trading is a separate product planned for a later public release.

## Authentication and credentials

Create a Melaya platform key and pass it to the SDK through **MELAYA_API_KEY** or your application's secret manager.

Provider credentials must be configured through Melaya Connectors. Do not add **api_key**, **apiKey**, provider key environment variables, access tokens, client secrets, or private keys to:

- pipeline configuration
- agent configuration
- per-run **env_overrides**
- prompts, RAG documents, or artifacts
- source control

The public pipeline API rejects inline credential fields. At run time the platform resolves authorized Connector credentials without returning plaintext secrets to the SDK client.

## Pipeline configuration

A pipeline has a name and project, one or more agents, and an optional execution tree.

| Field | Purpose |
|---|---|
| **name**, **project** | Pipeline identity and tenant scope. |
| **description** | User-facing purpose. |
| **model_provider**, **model_name** | Model selection; credentials come from Connectors. |
| **agents** | Agent roles, instructions, scoped tools, and optional limits. |
| **steps** | Sequential, parallel, conditional, or loop execution. |
| **loopPolicy** | Optional evaluation policy. |
| **maxCostUsd** | Optional run-wide cost ceiling. |
| **persistent_memory** | Optional cross-run memory. |
| **force_local_runner** | Require the user's runner for the pipeline. |

Each agent should receive only the tools it needs through **agent_tools**. Use **humanApprovalTools** for actions that must pause for user review.

## Create a Device Control agent

Pair and authorize the phone first, as described in [Mobile Device Control](./device-control.md). Then create the pipeline:

~~~ts
import { Melaya } from "@melaya/sdk";

const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! });

await melaya.agents.pipelines.create({
  name: "mobile-inbox-review",
  project: "Operations",
  description: "Summarize unread items in an authorized mobile inbox.",
  model_provider: "anthropic",
  model_name: "claude-sonnet-4-6",
  agents: [{
    name: "mobile-operator",
    role: "Careful mobile operator",
    instruction: "Orient before acting. Read only; never send, publish, or delete.",
    agent_tools: [
      "phone_get_screen_tree",
      "phone_current_app",
      "phone_open_app",
      "phone_click_text",
      "phone_back",
      "phone_wait"
    ],
    maxCostUsd: 1.00
  }],
  steps: [{
    kind: "agent",
    agent: { name: "mobile-operator" }
  }],
  loopPolicy: { mode: "observe_only" },
  maxCostUsd: 1.50
});
~~~

## Run, track, and collect

The canonical TypeScript lifecycle is:

~~~ts
const { run_id, queued } = await melaya.agents.pipelines.run(
  "mobile-inbox-review",
  {
    project: "Operations",
    executionTarget: "cloud-spawn"
  }
);

const { run_ids } = await melaya.agents.pipelines.runIds(
  "mobile-inbox-review"
);

const status = await melaya.agents.pipelines.runStatus(
  "mobile-inbox-review",
  run_id
);

const outputs = await melaya.agents.pipelines.outputs(
  "mobile-inbox-review"
);
~~~

Valid explicit execution targets are:

- **local-runner**
- **cloud-spawn**

Omit **executionTarget** to use the pipeline's configured/default target. Per-run **env_overrides** are for non-secret tuning values only.

The run endpoint returns **{ run_id, queued }**. Run status contains **runId**, **status**, **createdAt**, **executionTarget**, and a structured **cost** object when cost data is available.

To cancel an active run:

~~~ts
await melaya.agents.pipelines.cancelRun(
  "mobile-inbox-review",
  run_id
);
~~~

## Update and delete

Updates send the complete pipeline configuration together with its project:

~~~ts
const config = await melaya.agents.pipelines.get(
  "mobile-inbox-review",
  "Operations"
);

config.description = "Updated description";

await melaya.agents.pipelines.update(
  "mobile-inbox-review",
  config,
  "Operations"
);
~~~

Delete with:

~~~ts
await melaya.agents.pipelines.remove(
  "mobile-inbox-review",
  "Operations"
);
~~~

## Build with AI and templates

**buildWithAI()** can draft a pipeline from a natural-language brief. Review the returned configuration, remove unnecessary tools, confirm Connector selection, set cost limits, and test it before saving.

**instantiateTemplate()** creates a pipeline from a template available to the caller. Project membership and template visibility remain server-enforced.

## Production checklist

- Store provider credentials in Connectors.
- Use a dedicated project and minimum project role.
- Assign the minimum tool set to every agent.
- Require approval for consequential writes.
- Set pipeline and agent cost caps.
- Test the exact pipeline on a paired non-production device.
- Verify **runStatus()**, cancellation, and output collection.
- Confirm logs and artifacts contain no credentials or personal data.
- Revoke unused platform keys and paired devices.

## See also

- [Mobile Device Control](./device-control.md)
- [Security and trust](./security.md)
- [Concepts](./concepts.md)
- [SDK overview](../README.md#official-sdks)