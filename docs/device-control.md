# Mobile Device Control

Mobile Device Control is Melaya's flagship capability. It lets an agent pipeline operate a paired Android phone through user-authorized tools: read the visible interface, open an approved app, tap, type, navigate, and wait for the next screen.

The SDK is the management boundary. It pairs devices, lists devices and apps, sets the app allowlist, and associates an active pipeline run. Phone actions are not direct SDK methods; agents execute them through the tools assigned in **agent_tools**.

A Melaya platform key is required for the SDK. Device Control does not require a private API from the target mobile app.

## Requirements

- A Melaya account and platform API key beginning with **mk_**
- The Melaya Android app installed on the target phone
- An Agent Builder plan that permits pipeline execution
- Explicit user authorization for every app the agent may operate
- Provider credentials stored in Melaya Connectors, or a supported local model on the runner

Never put provider keys inside a pipeline config or per-run environment overrides.

## Pair and authorize a phone

~~~ts
import { Melaya } from "@melaya/sdk";

const melaya = new Melaya({ apiKey: process.env.MELAYA_API_KEY! });

const { code, expiresInSeconds } = await melaya.agents.phone.pair();
console.log({ code, expiresInSeconds });
// Enter code in the Melaya Android app before it expires.

const devices = await melaya.agents.phone.listDevices();
const apps = await melaya.agents.phone.listApps();

await melaya.agents.phone.setAllowedApps([
  "com.android.chrome",
  "com.google.android.gm",
]);
~~~

The response and request contracts are:

| Method | Contract |
|---|---|
| **pair()** | Returns **{ code, expiresInSeconds }**. |
| **listDevices()** | Returns the authenticated user's device array. |
| **revokeDevice(deviceId)** | Revokes one device belonging to the caller. |
| **screenTree()** | Returns the current phone-read response. |
| **listApps()** | Returns the installed app array from the phone response. |
| **setAllowedApps(packageNames)** | Converts package names into the server's app-policy request and returns the sync result. |
| **registerActiveRun(runId)** | Associates a pipeline run with the paired phone. |

All nine official SDKs expose the same management lifecycle with language-idiomatic method names.

## Give an agent phone tools

Only assign the tools required for the workflow. Common tools include:

| Tool | Purpose |
|---|---|
| **phone_get_screen_tree** | Read the visible accessibility state before acting. |
| **phone_screenshot** | Read a screen that requires vision. |
| **phone_current_app** | Confirm the foreground application. |
| **phone_list_apps** | Read the apps authorized for agent use. |
| **phone_click_text**, **phone_click_id** | Select a visible control. |
| **phone_input_text**, **phone_clear_text**, **phone_press_enter** | Edit and submit text fields. |
| **phone_open_app**, **phone_open_url** | Open an authorized app or link. |
| **phone_home**, **phone_back**, **phone_recents** | Navigate Android. |
| **phone_wait** | Wait for a UI transition. |
| **phone_batch** | Execute a bounded sequence and return the observed result. |

A minimal pipeline can be created and run through the SDK:

~~~ts
await melaya.agents.pipelines.create({
  name: "mobile-inbox-review",
  project: "Operations",
  description: "Open an authorized inbox and summarize unread items.",
  model_provider: "anthropic",
  model_name: "claude-sonnet-4-6",
  agents: [{
    name: "mobile-operator",
    role: "Careful mobile operator",
    instruction: "Read the screen before every action. Do not send, publish, or delete anything.",
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
  }]
});

const { run_id } = await melaya.agents.pipelines.run(
  "mobile-inbox-review",
  { project: "Operations" }
);

await melaya.agents.phone.registerActiveRun(run_id);
const status = await melaya.agents.pipelines.runStatus(
  "mobile-inbox-review",
  run_id
);
~~~

The provider credential used above must already exist in Connectors. The config contains provider and model selection only.

## Safety model

- Authentication and tenant membership are enforced by the platform.
- Device and app policy is scoped to the authenticated user.
- Agents receive only the tools assigned to them.
- Read and write actions are distinguished so side effects can be reviewed.
- Sensitive publish or send flows should use human approval.
- Users can revoke a device or remove an app from the allowlist.
- A pipeline can be cancelled through the run API.
- SDK errors must be treated as failures; clients must not claim an action succeeded without a successful response.

Use the smallest practical allowlist and tool set. Do not place passwords, API keys, session cookies, or private tokens in prompts, pipeline JSON, artifacts, or per-run overrides.

## Next steps

- [Agent Builder](./agent-builder.md)
- [Security and trust](./security.md)
- [SDK overview](../README.md#official-sdks)