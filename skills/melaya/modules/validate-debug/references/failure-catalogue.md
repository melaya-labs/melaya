# Failure catalogue: symptom -> root cause -> fix

Each row is a failure observed on real validation runs. "Where to see it" names
the MCP read that exposes it. "Class" is Config (regenerate + re-save),
Setup (user action), Source (change source order), Transient (re-run), or
Platform (report to Melaya, see `fix-handoff.md`).

Each section ends in plain words you can say to a non-technical user.

## Run state and reading results

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| Run reported "done" but nothing was produced | `melaya_run_status` | Every finished run is normalised to `status: "done"` | Read `outcome`; then `melaya_run_diagnosis` | Reading error |
| `outcome` success, but later steps have no messages and their artifacts are missing | `melaya_run_inspect`: messages exist only for the first steps; the last step's reply is absent | The run was interrupted (a platform restart, or the runner went offline) and closed out as finished | Re-run the pipeline unchanged. If it repeats at the same step, look at that step's tool calls; if it repeats with no pattern, report it (`fix-handoff.md`) | Transient |
| Run stopped mid-way on a runner-hosted pipeline | `melaya_runner_status` shows disconnected | The user's computer slept, lost network, or the runner was closed | Ask the user to keep the runner running and the machine awake for the run; re-run. For schedules use `requires_runner: true` so fires are skipped, not failed, while it is offline | Setup |
| Run "hangs" with no new messages | `melaya_approval_list` with `run_id` | A gated tool is waiting for a human decision | Tell the user what is waiting; they decide in the app/phone. Never approve for them | HITL |
| Triggered run pauses on a send the manual run did not | `melaya_approval_list` | Triggered runs are FORCED safe HITL | Expected. Document it; the user approves or the send is removed from the triggered path | By design |
| Gated send went out with no approval | `melaya_approval_list` `include_history` shows none | `hitl_mode` was `autonomous` or `payments_only`, which DROP the per-agent `human_approval_tools` list | Any gated send requires `hitl_mode: "safe"` | Config |
| No eval scores; eval report counts runs as unevaluated | `melaya_eval_report`, `melaya_run_diagnosis` | `loop_policy` missing or written as a string | `loop_policy: {"mode":"observe_only","evaluator":"default"}` object on every agent | Config |
| A field you authored has no effect | `melaya_pipeline_get` read-back, `melaya_pipeline_preview` code | Parser silently drops unknown/mis-shaped fields | Preview before every save; confirm the field appears in generated code | Config |
| Fields vanished after an update | `melaya_pipeline_get` | `mode: "update"` is a FULL replace; a partial config erased the rest | Always send the whole document (regenerate from the generator) | Config |
| Agent edits have no effect | `melaya_pipeline_preview` | The generated code reads the agents EMBEDDED in `steps[]` (`step.agent`, `step.agents[]`), not a top-level `agents[]` | Edit the embedded copies; keep top-level `agents[]` in sync only if present | Config |

## Data handling (the biggest class of defects)

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| Sheet rows with shifted or missing cells | Artifact check (header alignment) | Model retyped a wide row (dozens of columns) into `row_json` | Never `row_json` for wide rows. `dealdb_normalize` with `save_to` CSV, then `sheets_append_row` with `csv_path` | Config |
| Scoring step judged 9 of 15 items, or items garbled | `melaya_run_inspect` tool trace (`decide_batch` args) | Model copied long items into `items` | `decide_batch_file` (or `jev_batch_file`) with `items_path` = the `save_to` file, `item_template: "{col} \| {col}"`, `include_if: "status=a,b"` | Config |
| Agent believes the sheet has ~1000 rows | Tool trace: `sheets_read_range` reply | `range` echoes Google's empty grid (for example `A1:BY1015`) for 15 real rows | Read `data_rows` from the `save_to` summary, not `range` | Config |
| Agent works from half a table | Tool trace: truncated `sheets_read_range` reply | Inline output is truncated for wide tables | `sheets_read_range` with `save_to: "<name>.json"`; pass the path to `dealdb_*` `rows` / `decide_batch_file` `items_path` | Config |
| Empty read on a sheet that has data | Tool trace: "range is EMPTY" note | No tab named, so only the FIRST tab was read | `sheets_list_tabs`, then quote the tab: `"'Deals'!A1:BZ"` | Config |
| Write-back landed on the wrong row or column | Artifact check | Row number guessed or header offset ignored; columns computed ad hoc | Use `[row N]` tags from `decide_batch_file` (header = row 1); write fixed column letters computed from the schema (keep status, score, updated_at contiguous) | Config |
| Duplicate records appended each run | Artifact check (duplicates) | Recorder skipped dedupe | Per record `dealdb_dedupe` against the file; skip when score >= 0.92 | Config |
| Recorder wrote into an OLD, unrelated sheet | Artifact check (wrong file) | `drive_search` name matching is a word match, not an exact-title match: "Acme Deal DB" also matches "Acme Old Deal DB", and the agent took the first hit | Give the data store a name that shares no full word set with existing files; instruct "if more than one file matches, use the one whose name is EXACTLY <name>; if none is exact, create it"; find-or-create (`drive_search` -> `dealdb_schema` -> `sheets_create`) | Config |
| Agent read the wrong reference Doc or template | Tool trace: `drive_search` returned several files, agent used another | Same word-match search, on a generic name ("Template", "Memo") | Give reference files distinctive names, or put the file link in the instruction or the run brief instead of a name search | Config |
| Agent works from the first part of a long Google Doc only | Tool trace: `docs_read` reply `length` far above the text returned | `docs_read` returns up to about 30,000 characters of text (plus the full `length`); anything past that is not seen | For longer docs: `drive_export` with `mime: "txt"` then `file_read` (or `mime: "pdf"` then `pdf_to_text`) on the returned `path`; state the page or section to use in the instruction | Config |
| Next step does not see the first step's facts | `melaya_run_inspect` messages of step 2/3 | Parallel step join (`joinStrategy: "concat"`) contains ONLY the parallel agents' replies | Instruct one parallel analyst to start its reply with the previous message's TARGET/FACTS/SHEET/ROW lines copied unchanged | Config |
| Tool call rejected, dict/list arrived as a string | Forensics `bad_args` | Some models send list/dict args as JSON strings | The platform converts these now; still state argument shapes explicitly in the instruction | Config (Platform if it regresses) |

## Research and sources

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| `web_search` returns nothing or blocks | Forensics `empty`/`error` on `web_search` | Keyless HTML engines block scripted clients, even from residential IPs | Reliability order in every research prompt: (1) registry/data tools, (2) a KNOWN URL with `scrape_page`, (3) RSS and GDELT, (4) `web_search` only to discover URLs, then open the page. Note: `web_search` tries the calling agent's own provider search first, then Bing News RSS, then HTML engines | Source |
| News discovery empty | Forensics `empty` | Generic query on a generic feed | Targeted Bing News RSS URL with `rss_search_entries`: `https://www.bing.com/news/search?format=rss&q=<query>` | Source |
| Page read returns an error or an empty shell on a JS-heavy site | Forensics `error`/`empty` on `scrape_page` for one domain | The site builds its page in the browser; a plain fetch sees nothing | Use a registry/data tool or the site's RSS feed for that source; mark the field MISSING rather than retrying the same URL | Source |
| Agent loops on `/about`, `/legal`, `/team` URLs | Tool trace: repeated `scrape_page` "HTTP 404 page not found" | Guessed paths; many sites serve the home shell on unknown paths | `scrape_links` on the home page, then open the real link | Config |
| Report says a check is "clear" when the source failed | Artifact check, forensics `error`/`empty` | Failure mapped to a negative finding | Provenance rule: failed source = `MISSING`, never "clear". Every field gets `<field>__src` + `<field>__status` (SOURCE / INFERRED / MISSING) | Config |
| Numbers with no source | Eval `ungrounded` / `citation_missing` | No provenance contract in the instruction | "Never invent a number"; require `__src` per field and a CITE section | Config |
| Agent says a tool failed but trace shows data | Eval `false_fallback` | Model misread a large or unusual reply | Tighten output contract; read big replies from file; if the tool reply itself is misleading, report it | Config or Platform |

## Speed and loops

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| One agent makes 100+ sequential calls, 25+ minutes | Tool trace count, duration | "Use every source" prompt with no budget | Bounded phases + hard budget + stop at TARGET + parallel batches. See `speed-tuning.md` | Config |
| Forensics `stall` | `melaya_run_diagnosis` | No stop rule, or waiting on something that never comes | Explicit stop condition; cancel the run; check approvals | Config |
| Forensics `coverage` (expected tool never called) | `melaya_run_diagnosis` | Instruction does not force the phase | Numbered phases with the exact tool per phase | Config |
| Run minutes per turn on a local model | Duration, model in config | Validating on a CPU/local model | Validate on a cheap hosted model; see SKILL.md "Validation model" | Config |
| Final reply empty, or says it hit the iteration limit, after a long tool loop | `melaya_run_inspect`: last message of that agent is empty or a short partial summary; many tool calls before it | Each agent has a ceiling on reasoning turns (set by the platform and the model; you cannot raise it from the config). An agent that spends it on tool calls has no turn left to write its answer | Budget the agent: "at most N tool calls, then STOP and write the output"; parallel batches; move bulk work to one file-based call (`decide_batch_file`, `save_to`); split a huge agent into two steps (collect, then write). Small local models get a much lower ceiling | Config |
| One agent in a parallel step failed but the run continued | `melaya_run_diagnosis` forensics on that agent; its reply is missing from the joined output | Parallel siblings are isolated: one failure degrades the step instead of killing the run | Check the writer did not present the missing part as done; instruct it to mark that section MISSING. Fix the failing agent's cause | Config |
| Model error mid-run, agent reply is a short partial summary | `melaya_run_diagnosis` messages mention a model error | The model provider failed repeatedly; the agent stops and summarises what it had | See "Providers and quotas" below; re-run once the provider is healthy | Setup / Transient |

Plain words: "The run was cut short before the last steps ran; I am running
it again." / "The agent ran out of steps before writing its answer; I am
giving it a tighter plan so it finishes."

## Providers, rate limits and quotas

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| Errors mentioning 429, "rate limit", "too many requests" on model calls | `melaya_run_diagnosis` messages; runs slow then fail | A free-tier or per-minute cap on the model provider. It resets on its own | Wait and re-run; run fewer pipelines at once; use a paid key or another provider for validation; parallel batches of 20 calls hit per-minute caps faster, so lower the batch size | Setup |
| "Out of credits", "insufficient quota", "billing" on model calls | Same | The balance with the model provider is empty (not the same as a rate limit: it does not reset) | The user tops up with that provider, or switches model (`melaya_model_list`) | Setup |
| "Key rejected", 401 on model calls | `melaya_model_list` returns `invalid_key` | Key wrong, expired or revoked | The user replaces the key under Connectors (`melaya_connector_connect` shows where) | Setup |
| "Model retired" / model not found at run start | Diagnosis messages | Provider withdrew the model, or the id was guessed | Pick a live id from `melaya_model_list`; re-save | Config |
| Run blocked or refused before it starts: monthly or concurrent run limit | `melaya_pipeline_run` error; `melaya_account_usage` | Plan limits on runs | Wait for running runs to finish, space validation runs out, or the user upgrades the plan | Setup |
| 429 or quota errors from a data tool (Google, a public API) | Forensics `error` on that tool, reply mentions quota or 429 | The upstream API throttles per minute or per day | Fewer calls per run (read ranges to a file once, not row by row); spread schedules; keyless sources may throttle scripted clients, so keep the reliability order | Source |

Plain words: "The AI provider is temporarily limiting how fast we can use it;
I will try again in a few minutes." / "Your account with <provider> has run
out of credit; please top it up on their site or choose another model."

## Memory

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| After a tool was fixed, agents still refuse to use it ("tool X is broken") | `melaya_agent_memory` | `persistent_memory` replays the last step's tail + auto-extracted tool-failure notes into every run; stale notes poison later runs | Turn off `persistent_memory` (use the Sheet as memory) or clear the stale entries in the app; re-run | Config |
| Memory holds nothing useful | `melaya_agent_memory` | Only the LAST step's reply tail is persisted | Enable crew memory only when the last step's reply is worth remembering; otherwise keep state in the data spine | Config |

## Documents and mail

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| Plain, unbranded or text-only report | Artifact check | Output written as a message or `docs_create` text | `word_create` blocks (cover, heading, paragraph, bullets, callout, table) with theme `"melaya"` or a client theme dict, then `drive_upload` with a google-apps mime to publish as a Google Doc | Config |
| Two or more emails per run | `gmail_search` on the owner mailbox | Mailer retried or split | `gmail_my_address` then ONE `gmail_send`; instruction states "a second call is a failure" | Config |
| Email body equals subject, or no attachment | `gmail_search` row (subject, attachment names) | Mailer contract not explicit | Subject != body; attach FILE paths returned by the document tools | Config |
| Email contains a raw stack trace or HTTP body | `gmail_search` / mailbox | Mailer forwards errors | Name the failing service + the reconnect link; never forward raw errors; plain-ASCII writing rules | Config |
| Uploaded deck ignored | Tool trace: no `read_run_input` / `deck_extract` call | Files reach CLOUD runs only; or the instruction never reads the Run inputs block | Run on cloud; instruct to read the file path shown in the Run inputs block with `deck_extract` / `read_run_input` | Config |

## Setup

| Symptom | Where to see it | Root cause | Fix | Class |
|---|---|---|---|---|
| Forensics `auth` on a Google/Gmail tool | `melaya_run_diagnosis` | Connector not connected, expired, or missing a scope | `melaya_connector_connect { "service": "<id>" }` gives the user the consent link; wait; re-run. Never work around a missing grant | Setup |
| First tools of the run worked, later calls to the same service fail with `auth` ("authorization failed after refresh", 401, "invalid grant") | Forensics `auth` part-way through; earlier calls to the same service succeeded | The connector's access expired or was revoked during the run and could not be renewed (password change, access removed in the provider's security settings, the consent was for a different account, or an admin policy). Melaya renews Google access automatically once; a failure after that renewal needs the user | Reconnect the service (`melaya_connector_connect`), `melaya_connector_test` it, then re-run the WHOLE pipeline, not just the failed step: rows or files written before the failure may be partial, so check them for duplicates on the re-run | Setup |
| Model error at run start | `melaya_run_diagnosis` messages | Guessed model id, or provider key missing | `melaya_model_list` for the provider; fix the id; `no_key` = user connects the key | Config / Setup |
| Inputs rejected before the run starts | `melaya_pipeline_run` error | Unknown key, missing required input, wrong type | Match `melaya_pipeline_get` declared `inputs` | Config |
| New tool argument ignored | Tool trace args vs behaviour | Config saved before the platform change that adds the argument was live (or the runner was not updated) | Wait until Melaya confirms the change is live, restart the runner if used, then save the dependent config and re-run | Release order |

Plain words: "Melaya lost access to your <service> during the run. Please open
this link, sign in with the same account and press Allow; then I will run it
again from the start."
