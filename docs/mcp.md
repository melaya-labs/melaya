# MCP Server

Melaya is a remote [Model Context Protocol](https://modelcontextprotocol.io) server. Connect it once and the AI assistant you already use can operate your Android phone, drive a browser you have connected, author and run agent pipelines, read the services you have authorised, and tell you what all of it did.

The endpoint is:

~~~
https://api.melaya.org/mcp
~~~

MCP is a vendor-neutral standard, so this one endpoint works in every host that speaks it. Nothing is installed and no SDK is required.

![Melaya Device Control](https://melaya.org/blog/device-control/control-hero.png)

## Requirements

- A Melaya account. Free to create at [melaya.org](https://melaya.org).
- An MCP-capable assistant (see **Connecting**).
- For phone control: an Android phone (Android 8 or newer) with the Melaya app. There is no iOS build, and Android cannot be replaced by a simulator here: the agent drives a real device through the accessibility service.
- For browser control: the Melaya extension on Chrome or Edge, paired once in your own browser.
- For autonomous agent runs: Node 18+, Python 3.11+, and a signed-in CLI on the machine hosting the Melaya runner.

## Connecting

Authentication is OAuth 2.1 with PKCE. You are sent to a Melaya consent page, you choose which permissions to grant, and the assistant receives a scoped access token. Your Melaya password is never shared, and neither are the credentials for any service you have connected.

**Claude Code**

~~~bash
claude mcp add --transport http melaya https://api.melaya.org/mcp
~~~

**Claude** (claude.ai, Desktop, mobile) — Settings → Connectors → Add custom connector → paste the endpoint.

**ChatGPT** — Settings → Connectors → Developer mode → add the endpoint.

**Mistral Le Chat** — Connectors → Add custom MCP connector → paste the endpoint.

**Cursor, VS Code, Zed, Cline, Goose, Windsurf and other MCP clients** — add it wherever the client accepts a remote MCP server URL.

## Permissions

Eight scopes, one per domain. You grant them individually and can decline any of them.

| Scope | What it allows |
|---|---|
| `melaya:read` | Your workspace: pipelines, runs, traces, evaluations, and the approvals waiting on you |
| `melaya:platform` | Your account and plan: tier, usage against limits, subscription |
| `melaya:runner` | Set up and check the Melaya runner on your computer, including its credential |
| `melaya:phone` | Operate your paired Android phone, inside apps you have allow-listed |
| `melaya:browser` | Operate a browser you have connected, on sites you have allowed |
| `melaya:pipelines` | Create, edit, schedule, run and cancel agent pipelines |
| `melaya:connectors` | Read data from services you have connected. Read only: nothing can be sent or changed |
| `melaya:team` | Read who has access to your projects, and invite people you name |
| `offline_access` | Stay connected without signing in again |

**The scopes do real work.** The tool list your assistant receives is filtered to what you granted, so a connection made for phone control alone sees 21 tools rather than all 73. If a capability is missing, it is because you declined it, not because Melaya lacks it.

Disconnecting in Melaya settings immediately revokes the connection's ability to renew itself. The access token it already holds is self-contained and keeps working until it expires, which is at most one hour.

## What you can ask for

Once connected, ask in plain language.

**Set everything up**

> Connect my phone to Melaya and tell me what's left to do.

The assistant calls `melaya_setup_status`, which reports what is ready and what is missing, and returns the exact command or link for each gap. Where the assistant has shell access on your own machine it runs those commands itself; on a hosted assistant it hands them to you.

**Work in a phone app**

> Open Instagram on my phone, go through my unread DMs, and summarise who's waiting on a reply.

The assistant reads the screen, navigates, and reports back. It reads before every action and re-reads after, so it acts on what is actually on screen rather than on an assumption.

![The agent working, with a live step trace and a stop control](https://melaya.org/blog/july-2026/device-agent-overlay.png)

Melaya keeps navigation notes for common apps, so the agent arrives knowing where things are instead of exploring.

![App playbooks: navigation map, stable control ids, canonical step sequence](https://melaya.org/blog/july-2026/app-playbooks.png)

**Work on a desktop site**

> Attach my browser and pull this month's invoices out of the supplier portal.

Same read-act-verify loop, different limb. You pick the tab; the model never does.

**Debug a page instead of guessing**

> This page is slow and the submit button does nothing. What's wrong?

The assistant can read the page's network activity, its console, and a performance
diagnosis. Not raw panels: a report. The network view summarises by status and type
before you drill into one request; the performance view ranks causes with the file
and the number behind each, so the answer is "hero.webp wasn't requested until 3.1s
because a lazy-loaded script discovers it" rather than a wall of timings.

Credential values are redacted at capture, before anything reaches the assistant. You
see that an `Authorization` header was sent and its shape, never its contents, which
is what you actually need when debugging a 401.

**Build something reusable**

> Turn that into a pipeline that runs every weekday at 8am.

The assistant can list the template library and instantiate a validated one, or author a config from scratch and validate it before saving. Recurring schedules, model choice per agent and approval gates are all part of the config.

**Read your own systems**

> What did the customer say in the last email thread about order 4471?

Read-only, through a service you connected. There is no write path.

**Find out what happened**

> The overnight run failed. What went wrong and what did it cost?

**Take access away**

> Stop Melaya from touching anything on my phone.

## What the agent cannot do

Three limits are enforced on the device itself, not on the server, so no prompt and no agent instruction can move them.

**It only touches apps and sites you allow-list.** Everything else is refused at the device. The assistant can hand access back, narrowing the list or clearing it, but it cannot add to it. Granting is something you do in the Melaya app or on the phone.

![The allow-list, in the Melaya app](https://melaya.org/blog/device-control/app-permissions.jpeg)

That asymmetry is deliberate. The agent reads text off your screen, and that text can be written by anyone: a message, a comment, a web page. A boundary the agent could widen in response to what it reads would not be a boundary at all.

**Publishing and paying always ask you.** Posting a comment, creating a post, or anything that looks like a payment stages an approval card and waits. You see the exact text before it goes out.

![An approval card, showing the exact text before it publishes](https://melaya.org/blog/july-2026/on-device-approval.png)

Approvals reach you even when the phone is locked.

![An approval on a locked phone](https://melaya.org/blog/july-2026/sleeping-phone.png)

**There is a STOP control.** On the phone overlay and in the Melaya app. It halts everything immediately, across every agent and every connected assistant.

Password fields are excluded from screen reads.

**Approvals are listed here, never decided here.** `melaya_approval_list` shows what is waiting; nothing on this surface can clear it. The gate exists to put a human between an agent and a consequential action, and the caller here is a model reading untrusted content. You approve in the Melaya app or on your phone.

## Privacy

Screen and page content read during a run is sent to Melaya and to whichever model you have selected, for the duration of that run. The [privacy policy](https://melaya.org/en/legal/privacy) covers collection, retention and deletion in full.

One credential does cross the boundary, and it is worth naming: if you set up the optional local runner, the command the assistant gives you contains a runner token. It is valid for 7 days, revocable in Melaya settings or with `melaya_runner_revoke`, and unavoidable because the runner is started from a command line. On a hosted assistant that command will appear in your conversation history.

Nothing else does. Provider and connector credentials are resolved server-side at run time and never reach the assistant.

## Tools

Seventy-three, grouped by domain. Every one declares whether it is read-only or makes changes, so your assistant can ask before anything consequential.

**Setup and account** — `melaya_setup_status`, `melaya_account_whoami`, `melaya_account_usage`, `melaya_account_subscription`, `melaya_model_list`

**Local runner** — `melaya_runner_setup`, `melaya_runner_status`, `melaya_runner_revoke`

**Phone: pairing and permissions** — `melaya_phone_pair`, `melaya_phone_devices`, `melaya_phone_revoke_device`, `melaya_phone_apps`, `melaya_phone_restrict_apps`

**Phone: reading** — `melaya_phone_status`, `melaya_phone_screen`, `melaya_phone_screenshot`, `melaya_phone_current_app`, `melaya_phone_playbook`

**Phone: acting** — `melaya_phone_open`, `melaya_phone_click`, `melaya_phone_tap`, `melaya_phone_swipe`, `melaya_phone_scroll`, `melaya_phone_type`, `melaya_phone_navigate`, `melaya_phone_batch`, `melaya_phone_publish`, `melaya_phone_wait`, `melaya_phone_stop`

**Browser** — `melaya_browser_status`, `melaya_browser_pair`, `melaya_browser_attach`, `melaya_browser_screen`, `melaya_browser_screenshot`, `melaya_browser_get_text`, `melaya_browser_navigate`, `melaya_browser_click`, `melaya_browser_type`, `melaya_browser_scroll`, `melaya_browser_tabs`, `melaya_browser_batch`, `melaya_browser_restrict_origins`, `melaya_browser_stop`

**Browser: DevTools** — `melaya_browser_network`, `melaya_browser_console`, `melaya_browser_performance`

**Pipelines** — `melaya_pipeline_list`, `melaya_pipeline_get`, `melaya_pipeline_registry`, `melaya_pipeline_templates`, `melaya_pipeline_preview`, `melaya_pipeline_save`, `melaya_pipeline_from_template`, `melaya_pipeline_delete`, `melaya_pipeline_schedule`, `melaya_pipeline_run`, `melaya_run_phone_agent`

**Runs and quality** — `melaya_run_status`, `melaya_run_inspect`, `melaya_run_diagnosis`, `melaya_run_cancel`, `melaya_eval_report`, `melaya_agent_memory`, `melaya_approval_list`

**Connected services** — `melaya_connector_list`, `melaya_connector_test`, `melaya_connector_connect`, `melaya_connector_tools`, `melaya_connector_call`

**Team** — `melaya_team_list`, `melaya_team_invite`

**Platform gateway** — `melaya_gateway_list`, `melaya_gateway_call`

### Two notes on the connector tools

They are **read-only, structurally**. Not by policy, and not by an approval prompt you could click through: the write path is blocked in two independent places, so a write stays blocked even if Melaya's own tool catalog is out of date. If a task needs something sent or changed in a connected service, the answer is a Melaya pipeline that includes it.

And a naming warning if you also use the Melaya SDK: **"connectors" means something different there.** In the SDK, `connectors` is project credential storage. Here, `melaya:connectors` is reading data from services you already connected. This surface cannot store, read or delete a credential at all.

## What is deliberately not here

Recorded so its absence is not mistaken for an oversight.

**Trading.** Melaya's trading surface writes against live exchange keys and moves real positions. It is not exposed over MCP at any scope, and the exclusion is enforced by a test rather than by convention.

**Administration.** There is no honest way to write "act with platform administrator authority" on a screen a third-party client renders. An admin using this connection sees their own account, exactly like everyone else.

**Credentials of any kind.** No tool reads, writes or mints a credential value. The one exception is the runner token, named above, because a runner starts from a command line.

**Anything irreversible about your account**: billing changes, data export, account deletion, MFA.

## Troubleshooting

**"No paired phone is reachable."** Unlock the phone and open the Melaya app once. If it still does not connect, check that the Melaya accessibility service is enabled in Android Settings and that battery optimisation is disabled for the app.

**"That app is not on the allow-list."** Add it yourself in the Melaya app or on the phone. The assistant cannot, by design.

**"The user has no allowed sites configured."** Browser control needs at least one allowed origin, added on the Melaya browser page. There is no "all sites" option from here; that choice only exists on a screen that can ask you for it properly.

**An action needs approval.** Approve or reject it on the phone or in the Melaya app. The assistant will wait, and can be told to retry the same action afterwards.

**"No local runner is connected."** Only autonomous agent runs need it. Ask your assistant to set it up, or run `npx @melaya/runner@latest --token=<your token>` on the machine that should host it. It runs on your own computer, never on a server.

**Claude Code credentials not found.** Run `claude` once in a terminal on the runner machine to sign in, then restart the runner. No API key is involved.

**A saved pipeline is missing a field you set.** The builder ignores fields it does not recognise rather than rejecting them. Use `melaya_pipeline_preview` before saving and read the pipeline back after; the assistant should do both without being asked.

## Related

- [Mobile Device Control](./device-control.md) — the underlying capability, and how to drive it from the SDKs
- [Agent Builder](./agent-builder.md) — pipelines, tools, models and approvals
- [Security](./security.md) — credential handling and tenancy
