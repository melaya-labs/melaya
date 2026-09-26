# Scoring and triage from files

Use System One scoring whenever the same judgment must be applied to many items: triage of headlines, screening of every sheet row, routing of inbound messages, ranking of candidates. One tool call replaces dozens of LLM turns and the model never copies the items.

## 1. Pick the tool

| Situation | Tool | Cost |
|---|---|---|
| One item, several questions | `decide` | free, self-hosted |
| One item, one yes/no or one choice gate | `decide_check` | free |
| A list the agent already holds (short strings) | `decide_batch` | free |
| Rows in a file (sheet `save_to` JSON, CSV, JSON list) | `decide_batch_file` | free |
| Same, but hosted speed or an SLA is needed | `jev_batch` / `jev_batch_file` | paid (Jev connector, billed per token) |
| One label per item, hosted | `jev_classify_batch` | paid |
| One spectrum per item, hosted | `jev_score_batch` | paid |

Default to the free `decide_*` tools. Move to `jev_*` only when the client has the Jev connector and wants hosted throughput; the question schema is the same, so only the tool name and a few optional arguments change.

## 2. Question schema (shared by decide_* and jev_*)

`questions` is a JSON object `{question_id: spec}`:

```json
{
  "startup_round": {"type": "noul", "instructions": "This headline announces that an operating startup raised a funding round (not a fund, public company, token price move or opinion piece)."},
  "thesis_fit": {"type": "score", "instructions": "Fit with the fund thesis: <one sentence thesis>.", "criteria": ["none", "weak", "partial", "good", "core"]},
  "channel": {"type": "choice", "instructions": "How did this opportunity reach us?", "criteria": {"news": "a press article", "deck": "a pitch deck", "referral": "an introduction by a person"}}
}
```

| Type | criteria | Answer |
|---|---|---|
| `noul` | none | probability 0..1 that the statement is true, plus confidence |
| `score` | ORDERED array of 2 to 10 short level labels | expected level index 0..k-1 (a float), plus confidence |
| `choice` | MAP option -> description | winning option, per-option probabilities, confidence |

Writing good questions:
- Every question needs `instructions`. Write a statement for `noul` ("This X is Y"), a dimension for `score`, a question for `choice`.
- Score labels are short words, never sentences, lowest first.
- Put exclusions in the instruction ("not a fund, not a public company") instead of a second question.
- Keep one idea per question; combine them in code afterwards.
- Malformed schemas are auto-repaired, but do not rely on it; some models send the object as a JSON string, which the tools accept.

Mapping a `score` answer to 0-10: `answer / (levels - 1) * 10`. With 5 levels, 3.2 -> 8.0.

## 3. decide_batch_file on a sheet

Arguments (verified behaviour):

| Argument | Meaning |
|---|---|
| `items_path` | The `save_to` file of `sheets_read_range` (`{"values": [header, ...]}`), a `.csv`, a JSON list, or a JSON object with a list under `items_key` |
| `questions` | The schema above |
| `item_template` | Text built from column names, e.g. `"{company} | {country} | {stage}"`; unknown columns render empty |
| `include_if` | ONE condition `"column=v1,v2"`; column name exact, values case-insensitive |
| `limit` | Score at most this many items (0 = all) |
| `top_by`, `top_k`, `top_option` | Rank by a question (and by one option of a choice), show the top K inline (default 20) |
| `cascade_by`, `cascade_keep` | Gate question and the fraction that advances (default 0.25) |
| `save_to` | JSON file with every row, ranked when `top_by` is set |
| `model`, `item_key` | Optional checkpoint alias; text field when items are dicts |

Behaviour to rely on:
- Each sheet row becomes one string `"[row N] <template text>"`, where N is the real sheet row (header = row 1; blank rows are skipped, numbering is kept). Read from `A1` or the numbering is wrong.
- Without `item_template`, the item is every non-empty column except `__src` and `__status`.
- Hard cap 5000 items per call. Large calls are chunked and run two chunks at a time; slow chunks split and retry, so partial results beat a total failure.
- The reply starts with `decide_batch: <ok>/<n> scored`, then the ranked block when `top_by` is set: each line `N. [value] <item label>` followed by every other answer, so the next step can act without opening the file.

Ready-to-paste screening step:

```text
Invoke sheets_read_range with spreadsheet_id = SHEET_ID, a1_range = "<TAB>!A1:BZ2000" and save_to = "rows.json". Never copy the table yourself.
Invoke decide_batch_file with items_path = "rows.json", include_if = "status=sourced,screened", item_template = "{company} | {website} | {country} | {sector}/{sub_sector} | {stage} | model: {business_model} | round: {round_type} {round_size_usd} | traction: {traction_notes} | fit: {strategic_fit} | risks: {risks}", questions = <QUESTIONS JSON>, top_by = "strategic_fit" and top_k = 500. Every row comes back as "[row N] <company> | ..." with all scores; N is its sheet row.
Never compute the composite yourself. Invoke dealdb_rank with rows = a JSON array of {"company": ..., "row": N, "<criterion>": <raw answer 0-4>, ...} (one entry per scored row, the raw score answers as returned), components = "market:1,team:1,product:1,strategic_fit:2", component_max = 4 and top = 25. Its score is the 0-10 composite.
Invoke sheets_update_by_header ONCE with spreadsheet_id = SHEET_ID, sheet = "<TAB>" and updates_json = one {"row": N, "fields": {"status": "screened", "score": <dealdb_rank score>, "updated_at": "<today ISO date>"}} per scored row.
```

`dealdb_rank` then gives competition ranks (1, 2, 2, 4), marks ties, lists unscored rows and warns when score formats are mixed ("8/10", "75%", bare numbers are all normalized to 0-10). `exclude_status` defaults to `"rejected"`.

## 4. decide_batch on raw candidates (before they reach the sheet)

Triage cheap text first, confirm expensively later:

```text
Phase 1 (ONE parallel batch): collect candidate headlines from the feeds.
Phase 2 (ONE call, free): invoke decide_batch with items = a JSON array with one string per headline "<title> | <published date> | <link>", questions = <TRIAGE QUESTIONS JSON>, top_by = "thesis_fit" and top_k = the number of items. Candidates = rows with startup_round above 0.5, ordered by thesis_fit.
Phase 3 (ONE parallel batch): open only the best TARGET candidates to confirm the facts.
HARD BUDGET: at most 40 tool calls. Stop at TARGET.
```

Keep each item a short one-line string. Long items cost time and the model must type them; for more than a few dozen items write them to a file first (a harvest tool that saves JSON) and use `decide_batch_file`.

## 5. Cascade for big harvests

`cascade_by = "<gate question>"`, `cascade_keep = 0.25`: every item is scored on the gate question only, the top 25 percent get the remaining questions. Decisions drop from N x Q to about N + 0.25 x N x (Q - 1). Rows that did not advance carry only the gate answer and the flag `cascaded_out`. Use it when the gate must be passed anyway (relevance before fit and intent). Not available on `jev_*`.

## 6. The jev_* twin

| decide_* | jev_* equivalent | Differences |
|---|---|---|
| `decide_batch(items, questions, ...)` | `jev_batch(items, questions, concurrency, model, save_to, top_by, top_k)` | concurrency default 8 (max 16); model default `jev-latest`; top_k 0 means 10 inline; no cascade, no top_option |
| `decide_batch_file(items_path, ...)` | `jev_batch_file(items_path, questions, items_key, limit, concurrency, model, save_to, top_by, top_k, item_template, include_if)` | same file handling and `[row N]` tagging |
| `decide(state, questions)` | `jev_decide(state, questions)` | |
| `decide_check(state, statement, options)` | `jev_check` (yes/no) / `jev_classify` (choice) | |

`jev_batch` always writes the full results to a JSON file (rows `{item, answers}`) and returns per-question tallies, the optional top block, token usage and the saved path. It needs the Jev connector; if it is not connected, ask the user to connect it (the connect link comes from `melaya_connector_connect`) or stay on `decide_*`.

## 7. Speed, budgets and engine differences

- The free engine runs on CPU: roughly 0.2 to 0.5 s per decision (one item x one question). 500 rows x 3 questions is 1,500 decisions, i.e. minutes. Use `cascade_by` for big sets and keep items short.
- Each account has a per-minute decision budget that grows with the plan. One call over 200 items and 4 questions uses 800 decisions. Over budget, the call is rate-limited: wait a minute and retry once, do not loop.
- Large calls are split into chunks internally; a slow chunk is split again and retried, so a partial result beats a total failure. Read the `<ok>/<n> scored` line.
- The same schema works on both engines, but they are different models with different calibration. When a workflow moves between `decide_*` and `jev_*` (or the `decide` step's `engine`), re-check its thresholds (for example "startup_round above 0.5") on a few known items.
- A malformed question schema is repaired when possible (a score scale written as prose, options as a list), but a repair can change meaning: keep `criteria` as an ordered list for `score` and a map for `choice`.
- The `decide` STEP (a pipeline step kind, no agent) uses the same engines for one gate on the previous step's text; see `../../../modules/pipeline-authoring/GUIDE.md`, references/step-kinds.md.

## 8. Checks after a scoring run

- [ ] The reply shows `<ok>/<n> scored` with `n` = the rows you expected (check `include_if` if it is lower; the error `no rows left after include_if` means no row matched).
- [ ] Every write-back used a `[row N]` from the output, not a row the model counted itself.
- [ ] Composite scores are on the sheet's single scale.
- [ ] Rows outside `include_if` were not touched.
