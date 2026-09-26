<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when: The plain-language path for a NON-TECHNICAL user who has an AI assistant connected to Melaya and wants to get something done, not build a system. Use when the user says things like "I just connected Melaya, what now", "what is a pipeline", "run the template for me", "run it for this company, here is the deck", "is my run finished", "what did it produce", "something is waiting for my approval", "run it every Monday", "change the email it sends to", "let my colleague see it", "it failed, help". Gives a short explanation of Melaya, a jargon-free glossary, and nine numbered journeys (first setup, run a template, run with a brief and files, watch a run and read results, approve or reject, schedule, change something simple, share with a teammate, get help), each with what the user says, what the assistant does, what success looks like and what to do when it fails. For building a whole multi-pipeline system for a client, load `../../modules/agentic-systems/GUIDE.md` instead.

# Melaya quickstart: plain-language journeys

This module is for the person who is NOT an engineer. They have an AI assistant (Claude or another
MCP client) connected to their Melaya account and they want results: run something, see what it
produced, approve what needs approving, and make it happen again on its own.

You, the assistant, do the tool calls. The user only talks, clicks links you give them, and
decides. Keep every answer short, in plain words, and end each step with what the user should
see next.

References in this module:
- `references/glossary.md`: every Melaya word explained in one or two plain sentences, with an example.
- `references/app-map.md`: where each thing lives in the Melaya app (https://app.melaya.org), with click paths.
- `references/troubleshooting.md`: what the user sees, what it means, what to do. Use it whenever something fails.

## How to behave (read before any journey)

1. **Start with `melaya_setup_status`.** One call says what is ready and what is missing, with the
   exact link or command that fixes each gap. Call it again whenever something fails in a way
   that looks like setup (no model, no runner, a service not connected).
2. **Explain before you act, in one sentence.** "I will start the pipeline now; it usually takes a
   few minutes." Never dump raw JSON or tool output on the user. Translate it.
3. **Never ask for a password, API key or token in the chat.** Services are connected through a
   link the user opens (`melaya_connector_connect`). If the user pastes a key anyway, tell them
   to remove it from the chat and store it under Connectors instead.
4. **Never approve anything for the user.** Approvals are a human decision by design; the assistant
   can only list them (`melaya_approval_list`). Tell the user what is waiting and where to click.
5. **Know where you are running.** If you have a shell on the user's own computer, you may run
   setup commands yourself. On claude.ai, mobile or any hosted chat you cannot: give the command
   and say which computer it must run on. Never say you started something you could not start.
6. **Missing tools mean missing permission.** The tools you see are filtered to what the user
   granted when they connected Melaya. If, for example, no pipeline tools are available, say so
   plainly and ask them to reconnect Melaya to the assistant and tick the pipelines permission.
7. **Confirm before anything irreversible or shared:** deleting a pipeline or a project, inviting a
   person, arming a schedule, cancelling a run, and any send or change you make through a connected
   service from the chat (possible only if the user gave the assistant the "connectors.write"
   permission; it runs at once, with no approval card). Anything that moves money is never done from
   the chat: it waits for the user's approval in the Melaya app.
8. **Every tool call counts.** Assistant tool calls are counted against the plan's allowance.
   If the user plans a lot of work, check `melaya_account_usage` first.
9. **Use the pipeline's exact name.** Names come from `melaya_pipeline_list`. The name the user
   types ("the DD one") is a nickname; find the real one and confirm it with them.

## What Melaya is (say this when asked)

> Melaya runs teams of AI agents for you. You describe a job once (for example "research a
> company and write me a one-page summary"), Melaya saves it as a **pipeline**, and from then on
> you can run it whenever you like, give it a new subject each time, have it run on a schedule,
> and approve anything important before it happens. The agents use the services you connect
> (Gmail, Google Drive, Sheets and many others) and a large set of free public data sources.
> Everything they do is recorded, so you can always ask what happened and why.

Five words the user needs on day one (the full list is in `references/glossary.md`):

| Word | Plain meaning |
|---|---|
| Pipeline | A saved job made of steps. You run it; it does the work. |
| Agent | One AI worker inside a pipeline, with one role (researcher, writer, mailer). |
| Run | One execution of a pipeline, start to finish. Each run has its own record. |
| Connector | A service you linked to Melaya (Gmail, Drive...). Agents can only use what you connected. |
| Approval | A pause where the pipeline asks you before doing something important, like sending an email. |

## Journey index

| # | Journey | The user says something like |
|---|---|---|
| 1 | First setup | "I just connected Melaya, what do I need to do?" |
| 2 | Run a template | "Is there something ready-made for X? Run it." |
| 3 | Run with inputs (brief and files) | "Run it for acme.com, here is their deck." |
| 4 | Watch a run and read its results | "Is it done? What did it find?" |
| 5 | Approve or reject an action | "It says something is waiting for me." |
| 6 | Schedule it | "Run this every Monday at 8." |
| 7 | Change something simple | "Send the report to my colleague instead." |
| 8 | Share with a teammate | "Let Sam see this project." |
| 9 | Get help | "It failed." / "I am stuck." |

---

## Journey 1: First setup

Goal: the account can run a pipeline. Minimum: a working AI model. Usually also: the services the
user's work needs (Gmail, Drive...). Optional: the local runner.

**The user says:** "I just connected Melaya. What now?"

**You do:**
1. `melaya_setup_status`. Read the gaps back as a short numbered to-do list, most important first.
2. **Model.** Nothing runs without a model. If the user has none:
   - Simplest: a key from a provider they already use. Call `melaya_connector_connect` with the
     provider id (for example `openai`, `google`, `anthropic`, `qwen`); it returns where to store the
     key. Tell them: "Open this link, paste your key there (not here), then tell me when done."
   - Free options exist (several providers have a free, rate-limited tier). Say so if cost matters.
   - Check it worked: `melaya_model_list` with that provider. `status: "ok"` means ready.
     `no_key` means not stored yet; `invalid_key` means the key was rejected.
3. **Services.** Ask what the work touches ("Do you want results emailed? Saved in Google
   Drive?"). For each: `melaya_connector_connect` with the service id (`gmail`, `google_drive`,
   `google_sheets`, `google_docs`...). The user opens the link and consents on the provider's own
   screen. Then `melaya_connector_test` with the same id.
4. **Runner (optional).** Only needed to use models that live on the user's own computer (for
   example their Claude Code subscription, Ollama, LM Studio), to keep a pipeline's work on their
   machine, or to reach local files. Skip it otherwise. If needed: `melaya_runner_setup`, then
   follow rule 5 above (run the command yourself only if you have a shell on THEIR computer,
   otherwise hand it over). Poll `melaya_runner_status` until connected (first start can take a
   minute). Treat the command as a password: never repeat it later or write it to a file. Full
   detail: load `../../modules/runners-models/GUIDE.md`.
5. `melaya_setup_status` again to confirm.

**Success looks like:** setup status shows no blocking gap; the model list says `ok`; each needed
service tests green.

**If it fails:**
- The consent link opens but the service still shows as not connected: ask the user to finish the
  consent screen (all boxes), then test again.
- The key is rejected: the key is wrong, expired, or from the wrong account. They replace it on the
  Connectors page.
- The runner never connects: see `references/troubleshooting.md`, section Runner.

---

## Journey 2: Run a template

A template is a ready-made pipeline. Using one creates the user's own copy, which they can then run
and change.

**The user says:** "Do you have something ready-made that watches news about my competitors?"

**You do:**
1. `melaya_pipeline_templates` with `search` (a plain keyword: "news", "invoice", "due diligence").
   Present the top 3 in plain words: what it does, what services it needs, whether it has a schedule
   (from each entry's `builds` summary).
2. The user picks one. Check the services it needs are connected (Journey 1, step 3).
3. The copy needs a **project** (a folder for pipelines). Ask which project; if they want a new
   one, create it with `melaya_project_create` (needs the "projects" permission and the Forge plan
   or above; names are unique across Melaya), or they create it in the app (see
   `references/app-map.md`, "Create a project").
4. `melaya_pipeline_from_template` with `template_id`, a short `name` (lowercase, underscores),
   and `project`. Note the name it returns: that is the real name from now on.
5. Many templates contain settings marked **EDIT ME** (for example a watch list, a threshold, a
   recipient). `melaya_pipeline_get` the new pipeline, show the user those settings in plain words,
   and ask what their values should be. Changing them is Journey 7.
6. Run it (Journey 3 with no inputs, or just "Run now" in the app).

**Success looks like:** the new pipeline appears in `melaya_pipeline_list` under their project,
the EDIT ME settings hold the user's real values, and the first run finishes with outcome success.

**If it fails:**
- "Not a member" or "not found" on the project: the project does not exist yet or the account is
  not in it. Create it (`melaya_project_create`, or the user in the app), or ask its owner to invite
  them.
- The list is empty for a keyword: try a broader word. Some community templates are withheld until
  validated, so absence does not mean it cannot be built; offer to build one (load
  `../../modules/pipeline-authoring/GUIDE.md`).
- Plan limit on saved pipelines: `melaya_account_usage` shows the cap.

---

## Journey 3: Run with inputs (a brief and files)

Every pipeline accepts, with no setup, a **brief** (free text for this run only) and up to 10
**files**. Some pipelines also declare named **inputs** (for example "company", required).

**The user says:** "Run the due diligence for acme.com, focus on the team. Here is their deck."

### From the chat

1. `melaya_pipeline_list` to find the exact name. Confirm: "You mean `company_dd` in project
   `Research`?"
2. `melaya_pipeline_get` to see whether it declares inputs, and which are required.
3. `melaya_pipeline_run` with:
   - `pipeline` and `project`;
   - `brief`: the user's words, tidied ("Run the due diligence for acme.com; focus on the team.");
     max 8 KB;
   - `files`: each `{url: "https://..."}` (up to 25 MB) or `{base64: "...", name: "deck.pdf"}` (up to
     7 MB); PDF, Office, CSV, TXT, Markdown, JSON, images; no archives or programs;
   - `inputs`: values for declared inputs, keyed by their key, when the pipeline has any.
4. Tell the user the run started and roughly how long runs take (usually minutes). Keep the run id;
   go to Journey 4.

### From the app

1. Open the pipeline (Agent Builder, or its row in the pipeline list, or Monitor).
2. Click the Run control. Two choices:
   - **Run now**: starts right away, as saved, with no brief. Disabled with a message when the
     pipeline has required inputs.
   - **Run with inputs**: opens a small composer: type the **Brief**, drop files anywhere on it
     (or click Attach files), fill any named fields, then **Run** (Enter runs, Shift+Enter makes a
     new line).
3. The run shows a **Run inputs** card. **Run again** re-runs with the same brief and files (files
   are kept for a limited time; after that it asks to attach them again).

**Success looks like:** the run starts, and in the run record the brief and file names appear
under Run inputs.

**If it fails (before anything runs, with a clear message):**
- Unknown input key, missing required input, wrong type, or a choice not in the list: fix the value
  and run again. Nothing was spent.
- Brief or total text too long: shorten it (8 KB per value, 32 KB total).
- Files refused on a pipeline set to run on the user's own computer ("Run Locally"): files reach
  cloud runs only today. Either run it in the cloud or give the content as text in the brief.
- A file type refused: convert to PDF or plain text.

---

## Journey 4: Watch a run and read its results

**The user says:** "Is it done? What did it find?"

**You do:**
1. `melaya_run_status` with the `run_id`. Read **`outcome`**, not `status` (every finished run
   shows status "done"; `outcome` says success, failure or cancelled). `terminal: true` means it
   is finished. If not finished, wait a little and poll again (a few seconds apart is plenty); tell
   the user you are waiting rather than going silent.
2. If the run is paused on an approval, go to Journey 5.
3. When finished with success: `melaya_run_inspect` (without tool calls first) to read what the
   agents said. Summarize the final result in 3 to 6 plain bullets and give links to any documents
   or sheets it produced.
4. Check the result is real, not just claimed: if the run says it wrote a Google Doc or Sheet rows
   or sent an email, read it back with `melaya_connector_call` (a read tool; for example the Drive,
   Sheets or Gmail read tools found with `melaya_connector_tools`). Say what you verified. Never
   "fix" a wrong result by writing to the document yourself: fix the pipeline and run it again.
5. If it failed: `melaya_run_diagnosis` with the `run_id`. It names the step, the tool and the
   reason. Explain it in one or two sentences and propose the fix (see
   `references/troubleshooting.md`).
6. If it runs far too long or loops, offer to stop it: `melaya_run_cancel` with `run_id` and
   `pipeline`. Warn that work already done (for example a sent message) is not undone.

**In the app:** the live run view opens in the Agent Builder when a run starts; past runs are in the
pipeline's **History** tab, and under **Monitor** by project, where each run has its steps, the
agents' replies and a trace.

**Success looks like:** `outcome` success, a plain summary, and the produced artifact opened and
checked.

**If it fails:** use `melaya_run_diagnosis` first, then `references/troubleshooting.md`. For deep
debugging of a pipeline you built, load `../../modules/validate-debug/GUIDE.md`.

---

## Journey 5: Approve or reject an action

Pipelines can be set to pause before important actions (sending an email, posting, writing to an
outside system). The pause is an **approval**. Only the user can decide.

**The user says:** "Melaya says something is waiting for me."

**You do:**
1. `melaya_approval_list` (add `run_id` to limit it to one run). For each item say: which pipeline,
   which agent, what it wants to do, and the key details (recipient, subject, text).
2. Tell the user where to decide: in the Melaya app, open the pipeline in the Agent Builder; the
   approvals panel ("Pending tool approvals") opens from the bell on the canvas. Each item has
   **approve**, **edit** (change the fields first, then "approve with edits") and **reject** (with an
   optional reason). They can also decide from the Melaya app on their phone if it is set up.
3. Offer a recommendation if asked ("the email looks right, but the subject is empty; I would edit
   it before approving"), but do not decide.
4. After they decide, `melaya_run_status` again. Approve means the action runs (with their edits).
   Reject means the agent is told no and carries on without it.

**Clues the user may notice:** the browser tab shows a count of pending approvals; a pipeline with
something waiting pulses in the pipeline list.

**Success looks like:** the approval list is empty for that run and the run continues to the end.

**If it fails:**
- Nothing appears in the list but the run seems stuck: `melaya_run_diagnosis`; it may be slow, not
  waiting.
- An approval disappeared as rejected without anyone clicking: runs started by an automatic trigger
  have a deadline (about an hour); unanswered approvals expire as rejected.
- The user wants approvals on something that currently happens without asking: that is Journey 7
  (turn the approval on for that action).

---

## Journey 6: Schedule it

**The user says:** "Run this every Monday at 8 in the morning."

**You do:**
1. Check the plan allows schedules: scheduling needs a paid plan, and each plan has a minimum
   interval. If unsure, `melaya_account_whoami` or `melaya_account_usage`.
2. Turn the sentence into a schedule and read it back in words: "Every Monday at 08:00, Paris time.
   Correct?" (Cron examples: `0 8 * * 1` Mondays 08:00, `0 9 * * *` daily 09:00, `0 7 1 * *` the 1st
   of each month 07:00.) Always ask for the time zone (for example `Europe/Paris`).
3. Check the pipeline works without a brief: scheduled runs start with no brief and no files today.
   If it needs a subject every time, the subject must be written into the pipeline (Journey 7).
4. `melaya_pipeline_schedule` with `action: "set"`, `pipeline`, `project`, `cron`, `timezone`. If
   the pipeline runs on the user's own computer, add `requires_runner: true` (runs are skipped, not
   failed, while that computer is off).
5. Confirm with `action: "status"` and tell the user the next run time.

To pause: `action: "pause"`; to restart: `action: "resume"`; to remove: `action: "set"` with an
empty `cron`. In the app: Agent Builder, the pipeline's **Schedule & Triggers** tab.

**Starting on an event instead of a time** (a new email, a form submission, a webhook): that is a
trigger. Triggered runs always ask for approval before outside actions. Load
`../../modules/automation-governance/GUIDE.md` for it, and guide the user's clicks with
`../../modules/automation-governance/references/triggers-ui-walkthrough.md`.

**Success looks like:** status shows the schedule armed with the right next time, and after the
first scheduled time a new run appears.

**If it fails:** "interval too tight for the plan" means pick a longer interval or upgrade; status
shows skipped fires with the reason (for example the runner was offline).

---

## Journey 7: Change something simple

Examples: a recipient, a subject line, a watch list, the time window, the model, turning an approval
on for sending.

**The user says:** "Send the weekly report to sam@acme.com instead of me."

**Rule you must follow:** saving a change replaces the WHOLE pipeline. Always start from the current
version and send everything back.

**You do:**
1. `melaya_pipeline_get` with the exact `pipeline` (and `project`).
2. Find the setting in plain terms and show the user the before and after lines. Change only that.
3. `melaya_pipeline_preview` with the full edited config. Check your change appears and nothing else
   disappeared.
4. `melaya_pipeline_save` with `mode: "update"`, `pipeline` (the exact name) and the FULL config.
5. `melaya_pipeline_get` again and confirm the change stuck.
6. Offer a test run (Journey 3), and if the change affects an outside action, suggest keeping the
   approval on for the first run.

Common simple changes and where they live:
| Change | Where in the pipeline |
|---|---|
| Who gets the email, what it says | The mailer agent's instruction |
| What to watch, thresholds, time window | The agent instruction that uses them (often in an EDIT ME block in templates) |
| Ask before sending | Add the send tool (for example `gmail_send`) to that agent's `human_approval_tools`, with pipeline `hitl_mode: "safe"` |
| The AI model | Each agent's `model_provider` / `model_name` (check the id with `melaya_model_list` first) |
| The schedule | `melaya_pipeline_schedule` (Journey 6), not a config edit alone |

**In the app:** Agent Builder, open the pipeline; the **Pipeline** tab shows the steps (click an agent
to edit its instruction and tools); the **Configure** tab holds the model (with "Apply to all agents")
and "Run Locally"; save when done.

**Success looks like:** the read-back shows the new value; the next run uses it.

**If it fails:** if the preview drops something or the change is bigger than one setting (new steps,
new tools), load `../../modules/pipeline-authoring/GUIDE.md`.

---

## Journey 8: Share with a teammate

**The user says:** "Let Sam see this project." / "Give my colleague access."

**You do:**
1. `melaya_team_list` with `project`. It shows the members and whether this account can invite (only
   the project owner can).
2. Ask: view only (`viewer`) or allowed to change pipelines (`editor`)? Confirm the person. Seats
   count against the owner's plan.
3. `melaya_team_invite` with `project` and `role`, and either `username` (an existing Melaya user) or
   no username to get a shareable invite link (optionally `expiry_hours`, `max_uses`). Give the link
   to the user to send; do not send it anywhere yourself.
4. `melaya_team_list` again once they joined.

**In the app:** open the project (Monitor, then the project), Team: **Invite**, or **Invite by link**
for a QR code and link.

**Success looks like:** the teammate appears in the team list with the right role.

**If it fails:** "owner only" means ask the project owner to invite; a seat limit means the owner's
plan is full.

To share a pipeline design (not the project) with the team, it can be saved as a template in the
Agent Builder (Templates, Share with team). Load `../../modules/projects-templates/GUIDE.md` for details.

---

## Journey 9: Get help

**The user says:** "It failed." / "Nothing happens." / "I am lost."

**You do, in this order:**
1. `melaya_setup_status`: is something missing (model, service, runner)?
2. For a specific run: `melaya_run_diagnosis` with the `run_id`, then `references/troubleshooting.md`.
3. For a service: `melaya_connector_test` with its id. A red test means reconnect it
   (`melaya_connector_connect`).
4. For "is my plan the problem": `melaya_account_usage`.
5. For "how are my agents doing overall": `melaya_eval_report` (optionally `project`, `range: "week"`).
6. Still stuck: the app has **Guided tutorials**, **Documentation** and **Report a bug** in its menu.
   Help the user write a short report: what they did, what they expected, what they saw, the pipeline
   name and the time. Never include keys or passwords in it.

**Success looks like:** the user knows the cause in one sentence and the next action.

---

## Where to go next

| The user wants to... | Load |
|---|---|
| Use their own computer's models, Claude Code, Ollama, or choose models | `../../modules/runners-models/GUIDE.md` |
| Let agents operate their Android phone or their browser | `../../modules/devices-browser/GUIDE.md` |
| Find which tools and services fit a need | `../../modules/discovery/GUIDE.md` |
| Build or heavily change a pipeline | `../../modules/pipeline-authoring/GUIDE.md` |
| Organize projects, templates, EDIT ME settings | `../../modules/projects-templates/GUIDE.md` |
| Automatic starts on events, approval rules, memory, cost | `../../modules/automation-governance/GUIDE.md` |
| Debug a pipeline deeply | `../../modules/validate-debug/GUIDE.md` |
| Build a complete multi-pipeline system for a client | `../../modules/agentic-systems/GUIDE.md` |
