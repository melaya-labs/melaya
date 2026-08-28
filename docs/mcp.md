# MCP Server

Melaya is a remote [Model Context Protocol](https://modelcontextprotocol.io) server. Connecting it lets an AI assistant operate your paired Android phone, run your Melaya agent pipelines, and read your account usage and limits — from whatever assistant you already use.

The endpoint is:

~~~
https://api.melaya.org/mcp
~~~

MCP is a vendor-neutral standard, so this one endpoint works in every host that speaks it. Nothing is installed and no SDK is required.

## Requirements

- A Melaya account. Free to create at [melaya.org](https://melaya.org).
- An MCP-capable assistant (see **Connecting** below).
- For phone control: an Android phone (Android 8 or newer) with the Melaya app.
- For autonomous agent runs: Node 18+, Python 3.11+, and the `claude` CLI signed in on the machine that will host the Melaya runner.

There is no iOS build. Android cannot be replaced by a simulator here — the agent drives a real device through the accessibility service.

## Connecting

Authentication is OAuth 2.1 with PKCE. You are sent to a Melaya consent page, you choose which permissions to grant, and the assistant receives a scoped access token. Your Melaya password is never shared, and neither are the credentials for any connector you have authorized.

**Claude Code**

~~~bash
claude mcp add --transport http melaya https://api.melaya.org/mcp
~~~

**Claude (claude.ai, Desktop, mobile)** — Settings → Connectors → Add custom connector → paste the endpoint.

**ChatGPT** — Settings → Connectors → Developer mode → add the endpoint. Available on Team, Business, Enterprise and Education plans.

**Mistral Le Chat** — Connectors → Add custom MCP connector → paste the endpoint.

**Cursor, VS Code, Zed, Cline, Goose, Windsurf and other MCP clients** — add it wherever the client accepts a remote MCP server URL.

## Permissions

You grant these individually on the consent screen, and can decline any of them:

| Scope | What it allows |
|---|---|
| `melaya:read` | Read your account, plan and tier, usage and limits, pipelines, runs and paired devices |
| `melaya:device` | Read your phone screen and act on it; manage pairing and the app allow-list |
| `melaya:agents` | Create and run agent pipelines on your behalf, and cancel runs you started |
| `offline_access` | Stay connected without signing in again |

Disconnecting in Melaya settings immediately revokes the connection's ability to renew itself. The access token it already holds is a self-contained credential and keeps working until it expires, which is at most one hour.

## Example prompts

Once connected, ask in plain language.

**Set everything up**

> Connect my phone to Melaya and tell me what's left to do.

The assistant calls `melaya_setup_status`, which reports what is ready and what is missing, and returns the exact command or link for each gap. Where the assistant has shell access on your own machine it runs those commands itself; on a hosted assistant it hands them to you.

**Work in an app**

> Open Instagram on my phone, go through my unread DMs, and summarise who's waiting on a reply.

The assistant reads the screen, navigates, and reports back. It reads before every action and re-reads after, so it acts on what is actually on screen rather than on an assumption.

**Delegate something long-running**

> Every morning, check my WhatsApp for anything from the ops group and summarise it.

This launches an autonomous Melaya agent on your own machine, using your own Claude subscription, that keeps working after the conversation ends.

**Check your account**

> How close am I to my Melaya plan limits this month?

**Take access away**

> Stop Melaya from touching anything on my phone.

## What the agent cannot do

Three limits are enforced on the phone itself, not on the server, so no prompt and no agent instruction can move them.

**It only touches apps you allow-list.** Everything else is refused at the device. The assistant can hand access back — it can narrow the list or clear it entirely — but it cannot add to it. Granting an app is something you do in the Melaya app or on the phone.

That asymmetry is deliberate. The agent reads text off your screen, and that text can be written by anyone: a message, a comment, a web page. A boundary the agent could widen in response to what it reads would not be a boundary at all.

**Publishing and paying always ask you.** Posting a comment, creating a post, or anything that looks like a payment stages an approval card on the phone and waits. You see the exact text before it goes out.

**There is a STOP control.** On the phone overlay and in the Melaya app. It halts everything immediately, across every agent and every connected assistant.

Password fields are excluded from screen reads.

## Privacy

Screen content read from your phone during a run is sent to Melaya and to whichever model you have selected, for the duration of that run. The [privacy policy](https://melaya.org/en/legal/privacy) covers collection, retention and deletion in full.

One credential does cross the boundary, and it is worth naming: if you set up the optional local runner, the command the assistant gives you contains a runner token. It is valid for 7 days, revocable in Melaya settings, and unavoidable because the runner is started from a command line. On a hosted assistant that command will appear in your conversation history.

Nothing else does. Provider and connector credentials are resolved server-side at run time and never reach the assistant.

## Tools

Thirty-five, grouped by what they do. Every one declares whether it is read-only or makes changes, so your assistant can ask before anything consequential.

**Setup and account** — `melaya_setup_status`, `melaya_whoami`, `melaya_usage`, `melaya_subscription`, `melaya_runner_setup`, `melaya_runner_status`

**Device pairing and permissions** — `melaya_phone_pair`, `melaya_phone_devices`, `melaya_phone_revoke_device`, `melaya_phone_apps`, `melaya_phone_restrict_apps`

**Reading the phone** — `melaya_phone_status`, `melaya_phone_screen`, `melaya_phone_screenshot`, `melaya_phone_current_app`, `melaya_phone_playbook`

**Acting on the phone** — `melaya_phone_open`, `melaya_phone_click`, `melaya_phone_tap`, `melaya_phone_swipe`, `melaya_phone_scroll`, `melaya_phone_type`, `melaya_phone_navigate`, `melaya_phone_batch`, `melaya_phone_publish`, `melaya_phone_wait`, `melaya_phone_stop`

**Agents and pipelines** — `melaya_run_phone_agent`, `melaya_pipelines`, `melaya_run_pipeline`, `melaya_run_status`, `melaya_cancel_run`, `melaya_pending_approvals`

**Platform toolkit** — `melaya_tools`, `melaya_call_tool`

`melaya_pending_approvals` is read-only and has no counterpart: an assistant can show you what is waiting for approval but cannot approve it. The point of the gate is that a person decides.

The platform toolkit reaches pipelines, runs, traces, agent memory, RAG stores and cost data. It does **not** reach third-party connectors you have authorized — those are driven by Melaya agents, so to use one, run a pipeline that includes it.

## Troubleshooting

**"No paired phone is reachable."** Unlock the phone and open the Melaya app once. If it still does not connect, check that the Melaya accessibility service is enabled in Android Settings and that battery optimisation is disabled for the app.

**"That app is not on the allow-list."** Add it yourself in the Melaya app or on the phone. The assistant cannot, by design.

**An action needs approval.** Approve or reject it on the phone. The assistant will wait.

**"No local runner is connected."** Only autonomous agent runs need it. Ask your assistant to set it up, or run `npx @melaya/runner@latest --token=<your token>` on the machine that should host it. It runs on your own computer, never on a server.

**Claude Code credentials not found.** Run `claude` once in a terminal on the runner machine to sign in, then restart the runner. No API key is involved.

## Related

- [Mobile Device Control](./device-control.md) — the underlying capability, and how to drive it from the SDKs
- [Agent Builder](./agent-builder.md) — pipelines, tools, models and approvals
- [Security](./security.md) — credential handling and tenancy
