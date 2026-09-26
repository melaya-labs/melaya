# Pipeline config schema, with builder UI equivalents

Authoritative sources: the `melaya_pipeline_save` / `melaya_pipeline_preview` tool descriptions. When they
disagree with this page, the preview wins: the generated program is ground truth.

Builder = https://app.melaya.org/builder. Its tabs: **Pipeline** (the canvas of steps and agent cards),
**Configure**, **Docs**, **Memory**, **Schedule & Triggers**, **Code**, **History**.

## 1. Pipeline-level fields

| Field | Type | Behaviour | Builder UI equivalent |
|---|---|---|---|
| `name` | string | The human title ("Acme - Deal Intake"). The save response returns the CANONICAL name (a lower snake case id); use that for get, run, schedule and trigger | Configure: pipeline name |
| `display_name` | string | Card label; copied from `name` when absent | same |
| `description` | string | What it does, what it needs (brief, files), what it delivers. Aim for 200+ characters | Configure: description |
| `project` | string | All pipelines of one system share a project. `project` on the save call overrides it. Projects are created with `melaya_project_create` (projects permission) or in the app | Project selector |
| `steps` | array | The step list; agents are EMBEDDED here (see step-kinds.md) | Pipeline canvas |
| `edges` | array | Leave `[]`. Non-empty edges switch to graph routing, which does not compile `condition` or `loop` steps (step-kinds.md section 6) | Arrows drawn on the canvas |
| `tools` | array | Legacy pipeline-wide pool. Leave `[]`: an agent with an empty tool list INHERITS this whole pool | none (derived) |
| `model_provider` / `model_name` | string | Fallback model for agents that set none | Configure: Model, plus **Apply to all agents** (on = every agent follows the pipeline model; off = per-agent overrides kept) |
| `hitl_mode` | `"safe"` / `"autonomous"` / `"payments_only"` | Only `"safe"` honours per-agent `human_approval_tools` (section 3) | Pipeline tab: **Autonomy** (Safe / Autonomous / Payments-only) |
| `persistent_memory` | bool | Cross-run memory (context-and-memory.md). Default false | Configure: **Persistent memory** toggle |
| `schedule` | cron string | e.g. `"0 7 1 * *"`. Not active until armed with `melaya_pipeline_schedule` action `set` | Configure: **Schedule** (Manual, Daily, Weekdays, Weekly, Hourly, Custom cron) |
| `user_lang` | string | `en`, `fr`, `zh`, `es`, `pt` add an answer-language line; other codes do nothing (limits-costs-languages.md) | follows the app language |
| `inputs` | array | Declared run inputs (run-inputs.md). API and MCP only | none (the run composer shows the fields) |
| `loop_policy` | object | Default quality loop for agents that set none (quality-loop.md) | per agent: **Quality loop** |
| `inline_rag_docs` | array | `[{"title", "body"}]` static-context documents written at save; not visible in preview (expected) | Docs tab (they appear as documents) |
| `rag_mode_retrieval` | bool | With `rag_embedder_provider` + `rag_embedder_model`: retrieval mode, agents get `rag_retrieve` | Docs tab: document source + **Embedder model** |
| `static_context_local_folder_path` | string | Documents from a folder on the user's computer, never uploaded. EVERY agent must use a local provider | Docs tab: local folder (Browse... on the runner) |
| `force_local_runner` | bool | Run on the user's runner even with cloud models (needed for session-bound tools such as LinkedIn or Luma) | Configure: **Run Locally** |
| `event_triggers` / `listen_trigger_wakeups` | | Only WAKE a crew that is already running. Starting runs from events is `melaya_pipeline_trigger` | Schedule & Triggers tab |

Unknown fields are silently dropped with a success response. Preview, then `melaya_pipeline_get`, to confirm.
Credentials never go in a config: services are connected under Connectors.

## 2. Agent object (inside `steps[]`)

```json
{
  "name": "Deal Scout",
  "role": "Venture sourcing analyst",
  "instruction": "...",
  "agent_tools": ["rss_search_entries", "decide_batch", "scrape_page", "web_search"],
  "human_approval_tools": [],
  "model_provider": "qwen",
  "model_name": "qwen3.7-plus",
  "include_context": false,
  "include_rag_tool": false,
  "loop_policy": {"mode": "observe_only", "evaluator": "default"}
}
```

| Field | Behaviour | Builder UI equivalent (agent card) |
|---|---|---|
| `name` | Unique per pipeline; duplicates are suffixed "Name 2" | card title |
| `role` | Short persona line | role |
| `instruction` | SINGULAR. The per-step directive placed at the top of the agent's message. Supports `{{brief}}` and `{{inputs.<key>}}`. `instructions` (plural) is not read | Instruction box |
| `agent_tools` | The agent's tools. Missing or empty = inherits the pipeline `tools` pool (none when that pool is `[]`). `["__none__"]` = explicitly no tools | tool chips |
| `human_approval_tools` | Tools that pause for approval (subset of `agent_tools`; others are dropped). Honoured only with `hitl_mode: "safe"` | approval toggle on a tool chip |
| `model_provider` + `model_name` | Both needed, else the pipeline model. `model: {"provider", "name"}` is also accepted | **Model** (per-agent override; "inherits pipeline" when empty) |
| `include_context` | Default TRUE when absent: static documents are appended. Set false where not needed | static context switch |
| `include_rag_tool` | Default TRUE when retrieval is on. False opts the agent out of `rag_retrieve` | **RAG retrieval** switch |
| `loop_policy` | Object only (quality-loop.md). A string is ignored | **Quality loop** Off / Observe / Enforce, Max attempts |
| `system_prompt_override` | Replaces the persona's system prompt in every language | system prompt |
| `factory` / `crew` / `import_path` | Optional catalog persona from `melaya_pipeline_registry` `kind: "agents"`. Without them a neutral, instruction-faithful agent is built (recommended) | agent picker |

camelCase twins (`agentTools`, `humanApprovalTools`, `systemPromptOverride`, `modelName`, `modelProvider`, `includeRagTool`, `includeContext`, `loopPolicy`) are accepted; snake_case wins when both are set. Writing both is harmless and survives a round-trip through the builder UI.

A role persona can override your directive (a "COO" persona turned an alignment task into an operations assessment). Prefer the neutral agent; if you use a persona, pin the behaviour with `system_prompt_override`.

## 3. Approvals (HITL) as they really behave

Plain words: HITL (human in the loop) = the run pauses on a tool call until a person approves, edits or rejects it on an approval card (app, phone or `melaya_approval_list`).

| `hitl_mode` | What is gated on manual and scheduled runs |
|---|---|
| `"safe"` (default) | Exactly the tools listed in each agent's `human_approval_tools`. Everything else (reads, Sheet and Drive writes) runs freely |
| `"autonomous"`, `"payments_only"` | The per-agent lists are DROPPED. Only a few form tools that need human-supplied fields stay gated. A listed `gmail_send` goes out with no card |

Some MCP descriptions say the per-agent list applies "whatever the mode"; observed behaviour is the table above. So:
- Any gated send needs `hitl_mode: "safe"`.
- Gate every external write (send, post, invite, create in someone else's system). Do not gate local artifacts (`word_create`, `excel_write_data`): they are the deliverable.
- Phone tools are approved on the phone itself, not by a pipeline card.
- Runs started by an event trigger are forced to safe and gate EVERY tool that is not read-only, listed or not.
- A demo that only emails the owner's own inbox may run ungated deliberately; say so in the generator.
- Never approve a gate on the user's behalf.

Depth: `../../../modules/automation-governance/GUIDE.md`.

## 4. Save, get, preview semantics

| Call | Semantics |
|---|---|
| `melaya_pipeline_preview {config}` | Generates the program without saving. The only real validation. It also reports unknown keys and placeholders that name an undeclared input |
| `melaya_pipeline_save {mode: "create", config, project}` | Fails with a conflict if the name exists |
| `melaya_pipeline_save {mode: "update", pipeline, config, project}` | Replaces the WHOLE document. Omitted fields are erased |
| `melaya_pipeline_get {pipeline, project}` | The editable config, declared inputs and attached `docs`; `include_code: true` adds the generated program |
| `melaya_pipeline_list {project}` | Canonical names |
| `melaya_pipeline_schedule {action: "set", cron, pipeline, project, timezone}` | Arms the cron; needs a plan that allows schedules |

Editing a pipeline you did not generate: get -> modify the agents INSIDE `steps[]` (a flat top-level `agents[]`, when present, is only a mirror; editing it alone changes nothing) -> preview -> save update with everything -> get again.

## 5. Code vs no-code pipelines

- **No-code (normal)**: the program is regenerated from the config on every save. Anything you want to change, change in the config. Every save through MCP regenerates it.
- **Hand-edited code**: the builder's **Code** tab can switch the program to MANUAL EDIT, but only for accounts an administrator granted code editing; others see it read-only (a save attempt fails with "code editing not permitted").
- What happens to hand edits:
  - A later config save (UI or MCP) regenerates the program. Hand edits that still carry the auto-generated header are overwritten. Treat any hand edit as temporary unless the whole program was rewritten by hand.
  - A fully hand-written program (no auto-generated header) is kept, and then: runs with run inputs are REFUSED ("run inputs unsupported"); runs started by triggers receive the event only as a file, not in the first agent's message; quality-loop, memory and approval wiring exist only if the hand-written program includes them.
- Rule for client systems: stay no-code. Put every behaviour in the config, regenerate from the generator script, re-save.

## 6. What to check in the preview output

The preview is the generated program. Read it for:

- One numbered phase per step, in your order. A parallel phase lists its agents; a decide phase names the engine; a loop phase shows its iteration count; a condition phase shows its branches.
- A phase that is only a comment ("prefer linear flow ...") = that step was not compiled. Remove `edges`.
- Each agent is built with exactly your tool ids. A tool you listed but do not see was an invalid id and was dropped.
- Each agent shows the model name and provider you chose.
- A scoring call after every agent (it mentions "observe" or "enforce"): proof that `loop_policy` was read.
- An approval list on agents whose tools you gated.
- Your instruction text, with each `{{inputs.<key>}}` turned into a run-input lookup.
- No agent that should not send holds the shared pipeline toolkit (a sign that `agent_tools` was empty and `tools` was not).
