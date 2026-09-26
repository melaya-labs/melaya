<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when writing, generating, fixing or re-saving a Melaya pipeline config through the Melaya MCP server (melaya_pipeline_preview / melaya_pipeline_save / melaya_pipeline_get). Covers the steps[] schema and the exact run-time behaviour of the five step kinds (agent, parallel, condition, loop, decide) and of edges, per-agent fields and their builder UI equivalents, approvals (hitl_mode) as they really behave, the quality loop (observe vs enforce, side-effect classes), static context (inline docs, uploaded docs, local folder), retrieval, persistent memory (markers, seed, inspect, reset), run inputs and their limits, code vs no-code pipelines, costs, limits and languages, instruction-writing patterns, provenance, file-based data handoff, designed and branded documents, the mailer pattern and generator scripts. Load it after discovery and before the first save of any pipeline.

# Melaya pipeline authoring

This module turns a designed workflow into a pipeline config that the Melaya builder compiles and runs
as you intended. It assumes discovery is done (connectors, tools, models: `../../modules/discovery/GUIDE.md`) and a
project exists (`../../modules/projects-templates/GUIDE.md`). Where it runs and which models: `../../modules/runners-models/GUIDE.md`.

Plain words used below:
- **Pipeline**: a saved chain of steps; each step runs one or more AI agents.
- **Config**: the JSON document that describes a pipeline. The builder turns it into a program on every save.
- **Agent**: one AI worker with an instruction, a model and a short list of tools.
- **Run input**: a brief or files given to ONE run.
- **Approval (HITL)**: the run pauses on a tool call until a person approves it.
- **Canonical name**: the id the save returns; the only name that addresses the pipeline afterwards.

References (load as needed):
- `references/config-schema.md` - every field, its behaviour and its builder UI control; approvals; save semantics; code vs no-code; what to check in the preview.
- `references/step-kinds.md` - exact behaviour of agent, parallel, condition, loop, decide, and of edges.
- `references/quality-loop.md` - evals: observe vs enforce, side-effect classes, tool forensics.
- `references/context-and-memory.md` - static context, retrieval, persistent memory (seed, inspect, reset).
- `references/run-inputs.md` - brief, files, declared inputs, placeholders, limits, run calls.
- `references/limits-costs-languages.md` - time caps, tool timeouts, sizes, costs, schedule limits, languages.
- `references/instruction-patterns.md` - instructions that small, cheap models follow.
- `references/designed-documents.md` - Word, Sheets, Slides, PDF, Drive publishing, brand themes.
- `templates/build_configs.py` - a generic generator script. Copy it, rename the constants, add pipelines.

---

## 1. The authoring loop (never skip a step)

1. Search real tool ids: `melaya_pipeline_registry` with `search` (and `kind: "tools"`). An invented id is silently dropped on save. Note each tool's `read_only` flag.
2. Check model ids: `melaya_model_list` with `provider`. A guessed id saves fine and fails at run time.
3. Generate the config from a script (section 12), never by hand-editing JSON.
4. `melaya_pipeline_preview` with the full config. Read the audit at the top and the generated program: every step as a phase, every agent with its tools and model, a quality-loop scoring call after each agent. Anything missing was dropped (checklist: config-schema.md section 6).
5. `melaya_pipeline_save`:
   - first time: `mode: "create"`, `project: "<Project>"`, `config`.
   - afterwards: `mode: "update"`, `pipeline: "<canonical name>"`, `project`, `config` = the WHOLE document.
6. Record the canonical `name` from the save response.
7. `melaya_pipeline_get` and compare with what you sent.
8. If the config has `schedule`, arm it: `melaya_pipeline_schedule` with `action: "set"`, `cron`, `pipeline`, `project` (`../../modules/automation-governance/GUIDE.md`).
9. If agents need uploaded documents, ask the user to add them (context-and-memory.md section 1).
10. Validate with a real run (`../../modules/validate-debug/GUIDE.md`). Fix the generator, regenerate, re-preview, re-save.

Save is a FULL-document replace. There is no patch. Omitted fields are erased.
Credentials never go in a config: services are connected under Connectors.
Projects are created with `melaya_project_create` (needs the user's `melaya:projects` permission) or in the app (https://app.melaya.org).

## 2. Design rules before you write a line

| Rule | Why |
|---|---|
| One pipeline per business workflow (sourcing, intake, screening, DD, memo, forms, copilot, monitor) | Each can be run, scheduled, triggered and validated alone |
| The subject is a run input (brief, files, declared inputs), never hard-coded | One pipeline serves every company or topic |
| Every pipeline also works with NO brief (a default source) | Schedules and triggers carry no brief |
| A shared data store (a Google Sheet with a fixed schema) is the memory between pipelines | Models forget; a sheet does not. See `../../modules/data-spine/GUIDE.md` |
| Every human-facing output is a designed document | Clients judge quality by the artifact |
| One mailer step at the end, if anyone must be told | One send, attachments, no duplicates |
| At most about 2 data-carrying hops between the step that fetches a value and the step that prints it | Field-level data survives one hand-off, rarely three |
| Prefer keyless public-data tools for proof of value | Runs work before the client connects anything |
| Stay no-code: every behaviour lives in the config | Hand-edited programs lose run inputs and are overwritten on the next save (section 16) |

## 3. Config skeleton

```json
{
  "name": "Acme - Deal Intake",
  "display_name": "Acme - Deal Intake",
  "description": "What it does, what it needs, what it delivers.",
  "project": "Acme",
  "steps": [
    {"id": "s1", "kind": "agent", "label": "Intake Analyst", "agent": { ...agent... }},
    {"id": "s2", "kind": "parallel", "label": "Lenses", "joinStrategy": "concat", "agents": [ {...}, {...} ]},
    {"id": "s3", "kind": "agent", "label": "Mailer", "agent": { ...agent... }}
  ],
  "edges": [],
  "tools": [],
  "schedule": "",
  "hitl_mode": "safe",
  "persistent_memory": false,
  "user_lang": "en",
  "model_provider": "qwen",
  "model_name": "qwen3.7-plus"
}
```

- Agents live EMBEDDED in `steps[]` (`step.agent`, or `step.agents[]` for parallel). The builder reads those copies. Do not author the flat top-level `agents[]` list; if an existing config has one, it is only a mirror: edit the step copies.
- `edges: []` = steps run in list order and every kind compiles. Non-empty edges switch to graph routing, which silently skips `condition` and `loop` steps (step-kinds.md section 6).
- `tools: []` always: an agent whose `agent_tools` is empty INHERITS this whole pool (a synthesis agent once inherited a send tool and emailed twice).

## 4. Step kinds (summary; exact behaviour in step-kinds.md)

| kind | Shape | Use it for |
|---|---|---|
| `agent` | `{"kind":"agent","agent":{...}}` | One agent, once. The default |
| `parallel` | `{"kind":"parallel","agents":[...],"joinStrategy":"concat"}` (`summary`, `first` also exist) | Independent lenses on the same input |
| `decide` | `{"kind":"decide","decide":{"engine":"laya","questions":{...},"act_when":{...},"on_unavailable":"skip"}}` | A free System One gate that ends the run when there is nothing to do. 1-8 questions, judges the first 4,000 chars of the previous output, never the first step (except on trigger-started runs) |
| `condition` | `{"kind":"condition","rules":[{"keyword","label","agent"}],"defaultAgent":{...}}` | Keyword routing on the previous reply (case-insensitive substring, first match wins) |
| `loop` | `{"kind":"loop","agent":{...},"maxIterations":3,"stopKeyword":"DONE"}` | Genuine item-by-item iteration only; output = last iteration only |

Parallel join caveat (the most common silent bug): the next step receives ONLY the parallel agents' replies. Lines from the step before the block are gone, and siblings never see each other. Tell one (or every) parallel agent: "Start your reply with the TARGET, SHEET and ROW lines of the previous message copied unchanged (the next step only sees your reply)."

## 5. Agent fields (summary; UI equivalents in config-schema.md section 2)

| Field | Value | Notes |
|---|---|---|
| `name` | "Deal Scout" | Unique per pipeline |
| `role` | "Venture sourcing analyst" | Short persona |
| `instruction` | the brief (SINGULAR) | `instructions` is not read |
| `agent_tools` | `["rss_search_entries", "decide_batch"]` | Always explicit and minimal. Empty = inherits `tools`. `["__none__"]` = no tools |
| `human_approval_tools` | `["gmail_send"]` | Gated only when `hitl_mode` is `"safe"` |
| `model_provider` / `model_name` | `"qwen"` / `"qwen3.7-plus"` | Per agent; falls back to the pipeline model |
| `include_context` | `false` unless the agent needs the static documents | Defaults to TRUE when absent |
| `include_rag_tool` | `false` unless retrieval is on and the agent should search | Defaults to TRUE when retrieval is on |
| `loop_policy` | `{"mode":"observe_only","evaluator":"default"}` | On EVERY agent. Object only; a string is ignored |
| `system_prompt_override` | optional | Replaces a catalog persona that fights the instruction, in every UI language |

camelCase twins are accepted; snake_case wins. Writing both is harmless (the generator does, for UI round-trips).

## 6. Approvals, as they really behave

- `hitl_mode: "safe"` gates exactly the tools in each agent's `human_approval_tools`. Reads and Sheet or Drive writes run freely.
- `"autonomous"` and `"payments_only"` DROP the per-agent lists (only a few form tools stay gated), whatever some descriptions say.
- So any gated send needs `hitl_mode: "safe"`. Gate every external write (send, post, invite, create in someone else's system). Do not gate local artifacts (`word_create`, `excel_write_data`).
- Runs started by event triggers are forced to safe and gate EVERY non-read-only tool, listed or not.
- A demo that only emails the owner's own inbox may run ungated deliberately; say so in the generator.
- Never approve a gate on the user's behalf. Depth: `../../modules/automation-governance/GUIDE.md`.

## 7. Quality loop (summary; depth in quality-loop.md)

- `observe_only` on every agent: scores each reply and its tool calls (auth errors, false "unavailable" claims, stalls, unused send tools) for the Eval page and `melaya_eval_report`. Never changes or retries anything.
- `enforce`: retries a failed step with a lesson, `maxAttempts` 1-5 (default 2). Add `"sideEffectClass": "non_idempotent_write"` on any agent that sends, posts or appends: it is then never retried. On parallel and loop steps enforce only observes.
- Builder UI: agent card, **Quality loop** Off / Observe / Enforce.

## 8. Knowledge and memory (summary; depth in context-and-memory.md)

| Need | Config | Who adds it |
|---|---|---|
| Rubric, thesis, brand voice every relevant agent reads | `inline_rag_docs: [{"title","body"}]` + `include_context: true` on those agents | You, over MCP |
| Reference files (PDF, Word, Excel ...) | Docs tab upload | The user, in the app (no MCP upload) |
| Documents that must stay on the user's computer | `static_context_local_folder_path` + a local provider on EVERY agent | You set the path; the folder is on the user's machine |
| A large corpus searched on demand | `rag_mode_retrieval` + embedder fields | You + the user (documents) |
| Remember across runs (de-duplicate) | `persistent_memory: true` + marker lines such as `USED: <item>` | You |

Caps: 50,000 characters per document, 60,000 in total. Memory markers: `AVOID:`, `LEARNED:`, `REMEMBER:`, `USED:` and a few more; tool failures are captured automatically and fade. Inspect with `melaya_agent_memory`; the user deletes stale entries in the builder's **Memory** tab.

## 9. Instruction-writing patterns (summary)

Full catalogue with examples: `references/instruction-patterns.md`.

1. Context first (thesis, target), then numbered PHASES, then the exact OUTPUT CONTRACT.
2. Hard budget: "HARD BUDGET: at most 40 tool calls in total."
3. Parallel batches: "Call tools in parallel batches (all calls of a phase in ONE turn)."
4. Stop at target: "Stop searching the moment you have TARGET items."
5. Triage in one free call: harvest candidates, then ONE `decide_batch` (or `decide_batch_file`) call, then confirm only the best.
6. Output contracts with fixed line prefixes the next step parses: `RECORDS:`, `SUMMARY:`, `CITE:`, `TARGET:`, `SHEET:`, `ROW:`, `CHECK <name>:`, `DOC:`, `FILE:`.
7. "ALWAYS in exactly this format, even when you found nothing (then `RECORDS: []` and say why in SUMMARY)."
8. Anti-stall: no questions, no options, no "let me know". Act on the defaults this turn.
9. Spell out argument shapes: `items = a JSON array of strings`, `values_json = [["screened", 7.5, "2026-01-31"]]`.
10. Do not restate what the platform adds: the date, the run inputs, tool rules, static context.
11. Skip and report a missing connector; a rejected approval is "not sent", never a connection failure.

Speed evidence: a "use every source" prompt made one agent do 110 sequential calls in 28 minutes. Rewritten as one parallel batch of feeds, one `decide_batch` triage and one parallel batch of confirmations, with a 40-call budget and stop-at-target, it took 7 minutes for 15 real records. Runs stop after about 30 minutes of work.

## 10. Tool reliability order (paste into every research instruction)

1. A registry or data tool that answers directly (e.g. gleif_*, edgar_*, sanctions_*, mica_*, defillama_*, github_*, webtraffic_*).
2. A KNOWN URL read with `scrape_page` (`web_fetch` if it fails): the company site, a register page, an article you already have.
3. RSS (`rss_search_entries`) and GDELT (`gdelt_search_articles`) for news.
4. `web_search` ONLY to discover a URL, then open it with `scrape_page`; use the page, never the snippet.

Notes:
- `web_search` tries the calling agent's own provider search first, then Bing News RSS, then HTML engines. Keyless HTML engines block scripted clients, so do not depend on step 4.
- Targeted Bing News RSS feeds are reliable and keyless: `rss_search_entries` with `url = "https://www.bing.com/news/search?format=rss&q=<words+joined+by+plus>"`, `keyword`, `limit`.
- `scrape_page` on a missing path returns "HTTP 404 page not found" (sites often serve the home shell there). Do not guess `/about`, `/legal` repeatedly: call `scrape_links` on the home page and follow real links.
- `gdelt_search_articles` covers the last 3 months only (`timespan` such as "1m", "3m").
- A tool that fails 5 times in a row is switched off for about a minute; instruct a fallback.

## 11. Provenance, files and documents

Provenance rule (paste into every agent that fills fields): "For every field you fill, also set `<field>__src` (a URL, `<file name> p.N`, or `computed: <how>`) and `<field>__status`: SOURCE, INFERRED or MISSING. Never invent a number. A failed or unavailable source is MISSING, never 'clear' or 'no traction'." In prose: `[SOURCE: <src>]`, `[INFERRED: <how>]`, `[MISSING]`.

Never make the model retype big data. Move data through FILES:

| Need | Do | Never |
|---|---|---|
| Read a sheet | `sheets_read_range` with `save_to = "deals.json"`; read `data_rows` from the summary | copy the table into the reply |
| Score many rows | `decide_batch_file` with `items_path`, `item_template`, `include_if`; rows come back `[row N] ...` | build a 100-item array by hand |
| Append rows | `dealdb_normalize ... save_to = "new.csv"` then `sheets_append_row` with `csv_path` | `row_json` for wide rows |
| Write back cells | `sheets_update_by_header` by column NAME, all rows in one call | column letters (a wrong letter overwrote status and score) |
| Attach a document | the FILE path in `gmail_send attachments` | paste the document into the body |

Details: `../../modules/data-spine/GUIDE.md`.

Designed documents (full reference: `references/designed-documents.md`): `word_create` with blocks (cover with badge, toc, kpis tiles, heading, paragraph, bullets, callout with a tone, table with `status_col`, inline chart) and `theme`, `export_pdf = true` for a matching PDF, then `drive_upload` with `mime_type = "application/vnd.google-apps.document"` and a `folder`; `excel_write_data`, `pptx_create`, `pdf_from_html` likewise. Theme `"melaya"` by default or a client dict (`"logo": ""` removes the Melaya logo). Every writer reports `DOC: <link>` and `FILE: <path>`.

Numbers the model must not compute: a composite score is computed by a tool (`dealdb_rank` with `components` and `component_max`), never by the model; a model once skipped a x2.5 scaling and printed raw 0-4 scores as /10. Write-back goes by column NAME (`sheets_update_by_header`), never by letter (`../../modules/data-spine/GUIDE.md`).

## 12. The mailer pattern (last step)

```
The previous message is the finished work of this run. Send it to the team.
1. Invoke gmail_my_address and send to that address.
2. Compose from the previous message only. Do not add facts.
   subject = <one-line subject with the real values>
   body = <what to include, in what order>
   <writing rules>
   attachments = every path on a "FILE:" line of the previous message (none if there is none).
3. Invoke gmail_send exactly ONCE. A second gmail_send call is a failure.
   Your final reply MUST be the raw result text of gmail_send.
subject and body are TWO DIFFERENT fields. Never copy body text into the subject.
If the previous message reports a tool failure or a service that is not connected, send a short
body naming the service and where to reconnect it, never a raw error or JSON.
If gmail_send is rejected at approval, reply "not sent (rejected at approval)" and stop.
```

Tools: `["gmail_my_address", "gmail_send"]`. Gate `gmail_send` for anything that leaves the owner's inbox.
`gmail_send` also refuses a second send with the same recipient and subject in one run, so a re-planning agent cannot double-send; keep the one-send rule in the instruction anyway.
Writing rules for anything a person reads: plain ASCII punctuation, no em or en dashes, no AI tells (synergy, seamless, leverage, game-changer, delve, robust), short sentences, numbers with their source.

## 13. Generate configs from a script

Copy `templates/build_configs.py`. It holds shared constants (`THESIS`, `WRITING_RULES`, `PROVENANCE`, `RELIABLE`, `DESIGN` + `BRAND_THEME`, `ROW_RULE`, `ONE_SHOT_SEND`, `MODEL`, `PROJECT`, `STORE_NAME`, `TAB`), factories (`agent()`, `recorder()`, `mailer()`, `pipeline()`), and writes `cfg/<slug>.json` (readable) and `cfg/<slug>.min.json` (compact, for the MCP call). `pipeline()` keeps `edges` and `tools` empty and puts `loop_policy` on every agent.

Workflow: edit the script, run `python build_configs.py`, preview each `.min.json`, save with `mode: "update"`. Change a shared rule once and every pipeline gets it. Never hand-patch the saved JSON; the next regeneration would lose it.
Deploy discipline: if a config relies on a new tool parameter, save it only after that platform change is live.

## 14. Model choice per agent

| Agent type | Choice |
|---|---|
| Validation runs, most production steps | A cheap fast cloud model with reliable tool calling (e.g. `qwen` / `qwen3.7-plus`) |
| Heavy synthesis (memo writer) if quality lacks | A stronger model on that one agent only |
| Triage or scoring of many items | Not an LLM: `decide_batch` / `decide_batch_file` (free) or a `decide` step; `jev_*` is the paid hosted twin |
| Files must never leave the machine | A local provider (`lmstudio`, `ollama`, `claude_code`, `codex`, `github_copilot`) on EVERY agent, plus `static_context_local_folder_path` |
| The client requires its own provider or its own model server | Every pipeline and agent can use the provider the client wants, or the client's own model server reached through the runner, so data never leaves its infrastructure. Say where the model runs in the client's security documentation |

Any local provider on any agent sends the whole pipeline to the user's runner (files as run inputs are then refused). Always confirm ids with `melaya_model_list`; for local providers and the runner see `../../modules/runners-models/GUIDE.md`.

## 15. Run inputs and languages (summary)

- Every pipeline accepts a `brief` (8 KB) and up to 10 `files` per run, no declaration needed; every agent sees them in a "Run inputs" block. Declared `inputs` add typed fields and `{{inputs.<key>}}`; `{{brief}}` inserts the brief. Read files with `read_run_input` or `deck_extract`. Files reach cloud runs only; schedules carry no inputs. Full reference: `references/run-inputs.md`.
- `user_lang` supports `en`, `fr`, `zh`, `es`, `pt`. For any other language, write "Write every output in <language>" into each writing agent's instruction (limits-costs-languages.md).

## 16. Code vs no-code

- Normal pipelines are no-code: the program is regenerated from the config on every save.
- The builder's **Code** tab allows manual edits only for accounts an administrator granted code editing.
- A later config save regenerates the program and overwrites hand edits. A fully hand-written program is kept but refuses runs with inputs, gets trigger events only as a file, and loses any wiring it does not reimplement.
- For client systems: never hand-edit code. Change the generator, regenerate, re-save.

## 17. Procedures that need the user (say this, expect this)

| Situation | Say to the user | Success looks like | If it fails |
|---|---|---|---|
| Reference files must be attached | "Open https://app.melaya.org/builder, open <pipeline>, **Docs** tab, drop <files>. Tell me when they are listed." | `melaya_pipeline_get` lists them with non-zero loaded characters | A scanned PDF loads almost nothing: ask for a text version or paste key content as an inline doc |
| A stale memory note steers runs | "In the builder, open <pipeline>, **Memory** tab, open the persistent memory node and delete these entries: <list>." | `melaya_agent_memory` no longer returns them | Only owners and editors can delete; ask the owner |
| A session-bound tool (LinkedIn, Luma) is needed | "In **Configure**, turn on **Run Locally**, and keep the Melaya runner running on your computer." | Runs start on the runner | See `../../modules/runners-models/GUIDE.md` |
| A gated send is waiting | "An approval card is waiting in the app (or on your phone). Please review the exact text and approve or reject it." | The run continues | Never approve for them |

## 18. Pitfalls checklist (run before every save)

- [ ] Every tool id came from `melaya_pipeline_registry`; every model id from `melaya_model_list`.
- [ ] `tools: []`, `edges: []` (unless a real graph without condition or loop steps).
- [ ] Every agent has explicit `agent_tools`, `instruction` (singular), `loop_policy` object, deliberate `include_context`.
- [ ] Every agent after a parallel block gets the lines it needs (copy-forward instruction present).
- [ ] Every research agent has phases, a hard tool budget, stop-at-target and an output contract.
- [ ] No wide data is retyped: `save_to`, `items_path`, `csv_path`, file attachments.
- [ ] Every human-facing output is a designed document with DOC and FILE lines.
- [ ] Exactly one mailer, one `gmail_send`, gated when it leaves the owner's inbox, `hitl_mode: "safe"`.
- [ ] No hard-coded subject; the no-brief default path exists. No `[START EDIT ME]` blocks (template-only convention).
- [ ] No credentials, private file ids or personal emails in the config.
- [ ] `persistent_memory` is false unless de-duplication across runs is needed and marker lines are instructed.
- [ ] Output language set (`user_lang` or an explicit instruction).
- [ ] Preview shows every step, agent, tool, model and scoring call; save used `mode: "update"` with the whole document; the canonical name was recorded.
