<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when a Melaya pipeline (or a whole multi-pipeline agentic system) has been saved and must be proven on real runs, or when a run failed, stalled, was slow, produced wrong or empty artifacts, or looks "done" but did nothing. Covers the run/poll/inspect/diagnose loop over the Melaya MCP tools (melaya_pipeline_run, melaya_run_status, melaya_run_inspect, melaya_run_diagnosis, melaya_agent_memory, melaya_approval_list, melaya_eval_report, melaya_run_cancel), dependency-ordered validation of a system, read-only artifact verification with melaya_connector_call, speed tuning, deciding platform defect vs config/prompt defect, reporting a platform defect to Melaya, and explaining results to non-technical users. Not for first-time authoring (use `../../modules/pipeline-authoring/GUIDE.md`) or schedules/triggers (use `../../modules/automation-governance/GUIDE.md`).

# Melaya: validate and debug pipelines on real runs

A saved pipeline proves nothing. A clean `melaya_pipeline_save` only means the
parser accepted the JSON; a finished run only means the orchestrator stopped.
Validation means: the run's `outcome` is success, every tool call returned real
data, and the artifact the client will look at (Sheet rows, Google Doc, email)
exists and is correct. This module is the loop that gets you there.

Reference files (load when needed):

| File | Load when |
|---|---|
| `references/failure-catalogue.md` | Any symptom you need to map to a root cause and fix |
| `references/speed-tuning.md` | A run takes more than ~10 minutes or makes more than ~40 tool calls |
| `references/artifact-verification.md` | Checking Sheet rows, Docs, Drive files, sent mail after a run |
| `references/fix-handoff.md` | You must decide config vs platform defect, or report a platform defect to Melaya |
| `references/reading-results.md` | Explaining `melaya_run_diagnosis` or `melaya_eval_report` to a non-technical user |

## Hard rules

1. **Read `outcome`, not `status`.** Every finished run is normalised to
   `status: "done"`. Success, failure and cancellation live in `outcome`.
   `terminal` says whether to keep polling.
2. **Never approve HITL on the user's behalf.** `melaya_approval_list` is
   read-only by design; there is no approve tool over MCP and you must not look
   for another route (browser, phone, API). Show the user what is waiting and
   tell them to decide in the Melaya app or on their phone.
3. **Verify the artifact, not the agent's claim.** An agent reply saying "15
   rows appended" is a claim. Read the Sheet with `melaya_connector_call`.
4. **Artifact checks are read-only.** Use only read tools with
   `melaya_connector_call`. Even when the user granted `melaya:connectors.write`
   (which lets it run write tools), never "fix" data by hand from the MCP side;
   fix the pipeline and re-run. Money-moving tools are refused over MCP anyway.
5. **Cancel runaways.** A run that is looping, retyping data or past its budget
   gets `melaya_run_cancel`, not patience. Work already done is not undone.
6. **Fix at the source.** Config/prompt defect: regenerate the config from the
   generator script and re-save (full document). Platform defect: report it to
   Melaya with evidence (`references/fix-handoff.md`) and wait for the fix to
   be live. Never hand-patch saved JSON as the permanent fix.
7. **Validate on a cheap, fast cloud model** (see "Validation model" below),
   never on a slow local/CPU model, and never with the client's real sends
   going to external recipients.

## Pre-flight (once per session)

1. `melaya_setup_status` - account, runner, readiness. Re-call it whenever a
   tool fails with a setup-shaped error (auth, no runner, not connected).
2. `melaya_account_usage` - confirm monthly and concurrent run headroom before
   a validation campaign; a system of 8 pipelines with 2 or 3 re-runs each is
   20+ runs.
3. `melaya_pipeline_list` with the project - get the canonical pipeline names.
   Only canonical names address a pipeline.
4. `melaya_pipeline_get` for each pipeline under test - confirm the saved
   config is what you think (agents embedded in `steps[]`, `loop_policy`
   objects present, `hitl_mode`, `persistent_memory`, `inputs`). Without a
   `loop_policy` object on the agents, runs produce no eval scores.
5. `melaya_connector_list` - every connector the pipelines use is connected.
   A missing grant is a user action (`melaya_connector_connect` gives the
   link); do not work around it.

## Validation model

Validate with a cheap fast hosted model (a Qwen "plus" class model on the
`qwen` provider is a good reference choice). Confirm the exact id first:

```
melaya_model_list { "provider": "qwen" }
```

- `status: "ok"` - pick the id from the live list; never guess an id, the
  builder accepts a wrong one and the run fails at execution time.
- `status: "no_key"` / `"invalid_key"` - ask the user to connect or replace the
  key under Connectors. Do not switch to a local CPU model to dodge it: runs
  take minutes per turn and hide real failures behind timeouts.

If the production model differs, validate the logic on the cheap model first,
then do ONE confirmation run on the production model before handover.
Cheap models are also the harshest test of instructions: if a Qwen-class model
drops cells or loops, a stronger model will do it too, just less often. Fix the
structure (see failure catalogue), do not paper over it with a bigger model.

## The validation loop (one pipeline)

```
start -> poll -> (runaway? cancel) -> terminal -> outcome
      -> diagnosis -> inspect tool calls -> approvals -> memory
      -> verify artifacts -> classify defect -> fix -> re-run
```

### 1. Start the run

```
melaya_pipeline_run {
  "pipeline": "<canonical name>",
  "project":  "<project>",
  "brief":    "Validation run. Screen at most 5 records. Email only the owner.",
  "inputs":   { "company": "Acme", "deck": { "url": "https://..." } },
  "files":    [ { "url": "https://.../sample.pdf" } ]
}
```

- `brief` (max 8 KB) is seen by every agent; use it to bound the validation
  run (small TARGET, owner-only email) without changing the saved config.
- `inputs` must match the declared inputs (`melaya_pipeline_get` lists them).
  Unknown keys, a missing required input or a wrong type fail before anything
  runs: that is a config mismatch, not a platform bug.
- `files`: up to 10, each `{url}` or `{base64, name}` (base64 up to 7 MB, URL
  up to 25 MB). Files reach CLOUD runs only; a `force_local_runner` pipeline
  will not see them.
- Record the returned run id. Runs are asynchronous.

### 2. Poll with a sane cadence

`melaya_run_status { "run_id": "<id>" }`

| Elapsed | Poll every | Also do |
|---|---|---|
| 0 to 2 min | 30 s | First poll confirms it started (not queued forever) |
| 2 to 10 min | 60 s | One `melaya_run_inspect` (no tool calls) to see progress |
| 10 min + | 2 min | `melaya_run_inspect` with `include_tool_calls`; decide on cancel |

A few seconds between polls is the floor; hammering status buys nothing.
While waiting, check `melaya_approval_list { "run_id": "<id>" }`: a run
that is "slow" is often paused on an approval.

Cancel (`melaya_run_cancel { "run_id": "<id>", "pipeline": "<name>" }`) when:
- the same tool is called with the same arguments 3+ times;
- the tool-call count exceeds the budget written in the instruction;
- an agent is retyping large data (long `row_json`, huge `items` arrays);
- a hard auth failure repeats (a missing connector will not heal mid-run);
- elapsed time is 2x the expected duration with no new artifact.

### 3. Read the verdict

When `terminal` is true, read `outcome`. Then, success or not:

`melaya_run_diagnosis { "run_id": "<id>", "message_limit": 80 }`

It returns per-phase eval verdicts with reasons, tool forensics, agent
messages (previews) and token/dollar cost. Tool forensics statuses:

| Forensic status | Meaning | First suspicion |
|---|---|---|
| `auth` | Hard auth failure (401/403, not connected) | Connector missing or expired: user reconnects |
| `error` | Tool returned an error | Wrong args, dead URL, upstream down |
| `empty` | Tool returned no data | Bad query, wrong tab/range, blocked source |
| `oversized` | Result too large for context | Read to file (`save_to`), narrow range |
| `bad_args` | Invalid/missing arguments | Instruction does not state argument shape |
| `contract` | Output did not match the expected contract | Prompt output contract unclear |
| `stall` | Agent stopped making progress | Loop, missing budget, unclear stop rule |
| `coverage` | Expected tools never called | Instruction does not force the phase |

Eval failure kinds you will see in verdicts include `empty`, `empty_result`,
`tool_error`, `false_fallback` (agent claims a source failed while it
succeeded), `ungrounded`, `citation_missing`, `missing_sections`,
`schema_invalid`, `timeout`.

A run can have `outcome` success AND bad forensics (for example every
research call `empty`, agent wrote "no data found"). That is a failed
validation.

Two more "success that is not success" shapes to check on every run:
- **Interrupted run.** A platform restart or a runner disconnect can close a
  run as finished, sometimes with `outcome` success, while later steps never
  ran. Check that every step in the config has at least one message in
  `melaya_run_inspect`, and that the last step's reply is present. A missing
  step means: re-run. It is not a config defect.
- **Empty final reply.** An agent that spent its turn budget on tool calls can
  end with an empty or "iteration limit" reply. Budget the agent (see the
  failure catalogue, "Speed and loops").

### 4. Read what actually happened

`melaya_run_inspect { "run_id": "<id>", "include_tool_calls": true, "limit": 60 }`

- Messages come newest first and are truncated per message. Page older
  messages by passing the returned `nextCursor` back as `cursor` until
  `nextCursor` is absent.
- The tool trace (`include_tool_calls`) is NOT paginated and can be long:
  request it once per run and work from it.
- For each step, check: which tools were called, with what arguments, what
  came back, and whether the next step received the data it needed.
- Count calls per agent. Compare with the budget in the instruction.
- Look for the argument shape: lists/dicts sent as JSON strings (the platform
  coerces them, but an unclear shape is still a prompt defect), full rows
  copied into `row_json`, items copied into `decide_batch` instead of a file.

### 5. Approvals and memory

- `melaya_approval_list { "run_id": "<id>", "include_history": true }` -
  what is pending, and what was decided by whom. Report pending items to the
  user with what each wants to do. Do not decide them.
- `melaya_agent_memory { "pipeline": "<name>", "project": "<project>" }` -
  what the crew will replay into the next run. Three outcomes: entries
  returned; memory lives on the runner (`local: true`, not listable); empty.
  Stale tool-failure notes ("scrape_page is broken", "sheet not found")
  written before a fix will poison later runs: see failure catalogue.

### 6. Verify artifacts

Follow `references/artifact-verification.md`. Minimum per pipeline:
- data spine Sheet: row count delta, header alignment, provenance columns
  filled, status lifecycle advanced, no duplicates;
- every human-facing document: exists, correct title, real content, no
  placeholders, no raw error text;
- mailer: exactly one message sent, subject differs from body, attachments
  present (check with `gmail_search` on the owner's own mailbox).

### 7. Classify and fix

| Evidence | Class | Fix path |
|---|---|---|
| Tool raises/returns wrong result for valid args; same failure with any prompt; parser drops a documented field; platform ignores a setting | Platform defect | `references/fix-handoff.md`: report to Melaya with evidence; hold dependent configs until the fix is live |
| Agent calls wrong tool, wrong args, skips a phase, loops, retypes data, invents values, wrong output contract | Config/prompt bug | Edit generator constants/instruction, regenerate, `melaya_pipeline_preview`, `melaya_pipeline_save` mode `update` (full document), re-run |
| `auth` forensics, `no_key`, missing connector | Setup | User reconnects (`melaya_connector_connect` link); re-run after |
| Upstream site blocks/404s, feed empty today | Source reliability | Change source order in the instruction (registry/data tool, known URL, RSS/GDELT, web_search only to discover) |
| web_search header says a fallback engine, not `Engine: native` | The agent's key cannot use its provider's own search (Qwen Token Plan key, Groq key without compound access) or that provider was briefly failing | Nothing to fix for correctness: fallback results are still real. For native search, use a Pay-As-You-Go key or another provider for that agent |
| Run waits forever | HITL pending | User decides in app/phone; or config: gate only what must be gated |

Test to separate platform from config: reproduce the failing call in
isolation. If the tool is read-only, call it yourself with
`melaya_connector_call` using the exact arguments from the trace. Same bad
result with correct args = platform defect. Correct result = the agent's args
or sequencing were wrong = config defect.

Release discipline: a config that depends on a new tool parameter or a
platform fix is saved only AFTER Melaya confirms the change is live (and, for
runner-hosted pipelines, after the user has updated their runner). Otherwise
the builder or the tool ignores the new argument and you validate against old
behaviour.

## Validating a multi-pipeline system (dependency order)

Pipelines share a data spine (a Sheet) and feed each other through it. Validate
upstream first; a downstream failure caused by missing upstream rows wastes a
run and misleads diagnosis.

Example order for an early-stage investment team system:

| # | Pipeline | Depends on | Pass criteria |
|---|---|---|---|
| 1 | Sourcing | nothing (keyless public data) | N new rows, provenance filled, status `sourced`, no duplicates |
| 2 | Intake (brief + deck file) | spine exists | 1 row created or updated from the file, dedupe verdict respected |
| 3 | Screening | rows in `sourced` | scores + status written back to the exact `[row N]`, fixed columns |
| 4 | Due diligence | a screened row | designed DD document published, sources cited, MISSING not "clear" |
| 5 | Memo | DD output | memo document, numbers traceable to SOURCE fields |
| 6 | Forms / exports | memo + row | filled form file, correct fields |
| 7 | Benchmark | several screened rows | benchmark table, cohort stated |
| 8 | Copilot (Q&A on the spine) | populated spine | answers cite rows, no invention |
| 9 | Portfolio monitoring | rows in portfolio status | update rows + digest email |

Rules for the campaign:
- One pipeline at a time until it passes; then move down the chain.
- Keep a ledger per pipeline: run id, model, outcome, calls, minutes, cost,
  artifact check result, defect class, fix applied. The handover docs reuse it.
- After a fix to an upstream pipeline, re-run downstream pipelines that read
  what it writes (schema/column changes ripple).
- Finish with one end-to-end pass in order, on the final saved configs,
  with no manual edits between runs.
- Use `melaya_eval_report { "project": "<project>", "range": "week" }` at the
  end for acceptance rate, failure-kind histogram and cost per accepted run.
  Runs without `loop_policy` show as unevaluated.

## Definition of done (per pipeline)

- [ ] Latest run `outcome` success, on the final saved config
- [ ] Diagnosis: no `auth`/`error`/`bad_args` forensics left unexplained;
      `empty` only where a source genuinely had nothing
- [ ] Tool calls within the stated budget; duration acceptable
- [ ] Artifacts verified read-only (rows, docs, mail) and correct
- [ ] No pending approvals left unexplained to the user
- [ ] Crew memory holds nothing stale (or `persistent_memory` is off)
- [ ] Every defect found is fixed at its source (generator or code) and the
      fix is recorded in the ledger

## Talking to a non-technical user

When the person you work for is not technical, translate every result:

1. Say the verdict first, in one line: "The run finished and the report is
   in your Drive" or "The run stopped because Gmail needs to be reconnected".
2. Say what they must do, if anything, as one click-level action ("open this
   link and press Allow", "open the Melaya app and decide on the waiting
   email to Acme").
3. Offer the detail only if asked. `references/reading-results.md` has the
   plain-words guide to `melaya_run_diagnosis` and `melaya_eval_report`.

Never paste raw JSON, forensics codes or stack traces to such a user.

## Anti-patterns

- Declaring success from `status: "done"`.
- Declaring success from the agent's final message.
- Re-running the same failing config hoping for a different result.
- Hand-editing JSON in the builder as the fix (the next regeneration erases it).
- Saving a config that uses a tool parameter that is not live yet.
- Using `melaya_pipeline_save` mode `update` with a partial config (omitted
  fields are erased).
- Waiting 30 minutes on a runaway instead of cancelling at minute 10.
- Approving, or asking an automation to approve, a gated send.
- Validating with real external recipients; validation emails go to the owner.
