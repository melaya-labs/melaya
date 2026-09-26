<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when: Entry point and orchestrator for building, replicating or handing over a complete multi-pipeline Melaya agentic system for a client through the Melaya MCP server. Use when the task is "build the whole agent system for <Client>", "replicate this set of pipelines for another team", "take this client from requirements to a validated, documented system" or "hand the system over". Gives the end-to-end method (discover, connect, project, design, author via a generator script, validate on real runs, fix code vs config, document, hand over), the gate and definition of done for each phase, which sibling melaya-* skill to load at each phase, the operating rules, and a compact system-design template. For a single pipeline edit or one failed run, load the specific module instead. For a non-technical user who only wants to run, watch, approve or schedule existing pipelines, load `../../modules/quickstart/GUIDE.md` first; for runners and models load `../../modules/runners-models/GUIDE.md`; for phone or browser control load `../../modules/devices-browser/GUIDE.md`.

# Melaya agentic systems: the end-to-end method

You are acting as the integrator. Your job is to take a client from a list of business requirements to a set of Melaya pipelines that run on real data, write to a shared system of record, produce designed documents, respect human control, and come with client documentation. You work through the Melaya MCP server (`melaya_*` tools). You never touch the client's data outside what the user has connected and consented to.

This module is the map. Each phase below names the module that holds the detail. Load the module when you enter its phase; do not try to do the phase from memory.

## First: who are you helping?

| The person is... | Start with |
|---|---|
| A non-technical user who wants to run, watch, approve, schedule or lightly change pipelines ("run the template", "is it done", "something needs my approval") | `../../modules/quickstart/GUIDE.md`. Stay there unless they ask for a new system or a substantial change. |
| Someone who needs models on their own computer, a local runner, or help choosing a model | `../../modules/runners-models/GUIDE.md`, then come back here |
| Someone whose agents must operate an Android phone or their own browser | `../../modules/devices-browser/GUIDE.md` |
| An integrator building, replicating or handing over a multi-pipeline system for a client | This module, phase 0 onward |
| Someone with one pipeline edit or one failed run | The specific sibling below (`../../modules/pipeline-authoring/GUIDE.md` or `../../modules/validate-debug/GUIDE.md`) |

When in doubt, ask one question: "Do you want to use pipelines that exist, or build a new set of them?" A user who only uses pipelines goes to `../../modules/quickstart/GUIDE.md`. Even inside a full build, the client's end users are onboarded with `../../modules/quickstart/GUIDE.md` at handover (phase 9).

## Other modules

| Module | Load it for |
|---|---|
| `../../modules/quickstart/GUIDE.md` | Plain-language journeys for non-technical users: glossary, first setup, run a template, run with a brief and files, watch results, approve or reject, schedule, simple changes, share, get help |
| `../../modules/runners-models/GUIDE.md` | The local runner (setup, status, revoke), local and cloud model providers, choosing and checking model ids, running pipelines on the user's own machine |
| `../../modules/devices-browser/GUIDE.md` | Pairing an Android phone and a browser, allow-listing apps and sites, the read-act-verify loop, phone agents |
| `../../modules/discovery/GUIDE.md` | Setup status, connectors, tool registry, models, keyless tool catalogue, requirement-to-tool mapping, asking the user to connect services |
| `../../modules/projects-templates/GUIDE.md` | Choosing the project, browsing templates, from_template vs raw authoring, the EDIT ME convention (templates only) |
| `../../modules/pipeline-authoring/GUIDE.md` | Config schema, step kinds, agent fields, instruction patterns, provenance, tool reliability order, designed documents, mailer, run inputs, generator script, save semantics |
| `../../modules/data-spine/GUIDE.md` | The Google Sheet system of record: schema, find-or-create, file-based reads, dedupe, normalize, CSV append, write-back by row, status lifecycle, batch scoring from files |
| `../../modules/validate-debug/GUIDE.md` | Real runs, status/inspect/diagnosis/memory/approvals, artifact verification, failure patterns, speed tuning, code vs config fixes |
| `../../modules/automation-governance/GUIDE.md` | Schedules, event triggers, HITL modes and their real semantics, approvals, memory policy, cost, security and data boundaries |
| `../../modules/client-handover/GUIDE.md` | The client documentation set, per-pipeline notes, ELI5 language, mermaid flows, pilot vs production |

References in this module:
- `references/phase-checklists.md`: tick-box checklist and exit evidence for every phase.
- `references/worked-example.md`: a fully anonymized 9-pipeline system for an early-stage investment team, end to end.
- `references/system-design-template.md`: the fill-in design document you produce in phase 4.

## Operating rules (apply in every phase)

1. **Start every session with `melaya_setup_status`.** Call it again whenever a tool fails in a way that looks like setup (no runner, no key, missing grant) rather than a mistake.
2. **Never approve HITL for the user.** `melaya_approval_list` is read-only on purpose. Show what is waiting and point the user to the Melaya app or their phone. Never design around a gate to "unblock" a run.
3. **Never work around a missing connector.** If a requirement needs Gmail, Drive, Sheets or another service that is not connected, call `melaya_connector_connect` with the service id, give the user the link, and wait. Never ask for a key or token in chat; the tool has no field for one.
4. **Save = full replace.** `melaya_pipeline_save` with `mode: "update"` overwrites the WHOLE document. Omitted fields are erased. Always send the complete config (from your generator, or from `melaya_pipeline_get`).
5. **Preview before every save.** `melaya_pipeline_preview` is the only real validation: the parser silently drops unknown fields and invented tool ids. Check that every agent, instruction, tool id and setting appears in the generated code. Read back with `melaya_pipeline_get` after saving and use the canonical `name` it returns.
6. **Generate configs, do not hand-edit JSON.** One generator script owns every pipeline of the system. Fix the script, regenerate, preview, re-save.
7. **Validate with a cheap, fast cloud model.** Pick a model id from `melaya_model_list` (a fast mid-size class model, not a slow local CPU model). Never write a guessed model id into a config.
8. **Cancel runaway runs.** Runs are asynchronous and take minutes. Poll `melaya_run_status` (read `outcome`, not `status`; stop when `terminal` is true). If the tool trace shows a loop or a call budget blown, call `melaya_run_cancel` with `run_id` and `pipeline`; do not wait it out.
9. **Release discipline.** A platform bug (a tool that misbehaves, a valid field that is dropped) is not yours to patch: report it to Melaya with a minimal reproduction (pipeline name, run time, the tool call and what came back) and wait until the fix is released. Never save a config that depends on a new tool parameter before the release is live. Config and prompt bugs are fixed in the generator and re-saved.
10. **Check usage before bulk work.** `melaya_account_usage` before creating many pipelines or launching many runs, so you do not hit a plan cap mid-phase.
11. **Anonymity and secrets.** Configs, instructions, docs and bug reports never contain credentials, API keys, private document ids, internal hostnames or personal data you do not need. Credentials live under Connectors, never in a config.
12. **Verify by reading; write only on purpose.** Use `melaya_connector_call` read tools to check the Sheet rows and documents a run produced. If the user granted `melaya:connectors.write`, the same tool can also run connector write tools (send an email, create or update a record or a file): each write runs at once with no approval card and is audit-logged, so confirm it with the user first, and never use it to patch an artifact a pipeline got wrong (fix the pipeline). Anything that moves money or trades (payments, refunds, purchases, transfers, ad budget or bid changes, orders) is refused at every permission level and stays an approval in the Melaya app.
13. **What still needs the user.** With the current MCP permissions you can run a client pilot end to end (create the project, save pipelines, arm poll, webhook and stream triggers, run, act through connectors, clean up). Four things stay with the user: instant (push) triggers (create, re-point, re-enable: they create a watch on the user's own account behind a consent box), connecting a service (sign-in or key, on the Connectors page), approving anything that moves money, and copying webhook signing secrets (shown once, in the app).

## The phases

```
Non-technical user? -> `../../modules/quickstart/GUIDE.md` (stop here unless a new system is needed)
Integrator:
0 Frame -> 1 Discover -> 2 Connect -> 3 Project -> 4 Design -> 5 Author -> 6 Validate -> 7 Fix (loop to 5/6) -> 8 Document -> 9 Hand over (end users onboarded with `../../modules/quickstart/GUIDE.md`)
```

Each phase has a gate. Do not start the next phase until the gate is met and you can show the evidence. The detailed checklists are in `references/phase-checklists.md`.

### Phase 0: Frame the engagement

Goal: know what "done" means for this client before touching the platform.

- Write down the client's requirements as a numbered list (R1, R2, ...), each one a business outcome, not a tool.
- For each requirement note: who uses the output, how often, what triggers it, what it must never do (send, pay, publish without approval).
- Decide pilot vs production scope: in a pilot, outputs usually go only to the owner's own inbox and use public data; in production, every external write is gated.

Gate: a numbered requirement list the user has confirmed.

### Phase 1: Discover (load `../../modules/discovery/GUIDE.md`)

Goal: know what the account can do today.

- `melaya_setup_status`: account, plan, runner, missing pieces.
- `melaya_connector_list`, then `melaya_connector_tools` with `search` per requirement keyword.
- `melaya_pipeline_registry` with `search` (and `kind: "tools"`) for every capability you plan to use. Only ids returned here go into `agent_tools`.
- `melaya_model_list` for the provider you will validate with (and the production provider, if different). `status` must be `ok`. If the client wants models on their own machines or a local runner, load `../../modules/runners-models/GUIDE.md`; if a workflow must operate a phone or a browser, load `../../modules/devices-browser/GUIDE.md`.
- `melaya_pipeline_templates` with `search`: a validated template may cover a workflow already.
- Map every requirement to tools. Prefer KEYLESS public-data tools (registries, sanctions lists, regulators, RSS, news event feeds, filings, on-chain and code-quality data, `scrape_page`) so the system proves value before the client shares any data.

Gate: a requirement-to-tool table where every row has either a verified tool id or a named missing connector.

### Phase 2: Connect

Goal: every service the design needs is connected and working.

- For each missing service: `melaya_connector_connect` with `service`, hand the user the link, wait for them.
- Then `melaya_connector_test` with the same `service` id. A green test proves the credential; it does not prove every tool works, so also run one real read with `melaya_connector_call` (for example list a Drive folder or read one Sheet range).

Gate: `melaya_connector_test` passes and one real read succeeds for every required service. Nothing is worked around.

### Phase 3: Project (load `../../modules/projects-templates/GUIDE.md`)

Goal: one project holds every pipeline of the system.

- Create it with `melaya_project_create` (`name`, optional `description`; needs the user's `melaya:projects` permission and the Forge plan or above, counts against the plan's project limit, names are unique across Melaya). Without that permission, ask the user to create it in the Melaya app. Confirm with `melaya_project_get` or `melaya_team_list` (`project`), which also reports whether the account can invite.
- Give the pilot its own Drive folder tree too (one subfolder per pipeline, found or created by `drive_create_folder`; see `../../modules/pipeline-authoring/references/designed-documents.md`).
- If collaborators need access, confirm with the user first, then `melaya_team_invite` (`viewer` or `editor`).
- Decide per workflow: instantiate a template (`melaya_pipeline_from_template` with `template_id`, `name`, `project`, small `overrides`) or author a raw config. Client pipelines never contain `[START EDIT ME]` blocks; that convention is for templates only.

Gate: `melaya_team_list` returns the project and the account's role allows saving pipelines there.

### Phase 4: Design (fill `references/system-design-template.md`)

Goal: a written design the user approves before any config exists.

Design principles:
- **One pipeline per business workflow.** Not one mega-pipeline, not one pipeline per tool.
- **A shared data spine** (load `../../modules/data-spine/GUIDE.md`): one Google Sheet with a fixed schema is the system of record and the memory between runs. Every pipeline reads it and writes back to it. Give it a name that shares no full word set with any older file in the Drive (Drive name search is a word match).
- **Run inputs, not hard-coded subjects.** The subject (company, topic, URL, file) is a declared input or the per-run `brief` and `files`.
- **Designed documents** for every human-facing output (Doc, Sheet, deck, PDF) with a theme, published to Drive.
- **One mailer step at the end**, exactly one send, attaching file paths.
- **Provenance on every field**: value, source, status (SOURCE / INFERRED / MISSING). Never invent a number.
- **Bounded agents**: phases, a hard tool-call budget, parallel batches, a stop condition, a fixed output contract.
- **Human control**: list every write tool that leaves the system and decide its gate now.
- **Dependency order**: draw which pipeline feeds which through the spine; that is also the validation order.

Gate: the user approves the design document (requirements -> workflows -> pipelines -> data spine -> run inputs -> outputs -> governance).

### Phase 5: Author via a generator script (load `../../modules/pipeline-authoring/GUIDE.md`)

Goal: every pipeline config is emitted by one script from shared constants.

Generator structure:
- Shared constants: project, model provider/name, spine name and tab, the client's thesis or policy text, the provenance rule, the row rule, the tool reliability order, the design/theme rule, human writing rules, the one-shot-send rule.
- Factories: `agent(...)` (embeds `instruction`, `agent_tools`, `human_approval_tools`, `model_provider`, `model_name`, `include_context`, `loop_policy` as an OBJECT `{"mode": "observe_only", "evaluator": "default"}`), `pipeline(...)` (builds `steps[]` with agents EMBEDDED in each step, edges, `hitl_mode`, `persistent_memory`, `schedule`, `inputs`), `recorder(...)`, `mailer(...)`.
- One call per pipeline, writing `cfg/<slug>.json`.

Save loop per pipeline:
1. Regenerate all configs.
2. `melaya_pipeline_preview` with the config. Confirm every agent, tool id, instruction and setting survived; fix the generator if anything was dropped.
3. `melaya_pipeline_save` (`mode: "create"` first time, `"update"` after, with `pipeline` = canonical name and `project`).
4. `melaya_pipeline_get` and compare with what you sent.
5. If the config sets `schedule`, arm it later (phase 9) with `melaya_pipeline_schedule` `action: "set"`.

Gate: every pipeline is saved, previewed clean, and read back identical to the generator output.

### Phase 6: Validate on real runs (load `../../modules/validate-debug/GUIDE.md`)

Goal: each pipeline produces a correct real artifact on real data.

- Run in dependency order (producers of spine rows first, consumers after).
- `melaya_pipeline_run` with `pipeline`, `project`, a realistic `brief`, and `files` or `inputs` where the pipeline takes them.
- Poll `melaya_run_status` until `terminal`; judge by `outcome`.
- `melaya_run_inspect` with `include_tool_calls: true` to see every call, argument and result. Look for retyped big data, repeated failing calls, guessed URLs, budget overruns.
- `melaya_run_diagnosis` for eval verdicts, tool forensics and cost.
- `melaya_approval_list` with `run_id` if the run is paused on a gate: tell the user, do not approve.
- `melaya_agent_memory` if `persistent_memory` is on: check nothing stale is being replayed.
- Verify the artifact itself, not the agent's claim: read the spine rows and open the produced documents with `melaya_connector_call` (read-only).

Gate: for every pipeline, one run with `outcome` success, a verified artifact (rows correct and aligned, provenance filled, document readable), the expected number of sends, and an acceptable duration and cost.

### Phase 7: Fix (code vs config), then loop back

Classify every defect before fixing it:

| Symptom lives in | It is a | Fix | Then |
|---|---|---|---|
| Instruction, tool choice, argument shape, budget, step order, gate list | Config/prompt bug | Edit the generator, regenerate, preview, save `update` | Re-run the pipeline |
| A tool returns wrong data, crashes, ignores a documented parameter, platform drops a valid field | Platform bug | Report it to Melaya with a minimal reproduction; meanwhile work around it in config only if the design allows | Wait for the release; only then save configs that use the fix; re-run |
| Credential expired or scope missing | Setup gap | `melaya_connector_connect` / `melaya_connector_test` with the user | Re-run |
| Model drops or shifts cells, invents values | Design bug | Move the data through files (save_to, csv_path, items_path), never through the model's retyping | Re-run |

After a tool fix, check `melaya_agent_memory`: failure notes written before the fix can poison later runs. Ask the user before clearing memory or turning `persistent_memory` off.

Gate: every defect found in phase 6 is closed and the affected pipelines re-validated.

### Phase 8: Document (load `../../modules/client-handover/GUIDE.md`)

Goal: a non-technical client can understand, run and trust the system.

Set: a hub page, how a pipeline works (ELI5 glossary), run inputs/schedules/triggers, tools catalogue, data model and provenance, designed documents and branding, security and human control, and one templated note per pipeline (one-sentence summary, goal, when and how to run, inputs, mermaid flow, step table, tools deep-dive, outputs, quality and trust, human control, a real example from a validation run, pilot vs production, links).

Gate: every pipeline has its note, every example comes from a real validated run, and no internal detail (ids, keys, hosts) appears.

### Phase 9: Hand over (load `../../modules/automation-governance/GUIDE.md`)

Goal: the system runs on its own, safely, and the client knows how to operate it.

- Arm schedules: `melaya_pipeline_schedule` `action: "set"` with `cron`, `timezone`, and `requires_runner: true` for runner-executed pipelines; confirm with `action: "status"`.
- Create event triggers where needed: `melaya_pipeline_trigger` `action: "create"` (webhook, wss, engine, poll). Triggered runs are forced to safe HITL. Signing secrets are never returned over MCP and the app shows a secret only once: for a webhook created or rotated over MCP the user presses **Rotate secret** on the trigger in the app and copies the new one into the sender. Push triggers and autonomy are UI-only; guide the user with `../../modules/automation-governance/references/triggers-ui-walkthrough.md`.
- Switch production gates on: every external send or write that leaves the system is in the agent's `human_approval_tools` with pipeline `hitl_mode: "safe"`.
- Invite the client team to the project (with the user's confirmation).
- Hand over the documentation and a short "first week" runbook: what runs when, where approvals appear, how to run with inputs, who to call. Base the end-user part on the journeys in `../../modules/quickstart/GUIDE.md` (run with inputs, watch results, approve or reject, schedule, get help), so the client's team can work through their own AI assistant.
- Baseline quality with `melaya_eval_report` (`project`, `range`).
- Clean up validation leftovers with the user's agreement: test rows (`sheets_delete_rows`, which leaves no blank gap), test triggers, and throwaway projects (`melaya_project_delete` is a dry run until `confirm: true`; it removes only an empty project the user created: no pipelines, no run history, no other members, not their only project; there is no cascade).

Gate: the definition of done below is fully ticked.

## Definition of done (whole system)

- [ ] Every confirmed requirement maps to a pipeline, a tool, or an explicit "out of scope" line.
- [ ] Every required connector passes `melaya_connector_test` and a real read.
- [ ] All pipelines live in one project and are emitted by one generator script kept with the engagement files.
- [ ] Every config previews clean and reads back identical to the generator output.
- [ ] Every agent has `loop_policy` as an object, so runs produce eval scores.
- [ ] Every pipeline has one validated real run: success outcome, verified artifact, correct send count, acceptable time and cost.
- [ ] The data spine has a fixed schema, aligned rows, provenance columns filled, and no duplicates from validation runs (or they are marked).
- [ ] Every external write is gated in production (`hitl_mode: "safe"` + `human_approval_tools`), and any pilot exception is written down and agreed.
- [ ] Schedules armed and confirmed with `status`; triggers created and tested with `action: "test"`.
- [ ] No `[START EDIT ME]` blocks, no secrets, no private ids in any config or document.
- [ ] Client documentation delivered, one note per pipeline, examples from real runs.
- [ ] Open platform issues listed with their release status.

## Compact system-design template

Fill this in phase 4 (full version with examples in `references/system-design-template.md`).

```
SYSTEM: <name>            CLIENT: <Client>        PROJECT: <project>
MODE: pilot | production  VALIDATION MODEL: <provider/model from melaya_model_list>

1. REQUIREMENTS
   R1 <business outcome> | user: <who> | cadence: <when> | never: <forbidden action>
   R2 ...

2. WORKFLOWS (one per business process)
   W1 <name>: trigger <manual|cron|event> -> <steps in plain words> -> <output>  (covers R1, R3)

3. PIPELINES (one per workflow)
   P1 <slug> | steps: <agent> -> [<parallel a>, <parallel b>] -> <recorder> -> <mailer>
      tools per agent: <verified ids>  | gated tools: <ids>  | reads spine: y/n | writes spine: <columns>

4. DATA SPINE
   store: Google Sheet "<unique name>" tab "<tab>" | key: <dedupe key>
   columns: <field>, <field>__src, <field>__status ... | status lifecycle: new -> screened -> ... -> closed
   writers: P1 (append), P3 (write-back cols X:Z by [row N]) | readers: P2, P4

5. RUN INPUTS
   P1: brief (free text) + inputs [{key, type, required}] + files (deck, logo)

6. OUTPUTS
   P1: spine rows | Doc "<title>" (theme) | email to <owner inbox | team>, 1 send, attachments = FILE paths

7. GOVERNANCE
   hitl_mode: safe | gated: gmail_send, ... | schedules: P3 "0 7 1 * *" <tz> | triggers: <kind -> pipeline>
   memory: spine only | persistent_memory: off unless the last step's reply is worth replaying
   data boundary: public data + connected services only | cost ceiling per run: <n>
```

## Anti-patterns that cost the most time

- Designing before discovery: tool ids that do not exist are silently dropped at save.
- Asking the model to copy a wide row or a long list: it drops or shifts cells. Move data through files.
- "Use every source" research prompts: 100+ sequential calls. Use bounded phases, batch triage, a hard budget.
- Guessing page paths (`/about`, `/legal`) with `scrape_page`: use `scrape_links` on the home page.
- Hand-patching one pipeline's JSON: the next regeneration silently reverts it.
- Saving an `update` built from a partial config: erased fields.
- Validating with a slow local model: minutes per turn, and you learn nothing faster.
- Approving a gate "to test the send": never. Ask the user.
- Leaving stale failure notes in crew memory after a tool fix.
