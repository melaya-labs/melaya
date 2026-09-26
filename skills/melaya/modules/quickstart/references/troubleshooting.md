# Troubleshooting: what the user sees, what it means, what to do

First call for anything that looks like setup: `melaya_setup_status`. First call for a failed run:
`melaya_run_diagnosis` with the run id. Then find the row below and say the fix in one sentence.

## Models

| The user sees | It means | What to do |
|---|---|---|
| "Connect <provider> to continue" / model list `no_key` | No key stored for the chosen provider | `melaya_connector_connect` with the provider id; user stores the key on the Connectors page |
| "<provider> rejected the key" / `invalid_key` | Key wrong, expired or revoked | User replaces the key under Connectors; check again with `melaya_model_list` |
| "<provider> needs a workspace for this key" | A key scoped to a workspace | Use an unscoped key, or reconnect and choose the workspace |
| "<provider> is out of credits" | The balance with that provider is empty | Top up with the provider, or switch provider |
| "<provider> is rate-limiting you" | Free tier or per-minute cap | Wait (it resets on its own) or switch model |
| "<provider> retired that model" | The provider withdrew the model | Pick the suggested replacement; update pipelines that name it (Journey 7) |
| "This conversation got too long" | Too much text for the model | Start a fresh chat, or choose a model with a bigger context |
| A run fails at once with a model error | The pipeline names a model id that does not exist for the account | `melaya_model_list`, then fix the model in the pipeline (Journey 7) |

## Services (connectors)

| The user sees | It means | What to do |
|---|---|---|
| The run says a service is not connected | The pipeline needs a service the user has not linked | `melaya_connector_connect` with that service id; user consents; `melaya_connector_test` |
| `melaya_connector_test` fails on a service that worked before | The consent expired or was revoked | Reconnect with `melaya_connector_connect` |
| Test is green but a tool still fails | The grant is fine but that action needs more permission, or the tool itself failed | `melaya_run_diagnosis` names the tool and error; reconnect with all boxes ticked; if it persists, report it |
| The assistant cannot see pipeline, phone or browser tools | That permission was not granted when Melaya was connected to the assistant | Reconnect Melaya to the assistant and tick the missing permission |
| The assistant says it cannot send, create or update something through a connected service, or cannot create a project | The "connectors.write" or "projects" permission was not granted (connections made before these permissions existed lack them) | Reconnect Melaya to the assistant and tick that permission; or let a pipeline do the send |
| The assistant refuses a payment, refund, purchase, transfer, ad budget change or order | Anything that moves money or trades is never run from the chat, whatever the permissions | The user approves it in the Melaya app |

## Runner (the user's own computer)

| The user sees | It means | What to do |
|---|---|---|
| Setup status: runner not running | The runner is not started on that computer | `melaya_runner_setup` gives the start command; run it on the user's computer (yourself only if you have a shell there) and poll `melaya_runner_status` |
| First start takes a while | It prepares itself the first time | Wait up to a minute, then poll again |
| Runner connected but no Claude Code models | The `claude` program is not signed in on that computer | User runs `claude` once and signs in, then restarts the runner |
| A new start command stopped the old one from working | Asking for a new command cancels an earlier unused one | Call `melaya_runner_setup` once and reuse its result |
| Scheduled runs skipped | The pipeline needs the runner and the computer was off | Expected with `requires_runner: true`; keep the computer on at run time or move the pipeline to the cloud |
| Files refused on a run | Files reach cloud runs only; the pipeline is set to Run Locally | Run it in the cloud, or pass the content as text in the brief |

More: load `../../../modules/runners-models/GUIDE.md`.

## Runs

| The user sees | It means | What to do |
|---|---|---|
| Status "done" but nothing happened | "done" only means finished; the result is in `outcome` | Read `outcome`; if failure, `melaya_run_diagnosis` |
| Run refused before starting with an input error | Missing required input, unknown key, wrong type, text too long | Fix the value (brief max 8 KB, total text 32 KB) and run again; nothing was spent |
| Run is slow | Runs usually take minutes; some agents make many calls | Poll every few seconds; if it clearly loops, `melaya_run_cancel` (work done is not undone) |
| Run paused | Waiting for an approval | `melaya_approval_list`; user decides in the app (Journey 5) |
| The agent says it created a document or sent an email, but the user cannot find it | The claim was not verified | Check with `melaya_connector_call` (read-only) on Drive, Sheets or Gmail; if missing, `melaya_run_inspect` with tool calls to see what really happened |
| Same wrong behaviour keeps coming back after a fix | The pipeline remembers old failures in its memory | `melaya_agent_memory` to look; ask the user before clearing memory or turning it off |
| The result is vague or invents facts | The instructions are too loose | Load `../../../modules/pipeline-authoring/GUIDE.md` to tighten instructions (sources required, stop rules) |

## Approvals

| The user sees | It means | What to do |
|---|---|---|
| "No pending approvals" but the run seems stuck | It is working, not waiting | `melaya_run_diagnosis` |
| Approval expired as rejected | Runs started by an automatic trigger have a deadline of about an hour | Re-run, and decide sooner |
| An email went out without asking | The send action was not set to ask first | Journey 7: add the send tool to that agent's approval list with `hitl_mode: "safe"` |
| The user asks the assistant to approve | Not possible by design | Point to the approvals panel in the app or the phone |

## Schedules and plan limits

| The user sees | It means | What to do |
|---|---|---|
| Schedule refused | Free plan, or interval tighter than the plan allows | Pick a longer interval or upgrade |
| Schedule armed but runs need a subject | Scheduled runs start without a brief or files today | Write the subject into the pipeline (Journey 7) |
| "Limit reached" on pipelines, runs, projects, seats or assistant calls | Plan cap | `melaya_account_usage` shows which; wait for the reset or upgrade |
| Cannot invite | Only the project owner can invite, and seats are limited | Ask the owner; check seats |

## Writing a good help request

When the user reports a problem to Melaya (menu: Report a bug), help them include: what they did,
what they expected, what they saw (the exact message), the pipeline name and project, and the time.
Never include keys, passwords or private documents.
