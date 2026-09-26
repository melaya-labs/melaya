<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when a Melaya multi-pipeline system needs a shared system of record (a Google Sheet "database" that several pipelines read and write), or when an agent must score, triage or rank many rows or items. Covers schema design with __src/__status provenance, collision-proof sheet naming, find-or-create, file-based reads (sheets_read_range save_to), dedupe, normalize-then-CSV-append (sheets_append_row csv_path), write-back by column name (sheets_update_by_header) and [row N], deleting rows without gaps, composite scores computed by dealdb_rank, a data health check pattern, a status lifecycle across pipelines, and bulk scoring with decide_batch / decide_batch_file (free) or the paid jev_* twin. Load it while designing the data model and while writing any Recorder, Screener or write-back instruction.

# Melaya data spine

A multi-pipeline system needs ONE place where every pipeline reads the current state and writes its result. On Melaya that is a Google Sheet with a fixed schema: human-readable, shareable with the client, versioned by Google, and reachable with the `sheets_*` and `drive_*` tools once the Google Workspace connector is granted.

This module is the method for that spine. It is built around one rule:

> **Never make the model retype big data.** Tools move rows through FILES. The model only moves file names, row numbers and a few short values.

Cheap fast models drop or shift cells when they copy a 76-column row or rebuild 15 long items. Every pattern below exists to take that copying away from the model.

## When to use this module

- You are designing the data model of an agentic system (entity table, columns, statuses).
- A pipeline must record new entities (a "Recorder" agent).
- A pipeline must score, triage, rank or route many rows (a "Screener").
- A pipeline must update one row (status, score, a link to a document).
- A run wrote shifted, truncated or duplicate rows and you need the structural fix.

Related skills: `../../modules/pipeline-authoring/GUIDE.md` (config schema, instruction patterns), `../../modules/validate-debug/GUIDE.md` (checking the Sheet after a run), `../../modules/discovery/GUIDE.md` (granting the Google Workspace connector).

## Tool map (verified in the tool registry)

| Tool | Read/write | Use it for |
|---|---|---|
| `drive_search` | read | Find the spine by `name` + `mime = "sheet"`, newest first |
| `sheets_create` | write | Create the spine with `title`, `sheet_title`, `initial_values_json` (header row) |
| `sheets_list_tabs` | read | Find the right tab when a read comes back empty |
| `sheets_read_range` | read | Read the table; ALWAYS with `save_to = "<name>.json"` for tables |
| `sheets_append_row` | write | Append rows; ALWAYS with `csv_path` for wide rows, never `row_json` |
| `sheets_update_by_header` | write | Write back cells by COLUMN NAME, many rows in one call; unknown column names are refused and nothing is written |
| `sheets_update_range` | write | Write an exact A1 range (the header row at creation; avoid for write-back) |
| `sheets_delete_rows` | write | Delete whole rows (rows below move up, no blank gap); the header row is refused |
| `drive_create_folder` | write | Find or create the client folder (a name path such as "Client Pilot/Data"); pass it as `folder` to `sheets_create` |
| `dealdb_schema` | read | Canonical deal schema + header row (`values_json`, `a1_range`) |
| `dealdb_normalize` | read | Normalize money/FX, stage, country, sector, status; `save_to` a CSV |
| `dealdb_dedupe` | read | Fuzzy match a new entity against the table file |
| `dealdb_rank` | read | Rank rows by a score column, ties and unscored handled; with `components` it computes the weighted composite itself |
| `dealdb_benchmark` | read | Peer percentiles and outlier flags for one row |
| `decide_batch` | read | FREE self-hosted scoring of an inline list of items |
| `decide_batch_file` | read | Same, items read from a file (sheet save_to JSON, CSV, JSON list) |
| `decide` / `decide_check` | read | One item, several questions / one gate question |
| `jev_batch` / `jev_batch_file` | read | Paid hosted twin (Jev connector, per-token billing), same question schema |

The `dealdb_*` tools are pure local computation (no network). All `dealdb_*` `rows` arguments accept a `.csv`/`.json` file path in the run folder, so pass the `save_to` file name, not the data.

## The eight rules

1. **One spine, one schema, one tab.** Header in row 1, one entity per row, fixed column order. The schema comes from a tool (`dealdb_schema`) or from one constant in your config generator, never from the model's memory.
2. **Provenance on every data field.** `<field>`, `<field>__src`, `<field>__status` side by side. Status is `SOURCE`, `INFERRED` or `MISSING`. A failed source is `MISSING`, never a guessed value.
3. **Collision-proof name.** Drive name search is a WORD match: a search for "Acme - Deal DB" also returns "Acme - Old Deal DB". Pick a name that shares no complete word set with any older file (for example "Acme Deal Pipeline v2" after an "Acme Deal DB"), and use exactly that name in every pipeline.
4. **Find or create.** `drive_search` first; create with `sheets_create` only when nothing is found. Take the newest result.
5. **Read to a file.** `sheets_read_range` with `save_to`. Use `data_rows` from the summary as the real row count. The echoed `range` is Google's grid (for example `Deals!A1:BZ1015` for 15 real rows): ignore it.
6. **Write new rows from a file.** Normalize ALL records in one `dealdb_normalize` call with `save_to = "<name>.csv"`, then ONE `sheets_append_row` with `csv_path`. Never `row_json` for a wide row.
7. **Update by column NAME and exact row numbers.** `sheets_update_by_header` with `updates_json` = a list of `{"row": N, "fields": {"status": ..., "score": ..., "updated_at": ...}}` writes many rows in one call and refuses unknown column names. Never write back by column letter: a wrong letter once overwrote the status and score columns with text. Row numbers come from the `[row N]` tags of `decide_batch_file` or from `values[i]` = sheet row `i + 1`. To remove a row, `sheets_delete_rows`; never clear it (a blank row breaks later appends).
8. **Status is the contract between pipelines.** Each pipeline reads the statuses it owns as input and writes exactly one next status. See [references/status-lifecycle.md](references/status-lifecycle.md).

## Design the schema

Start from `dealdb_schema` if the domain is deals or companies. For any other domain (leads, suppliers, tenders, candidates, incidents), copy the SAME layout. Full method and worked examples: [references/schema-design.md](references/schema-design.md).

`dealdb_schema(tab = "Deals", layout = "interleaved")` returns:

- `columns` (name, type, desc) and `optional_columns`
- `header` and `header_count` (76 in the interleaved layout: 28 fields, 24 of them with `__src` + `__status`)
- `a1_range` (`Deals!A1:BX1`) and `values_json` (the header as a JSON 2-D array)
- `conventions` (what `__src` / `__status` mean) and `enums` (stage, status, round_type, sector taxonomy)

Fields WITHOUT provenance companions: `company` (row key), `status`, `score`, `updated_at`. They sit at the end in that order, so in the interleaved layout the fixed letters are:

| Column | Letter | Written by |
|---|---|---|
| company | A | Recorder (append only) |
| website | B (`__src` C, `__status` D) | Recorder |
| strategic_fit | BJ | Recorder / Screener |
| risks | BM | Recorder / DD |
| dd_findings | BP | Due-diligence pipeline (a document link) |
| ic_feedback | BS | Committee pipeline |
| status | BV | every stage owner |
| score | BW | Screener |
| updated_at | BX | every writer |

Letters are for reading and checking only (for example `a1_range` of a read); writes go by column name. If you still need letters, recompute them whenever you change the schema and keep them in ONE constant of your generator script.

Design checklist for a new domain:

- [ ] One row key column first (name), one dedupe key (domain, email, registry id).
- [ ] Every data field that a human might challenge gets `__src` + `__status`.
- [ ] Enumerations are closed lists written in the schema (status, stage, category).
- [ ] Machine columns (`status`, `score`, `updated_at`) are last and contiguous.
- [ ] Money is plain numbers in one currency, no symbols; dates are ISO `YYYY-MM-DD`.
- [ ] One score scale per sheet (0-10 recommended).
- [ ] The header row fits `initial_values_json` of `sheets_create` (a JSON 2-D array).

## Find or create (paste into every agent that touches the spine)

```text
Find the database: invoke drive_search with name = "<SHEET NAME>" and mime = "sheet". Use the id of the newest result as SHEET_ID.
If no sheet exists, create it: invoke dealdb_schema with tab = "<TAB>", then invoke sheets_create with title = "<SHEET NAME>", sheet_title = "<TAB>", initial_values_json = the values_json returned by dealdb_schema and folder = "<CLIENT FOLDER>". Use the new id as SHEET_ID.
```

Only the Recorder (the first writer) gets the create branch. Readers that find nothing should report "no database yet" rather than create an empty one.

`drive_search` only sees files Melaya created or the user picked through Melaya. A sheet the client created by hand is invisible until they pick it; either let the Recorder create the spine, or ask the user to open it once through Melaya.

## Read to a file

```text
Invoke sheets_read_range with spreadsheet_id = SHEET_ID, a1_range = "<TAB>!A1:BZ2000" and save_to = "rows.json". It saves the whole table to that file and replies with a short summary (data_rows = the real number of rows; the range may show many empty grid rows, ignore them). Never copy the table yourself.
```

The summary is JSON: `saved_to`, `range`, `data_rows`, `columns`, `header_first` (first 8 header cells), `first_values` (first cell of up to 5 rows). The file holds `{"range": ..., "values": [header, row, ...]}` in full, never truncated. Without `save_to`, the reply is truncated for wide tables and the model will guess the rest.

Rules:
- Always read from `A1` so row numbering stays exact (header = row 1).
- Always name the tab (`"<TAB>!A1:BZ2000"`); a range without a tab reads only the first tab.
- If the reply says the range is EMPTY, call `sheets_list_tabs` and read the right tab.
- Pass `"rows.json"` to `dealdb_dedupe`, `dealdb_rank`, `dealdb_benchmark`, `dealdb_normalize` (as `rows`) and to `decide_batch_file` / `jev_batch_file` (as `items_path`).

## Record new entities (Recorder)

Full pattern with the upstream output contract: [references/recorder-pattern.md](references/recorder-pattern.md). Core steps:

1. Find or create the spine.
2. Read to `rows.json`.
3. `dealdb_dedupe(rows = "rows.json", new_company = ..., website = ...)` for each record, in ONE parallel batch. The tool returns `verdict` (`duplicate` at score >= 0.92, `possible_duplicate` at >= 0.75, else `new`) and `best_score`. Pick one skip threshold for the system and write it into the instruction (0.92, the tool's own duplicate verdict, is the safe default; lower it to 0.85 when sources name companies inconsistently, which also skips same-domain matches). Skip dedupe when `data_rows` is 0: on a header-only file the tool returns an error.
4. `dealdb_normalize` ONCE on all non-duplicates with `save_to = "new_rows.csv"`.
5. `sheets_append_row(spreadsheet_id, sheet = "<TAB>", csv_path = "new_rows.csv")`.

Provenance is enforced in code at step 4 (the prompt rules still apply: FACT_RULES in `../../modules/pipeline-authoring/references/instruction-patterns.md`):
- Only schema columns are written: extra keys are dropped (and listed) instead of spilling past the header.
- A filled value is `SOURCE` only when it has a `__src`; a filled value without one becomes `INFERRED`; an empty field is `MISSING`.
- A country whose `__src` says it was guessed from an email address, a domain, a person's name or an investor is blanked to `MISSING`.
- The upstream agent still sets `INFERRED` itself for estimates, and cites `__src` (a URL, `<file> p.N`, `computed: ...`) for everything it read.

## Write back rows

By column name, exact row, ONE call for all rows:

```text
Write the results back using the [row N] of each item. Invoke sheets_update_by_header ONCE with spreadsheet_id = SHEET_ID, sheet = "<TAB>" and updates_json = a JSON list with one entry per scored item: {"row": N, "fields": {"status": "screened", "score": <score from dealdb_rank>, "updated_at": "<today ISO date>"}}. Use only column names from the header row.
```

```text
If a ROW line gives a number and a SHEET line gives the sheet: invoke sheets_update_by_header with spreadsheet_id = the id in the SHEET URL, sheet = "<TAB>" and updates_json = [{"row": <ROW>, "fields": {"dd_findings": "<DOC link>", "status": "diligence", "updated_at": "<today ISO date>"}}].
```

Remove validation or duplicate rows with `sheets_delete_rows` (`rows = "4,9,12"`) after reading them back to confirm they are the right ones; never blank them.

Row numbers:
- From `decide_batch_file` / `jev_batch_file`: items come back as `[row N] ...`; N is the sheet row (blank rows are skipped but numbering is kept).
- From a JSON file you read yourself: `values[0]` is the header = row 1, so `values[i]` is row `i + 1`.
- Passing the row between steps: have each step end with `SHEET: <url>` and `ROW: <n>` lines, and tell the next agent to copy them unchanged.

Never rewrite a full 76-column row to change one cell. (If you must use `sheets_update_range`, `values_json` must match the range shape exactly.)

## Score and triage many items

Full reference with question design, cascade maths and the jev twin: [references/scoring-from-files.md](references/scoring-from-files.md).

`decide_batch_file` reads the saved sheet and turns each row into one text item:

```text
Invoke decide_batch_file with items_path = "rows.json", include_if = "status=sourced,screened", item_template = "{company} | {website} | {country} | {sector}/{sub_sector} | {stage} | model: {business_model} | round: {round_type} {round_size_usd} | traction: {traction_notes} | fit: {strategic_fit} | risks: {risks}", questions = <QUESTIONS JSON>, top_by = "strategic_fit" and top_k = 500. Every row comes back as "[row N] <company> | ..." with all scores; N is its sheet row.
```

Key facts (observed behaviour):
- `item_template` uses `{column}` placeholders; unknown columns render empty. Without a template, the item is every non-empty column except `__src` / `__status`.
- `include_if` is ONE `column=value1,value2` condition, case-insensitive on values, exact on the column name.
- Question types: `noul` (probability 0..1), `score` (2 to 10 ordered level labels; the answer is the expected level index 0..k-1, a float), `choice` (map of option -> description; answer = winning option + probabilities).
- `top_by` ranks by that question; `top_option` ranks a choice question by one option's probability.
- `cascade_by` + `cascade_keep` (default 0.25): score every item on the gate question, then only the top fraction on the rest.
- `save_to` writes every row (ranked when `top_by` is set). Cap: 5000 items per call.
- Free and self-hosted; `jev_batch_file` is the paid hosted twin with the same `items_path`, `item_template`, `include_if`, `questions`, `top_by`, `top_k` (no cascade; adds `concurrency`, `model`).

Composite score to write back: computed by a TOOL, never by the model. Pass `dealdb_rank` rows that carry the raw criterion answers (`{"company", "row", "market": 3, "team": 2, ...}`) with `components = "market:1,team:1,product:1,strategic_fit:2"` and `component_max = 4` (the top of the criterion scale): it returns the weighted composite on a true 0-10 scale, with ranks, ties and unscored rows. A model left to do it once skipped the x2.5 scaling and printed 0-4 scores as /10.

For raw candidates that are not in the sheet yet (headlines, search results), use `decide_batch` with `items` = a JSON array of short strings (`"<title> | <date> | <link>"`) in ONE call, before any expensive confirmation step.

## Data health check (a reusable pipeline)

A spine drifts: guessed countries, dead websites, a bad batch. Keep one pipeline that re-verifies it:
1. Read the whole table to a file (`save_to`).
2. Re-check every row against sources (registry, the website loaded with `scrape_page`, the deck, a cited article).
3. Repair a value only with a cited source (`__src`); blank what cannot be proven (`MISSING`); never fill from memory.
4. ONE `sheets_update_by_header` call with every changed row; `sheets_delete_rows` for confirmed duplicates.
5. A report document: rows checked, values repaired with their sources, values blanked, rows for a human to review.

Run it after a schema change, after a bad batch and before a handover. Instruction shape: `../../modules/pipeline-authoring/references/instruction-patterns.md`, "Data health check".

## Status lifecycle

The deal schema enum is `sourced | screened | diligence | ic | invested | rejected`. Map each status to exactly one owning pipeline:

| Pipeline | Reads rows with status | Writes |
|---|---|---|
| Sourcing (public data) | none (dedupes against all) | appends `sourced` |
| Intake (decks, forms) | none (dedupes against all) | appends `sourced` |
| Screening | `sourced,screened` | `screened` + score + updated_at |
| Due diligence | best `screened` with empty dd_findings | dd_findings link + `diligence` |
| Memo / committee | the target row | `ic` + updated_at (keeps score) |
| Portfolio monitoring | `invested,ic,diligence` | read-only, or updated_at |
| Copilot (Q&A) | any | read-only |

Only humans (or an explicitly HITL-gated step) set `invested` or `rejected`. Details, transition table and adaptation to other domains: [references/status-lifecycle.md](references/status-lifecycle.md).

## Human control on writes

`sheets_create`, `sheets_append_row`, `sheets_update_by_header`, `sheets_update_range` and `sheets_delete_rows` are write tools. With `hitl_mode = "safe"`, only tools listed in an agent's `human_approval_tools` are gated. Decide per system:
- Internal spine owned by the client team: usually NOT gated (the Sheet has version history), which keeps scheduled runs unattended.
- Spine shared outside the team, or writes that trigger other systems: gate `sheets_append_row` / `sheets_update_range`.

- Runs started by an event trigger are the exception: they ask for approval on EVERY tool that is not read-only (`sheets_create`, `sheets_append_row`, `sheets_update_by_header`, `sheets_update_range`, even local file writers such as `file_write` or `excel_to_csv`), listed or not. A triggered Recorder therefore waits for a person at each write. Prefer schedules or manual runs for spine writers, or accept the approvals. The `dealdb_*`, `decide_*` and `jev_*` tools are read-only and never pause.

State the choice in the client documentation. Never approve a gated write on the user's behalf.

## Verify the spine after a run

Use the MCP read-only path (`melaya_connector_call` with `tool = "sheets_read_range"` and `args = {"spreadsheet_id": ..., "a1_range": ...}`) and check:

- [ ] Row count grew by ADDED, not more (no duplicates).
- [ ] New rows start at column A and end at the last schema column (no shift).
- [ ] Every filled field has a `__src` and a valid `__status`.
- [ ] Written-back cells are on the right rows (spot-check 3 rows by company name).
- [ ] `status` values are only from the enum.

Memory policy: the Sheet IS the memory of the system. Keep crew `persistent_memory` off on spine pipelines. Crew memory keeps marker lines and automatic tool-failure notes per pipeline; a failure note that outlives the fix can steer runs until it fades, so if memory is on and a fixed tool keeps being avoided, have the user delete that entry in the builder's Memory tab (`../../modules/pipeline-authoring/GUIDE.md`, references/context-and-memory.md).

## Anti-patterns

| Do not | Do instead |
|---|---|
| `sheets_read_range` without `save_to` on a wide table | `save_to = "rows.json"`, pass the file |
| Trust `range` for the row count | Use `data_rows` |
| `sheets_append_row` with `row_json` for a 76-column row | `dealdb_normalize save_to` CSV + `csv_path` |
| Normalize or append one record per call | One normalize, one append for all records |
| Ask the model to find "the row of X" by reading | `[row N]` from decide_batch_file, or `values[i]` = row `i + 1` |
| Rewrite the whole row to change status | `sheets_update_by_header` with only the changed columns |
| Write back by column letter | `sheets_update_by_header` by column name |
| Clear a row to remove it | `sheets_delete_rows` (no blank gap) |
| Let the model compute or rescale a composite score | `dealdb_rank` with `components` and `component_max` |
| Score 50 items with 50 LLM turns | One `decide_batch_file` call |
| Reuse a sheet name that shares words with an older file | A new, distinct name everywhere |
| Let each pipeline invent its own status words | One enum in the schema, one owner per transition |
| Fill a missing number with a plausible guess | Leave it empty, `__status = MISSING` |

## Files in this module

- [references/schema-design.md](references/schema-design.md): provenance layout, column letters, schema for other domains.
- [references/recorder-pattern.md](references/recorder-pattern.md): the complete Recorder agent and its upstream RECORDS contract.
- [references/scoring-from-files.md](references/scoring-from-files.md): decide_batch / decide_batch_file / jev_* in depth, question design, cascade, write-back.
- [references/status-lifecycle.md](references/status-lifecycle.md): statuses, owners, transitions, readers.
