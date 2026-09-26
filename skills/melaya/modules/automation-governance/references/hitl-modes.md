# HITL modes and approvals reference

Verified against how runs actually behave, not only against older documentation. Where they disagree, this file follows the observed behaviour.

## 1. Where HITL is configured

| Level | Field | Values |
|---|---|---|
| Pipeline | `hitl_mode` (alias `hitlMode`) | `"safe"` (default), `"autonomous"`, `"payments_only"`; unknown or absent => `"safe"` |
| Agent (embedded in `steps[]`) | `human_approval_tools` | list of exact tool names to gate |
| Trigger | `hitl` | only `"safe"` accepted; triggered runs are forced safe |
| Phone agent (`melaya_run_phone_agent`) | `hitl_mode` | same three values, applied to on-device approval cards |

## 2. What each mode actually gates

| Mode | Tool-layer gate |
|---|---|
| `safe` | exactly the names in the agent's `human_approval_tools`, minus `phone_*` (phone actions use the on-device approval overlay instead) |
| `autonomous` | the per-agent list is DROPPED. Only form-modal tools stay gated (today `luma_register_event`, which needs operator-filled form answers) |
| `payments_only` | identical to `autonomous` at the tool layer. For phone agents it means on-device cards appear for purchases only |

Overrides that force `safe` regardless of the config:
- any run started by an event trigger: in addition, EVERY tool that is not marked read-only in the tool registry is gated, listed or not, and a tool the registry does not know is gated too (fail closed). Only the built-in framework helpers (final response, tool search and activation, delegation to a sub-agent, knowledge-base retrieval) and `phone_*` (which has its own on-device approval) pass. This also applies to pipelines whose code was hand-edited, and phone actions of a triggered run are clamped to safe;
- a crew woken by `wake_crew` from its first delivered event onward;
- trading crews (always safe; their order rails only run on gated tools).

Documentation trap: older notes say `human_approval_tools` gates the listed tools "whatever the mode" (the melaya_pipeline_save guide has been corrected). The runtime drops the list under `autonomous` and `payments_only`. Rule: if anything must be reviewed, the pipeline is `safe`.

Second trap: `safe` is not "gate every write". Outside triggered runs it gates only what is listed. Audit each agent's `agent_tools` for write tools (send, reply, post, create, update, delete, share, upload, register, pay) and put every one in `human_approval_tools`.

## 3. Batching of approval cards

- Several calls to the same gated tool in one model reply may coalesce into one batch card.
- Never coalesced (one card per call, individually editable): `gmail_send`, `gmail_reply`, `smtp_send`, LinkedIn message / connection request / reply / edit / post, `x_post`, `x_reply`, `x_send_dm`, `x_delete_post`, `x_follow`, `tiktok_publish_video`, and any gated call whose args carry free text (`body`, `text`, `message`, `content`, `caption`, `comment`, `subject`, `title`, `html`, `markdown`).
- Form-modal tools always get one card per call so the form opens.

## 4. Approval lifecycle

| Situation | Behaviour |
|---|---|
| Manual or scheduled run hits a gated call | the run pauses and waits with NO timeout (product rule). It resumes on approve, skips on reject, stops if the run is killed or superseded |
| Triggered run hits a gated call | waits up to the trigger's `approval_ttl_s` (60-86400 s, default 3600), then the call is rejected as expired |
| Trigger `tool_call` write | approval card with the stored, signed arguments; edits to arguments are ignored; expires after `approval_ttl_s`; max 20 pending per trigger, 50 per user |
| Trigger with UI-granted autonomy | writes run without a card only if every templated argument meets its constraint and the daily write cap allows; else a card is raised |

Not a pipeline approval: a connector write the assistant runs over MCP (`melaya_connector_call` with the user's `melaya:connectors.write` permission) executes at once, with no card, and is audit-logged. The assistant must confirm it with the user in the chat first. Anything that moves money or trades is refused over MCP at every level and is approved only in the Melaya app.

Where humans decide (never over MCP):
- approvals inside a run (manual, scheduled or triggered): the Melaya app approval queue, or the phone (cards reach a locked phone); `melaya_approval_list` shows them;
- approvals of a trigger's own `tool_call` write: on the trigger in the Agent Builder, tab Schedule & Triggers, and inline on the delivery row. The global Monitoring approval panel does not list these yet, and they are not tied to a run id.

A new manual run of the same pipeline replaces (supersedes) an earlier manual or scheduled run that is still waiting, which kills its pending approvals. Triggered runs are the exception: they never supersede, and are never superseded by, another run of the same pipeline.

What you (the agent) do:
1. `melaya_approval_list` with `run_id` to see what is waiting; `include_history: true` for the audit trail.
2. Report the tool, the preview and where to approve.
3. Wait, or tell the user the run will resume after their decision. Do not re-run the pipeline to "get past" an approval: a new run supersedes the old one and kills its pending approvals.

## 5. Choosing the gate set (decision table)

| The agent can... | Gate? |
|---|---|
| email or message a third party | yes, `safe` + listed |
| post publicly (social, forum, blog) | yes |
| share a file or change permissions | yes |
| write to the client's CRM / ERP / data store | yes in production; for the system's own data-store sheet, an ungated append is acceptable when the user agrees (it is the system of record and every row carries provenance) |
| create a new document in the owner's own Drive | optional; usually ungated |
| email only the owner's own address (digest, report) | may be ungated for a demo (section 6) |
| pay, order, trade, delete | always gated; never demo-ungated |
| read anything | never gated |

## 6. Running a demo without gates

Allowed only when all of these hold and the user asked for it explicitly:
- the only write is to the owner's own inbox (resolve the address with `gmail_my_address`, then one `gmail_send` to it) or the owner's own Drive;
- no third-party recipient, public post, payment or deletion anywhere in the pipeline;
- the run is manual or scheduled (triggered runs are forced safe and will gate it anyway);
- keep `hitl_mode: "safe"` and remove only that one tool from `human_approval_tools`; never flip the pipeline to `autonomous`;
- record in the handover: which gate was removed, why, and the one-line change that restores it before any real recipient is added.

## 7. Pre-handover HITL audit

- [ ] `melaya_pipeline_get` for each pipeline: `hitl_mode` is `safe` wherever a write tool exists.
- [ ] Every write tool in each agent's `agent_tools` appears in that agent's `human_approval_tools` (or is documented as a deliberate owner-only exception).
- [ ] Scheduled pipelines with gated sends: the user knows approvals will wait for them each morning.
- [ ] Triggered pipelines: the user knows every write in a triggered run asks, and how long approvals live (`approval_ttl_s`).
- [ ] `melaya_approval_list` shows nothing unexpected left pending from validation runs.
