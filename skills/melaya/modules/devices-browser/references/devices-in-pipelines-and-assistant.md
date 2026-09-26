# Phone and browser in pipelines, and the Melaya Assistant

## 1. Which surface for which job

| Surface | What it is | Best for | Survives the chat | Needs |
|---|---|---|---|---|
| Direct MCP driving (`melaya_phone_*`, `melaya_browser_*`) | You, the AI assistant in this conversation, operate the device step by step | Short, supervised, exploratory tasks; showing the user how it works | No | Paired phone or extension |
| `melaya_run_phone_agent` | A Melaya agent that works on the phone on its own | Long or repetitive phone tasks started from a chat | Yes | Runner with Claude Code signed in |
| Device Control pipeline | A saved pipeline whose agent uses the phone | Recurring or scheduled phone jobs, jobs that feed other steps | Yes | Paired phone; a model |
| Browser Control pipeline | A saved pipeline whose agent uses a dedicated browser on the runner | Recurring or scheduled browser jobs | Yes | Runner connected |
| `browser_read` inside any pipeline | A read-only tool that opens pages in the user's own browser | Reading JavaScript-heavy or bot-protected pages as a step of a larger pipeline | Yes | Extension paired and set to All tabs |
| Melaya Assistant | The chat copilot inside the Melaya app | One-off conversational work, questions about the user's own pipelines and runs, quick phone tasks | Per conversation | A model connected |
| Extension side panel ("cockpit") | A chat inside the browser that drives the attached tab | Conversational browser work without leaving the page | Per conversation | Extension paired |

Rule of thumb: if the user will want it again, on a schedule, or with outputs saved somewhere, make it a pipeline. If it is a one-off, drive it directly or use the Assistant.

## 2. Device Control pipelines (phone)

### How to create one

Option A, recommended: from a template.
1. `melaya_pipeline_templates` with `search: "device"` (category "Device Control"). Examples include a mobile app UI/UX audit, casual game playing by vision, and social community engagement on a specific app.
2. Read the template's `builds` summary with the user, then `melaya_pipeline_from_template` (see `../../../modules/projects-templates/GUIDE.md`).
3. Replace every `[START EDIT ME]` block with concrete values for this user. Real pipelines never keep EDIT ME blocks.

Option B: from the Assistant. After a successful phone task in the Melaya Assistant, the user can press "save as a reusable Device Control pipeline". The saved pipeline opens in Device Control.

Option C: authored from scratch (see `../../../modules/pipeline-authoring/GUIDE.md`), with the rules below.

### Config rules for phone agents

- **Tools.** Put the full phone toolkit in the agent's `agent_tools`. Verify each id with `melaya_pipeline_registry` search "phone". The standard set:
  - read: `phone_get_screen_tree`, `phone_screenshot`, `phone_current_app`, `phone_app_playbook`, `phone_list_apps`, `phone_wait`;
  - act: `phone_open_app`, `phone_open_url`, `phone_click_text`, `phone_click_id`, `phone_tap_id`, `phone_tap`, `phone_double_tap`, `phone_long_press`, `phone_swipe`, `phone_scroll`, `phone_drag_hold`, `phone_input_text`, `phone_clear_text`, `phone_press_enter`, `phone_batch`, `phone_home`, `phone_back`, `phone_recents`, `phone_notifications`;
  - publish (always approved on the phone in `safe`): `phone_post_comment`, `phone_create_post`;
  - ask the user a question on the phone: `phone_ask_user` (drop it when the pipeline is autonomous; an autonomous run should not stop to ask).
  A toolkit missing `phone_click_text` forces coordinate taps and causes mis-taps.
- **Autonomy is `hitl_mode`.** Set the pipeline's `hitl_mode` to `safe` (default), `payments_only` or `autonomous`. It decides whether the phone shows approval cards for publishing, payments and risky taps. In the Device Control page this is the **Autonomy** selector on the pipeline row.
- **Leave `human_approval_tools` empty** on phone agents. The phone gates itself; listing phone tools there would double the gate. The on-device gate does NOT cover everything: instruct the agent to ask (with `phone_ask_user`) before spending money, changing account settings, deleting data or opening sensitive personal information.
- **No runner required.** Phone pipelines can run in the cloud; the phone is reached through Melaya. The runner is needed only if the pipeline's model lives on the runner.
- **Instructions should say:** operate only the approved apps; read before acting; prefer tapping by label or id; use `phone_batch` only for short known flows and never for publishing; publish only through `phone_post_comment` / `phone_create_post`; human-paced actions; stop immediately on cancel; end with a short summary of what changed. Keep app navigation knowledge out of the prompt: the app's playbook is delivered to the agent automatically.
- **Model.** Use a vision-capable model if the app shows images, games or unlabelled icons. Use a stronger model for ambiguous or sensitive flows.
- **Approved apps are still required** at run time; a run fails clearly with "app not allowed" otherwise.

### Validate before relying on it

- Can it run using only the approved apps, and does it fail clearly when one is missing?
- Does every publish show a card on the phone, and does a rejection (with reason) make the agent revise?
- Does it recover when the screen is not what it expected (Back, re-read)?
- Does it stop when the run is cancelled (`melaya_run_cancel` plus `melaya_phone_stop`)?
- Poll `melaya_run_status` (read `outcome`) and inspect with `melaya_run_inspect` (see `../../../modules/validate-debug/GUIDE.md`).

## 3. Browser Control pipelines

### How to create one

1. The user opens https://app.melaya.org/browser-control, confirms the consent screen, picks the browser engine (Chrome, Edge or Brave) and the sites, and creates the task. This sets the browser-specific fields correctly.
2. You can then read it with `melaya_pipeline_get`, edit the instruction or schedule, preview (`melaya_pipeline_preview`) and save the WHOLE document back (`melaya_pipeline_save` mode `update`).

Hand-writing the browser fields from scratch is error-prone; prefer the page, then edit.

### What such a pipeline contains (so you recognise it)

- Pipeline-level: `is_browser: true`, `browser_source`, the allowed sites, and `force_local_runner: true` (browser pipelines always run on the user's runner, in a dedicated Melaya browser profile, never the user's personal profile). Plus `hitl_mode`.
- Agent: `agent_tools` from the `browser_*` family (verify with `melaya_pipeline_registry` search "browser"):
  - read: `browser_get_screen_tree`, `browser_get_text`, `browser_screenshot`, `browser_current_target`, `browser_wait`, `browser_wait_for_network_idle`, `browser_hover`, `browser_move`, `browser_list_tabs`, `browser_read`;
  - act: `browser_click`, `browser_tap`, `browser_input_text`, `browser_press_key`, `browser_scroll`, `browser_select_option`, `browser_submit`, `browser_navigate`, `browser_back`, `browser_forward`, `browser_drag_hold`, `browser_open_tab`, `browser_switch_tab`, `browser_close_tab`, `browser_batch`, `browser_upload`;
  - hand over to a person (logins, one-time codes, CAPTCHAs, account choice): `browser_ask_user` (dropped in autonomous mode).
- `human_approval_tools` stays empty: browser actions are gated by what they do (publish, purchase, upload, account change, ...) whatever the tool list says.

### Rules

- The runner must be connected when the run starts (`melaya_runner_status`); otherwise the run cannot reach a browser.
- Sites outside the pipeline's allowed list are blocked; the agent must stop and report, not look for another route.
- Instructions must forbid entering passwords, one-time codes and payment details, and must require reading the page before acting and acting by `@e` ref.
- A plan may limit browser devices, sessions and scheduling; the Browser Control page shows an upgrade prompt when a limit is hit.

### `browser_read` in ordinary pipelines

Any pipeline agent can use `browser_read` to fetch pages through the user's own browser (their sign-ins, their bot-check clearance). It is read-only. It needs the extension paired, online and set to **All tabs** at run time, and a person may need to solve a CAPTCHA. For unattended schedules prefer keyless tools (`scrape_page`, RSS) and keep `browser_read` for pages nothing else can reach.

## 4. Approvals, autonomy and triggers (device view)

| `hitl_mode` | Phone: publish | Phone: payment | Phone: risky taps | Browser consequential actions | Connector write tools |
|---|---|---|---|---|---|
| `safe` (default) | card | card | card | approval | approval (if listed in `human_approval_tools`) |
| `payments_only` | no card | card | no card | per effect policy | run automatically |
| `autonomous` | no card | no card | no card | per effect policy | run automatically |

- Unknown or missing values count as `safe`.
- Runs started by an **event trigger** are forced to `safe`, whatever the pipeline says.
- Approvals are decided by the user on the phone card, in a phone notification (works on a locked phone), in the Melaya web app, or in the extension panel. `melaya_approval_list` lists them; nothing over MCP can approve.
- `autonomous` is a deliberate user choice. Before setting it, tell the user in one sentence what it removes, and get an explicit yes.

For the general HITL model and schedules, load `../../../modules/automation-governance/GUIDE.md`.

## 5. The Melaya Assistant

**What it is.** The chat copilot inside the Melaya app, at https://app.melaya.org/assistant (also in the mobile app's bottom bar). It has two parts: a short onboarding that asks about the user's goals and suggests templates, and a streaming chat.

**What it can do.**
- Answer questions about the user's own pipelines, runs, costs, usage, templates and evaluations, and search Melaya's documentation.
- Use the connectors the user selects in the chat (for example Gmail or an ERP). Write actions from those connectors pause for approval in `safe` mode.
- Drive the paired phone directly. Before a phone task it checks pairing; if no phone is paired it shows a "pair your phone" card instead. After a successful phone task it offers to save the flow as a Device Control pipeline.
- Accept images and documents dropped into the chat, and voice dictation.
- An **autonomy selector** next to the input sets `safe` / `payments_only` / `autonomous` for that chat.

**Model.** Any connected model (cloud key or runner model). The choice is remembered per surface. Assistant usage is bounded by the plan's message allowance.

**When to use the Assistant vs a pipeline.**

| Use the Assistant when | Use a pipeline when |
|---|---|
| The task is one-off or exploratory | The task repeats or runs on a schedule |
| The user wants to talk it through | Several agents or steps are needed |
| A quick look at the user's own runs or costs | Outputs must be saved (files, Sheet rows, documents) or emailed |
| A quick phone action ("open Spotify and play X") | Event triggers must start it |
| Proving a phone flow works before saving it | The flow must be validated, versioned and handed over |

**The extension cockpit** is the browser twin: the side panel of the Melaya extension, where the user picks Cloud (a cloud model; no runner needed) or Local (a model on their runner), selects connectors and autonomy, and chats while the agent works in the attached tab. Approvals appear in the panel.

From an MCP conversation you cannot talk to the Assistant (there is no bridge, on purpose). Point the user to it when it is the better fit.
