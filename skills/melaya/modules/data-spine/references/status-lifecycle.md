# Status lifecycle across pipelines

The `status` column is the contract between pipelines. Each pipeline selects its input rows by status and writes one next status. No pipeline passes rows to another in memory: the next pipeline finds them in the spine.

## 1. The deal lifecycle (dealdb enum)

`sourced -> screened -> diligence -> ic -> invested`, and `rejected` from any stage.

`dealdb_normalize` maps common synonyms onto the enum: new / inbound / lead -> sourced; first call / qualified / review -> screened; dd / due diligence / deep dive -> diligence; committee / term sheet -> ic; closed / portfolio / funded -> invested; pass / declined / dead / lost -> rejected. Unknown words are flagged, not guessed.

## 2. Who writes what (worked example: an early-stage investment team)

| # | Pipeline | Input selection | Writes to the spine | Cells |
|---|---|---|---|---|
| 1 | Sourcing (public news, registries) | none; dedupes against all rows | new rows, status `sourced` | append via CSV |
| 2 | Intake (decks, forms, inbound email) | none; dedupes against all rows | new rows, status `sourced` | append via CSV |
| 3 | Screening | `include_if = "status=sourced,screened"` | status `screened`, score, updated_at | `BV<row>:BX<row>` |
| 4 | Due diligence | the brief's company, else the highest score with status `screened` and an empty dd_findings | dd_findings = document link, status `diligence` | `BP<row>`, `BV<row>` |
| 5 | Memo / committee pack | the brief's company (row found by name) | status `ic`, keeps score, updated_at | `BV<row>:BX<row>` |
| 6 | Form pre-fill | the brief's company | nothing (reads the row and its documents) | none |
| 7 | Benchmark | the brief's company vs all rows (`dealdb_benchmark`) | nothing | none |
| 8 | Copilot (questions and answers) | any row | nothing | none |
| 9 | Portfolio monitoring | status `invested,ic,diligence` | nothing, or updated_at only | optional `BX<row>` |

Humans own `invested` and `rejected`: they edit the Sheet directly, or a HITL-gated step writes them after approval. An automated pipeline never closes a deal.

Rules:
- One owner per transition. If two pipelines can set the same status, one of them is wrong.
- Keep the previous value when a later stage does not own it (the memo step writes `ic` but copies the existing score, or writes only `BV` and `BX`).
- Every writer sets `updated_at` (ISO date from the run's date context).
- Readers that write nothing get only read tools (`drive_search`, `sheets_read_range`, `dealdb_*`), never `sheets_update_range`.

## 3. Passing the row between steps of one pipeline

Inside a pipeline, the step that locates the row reports it; later steps copy it:

```text
Always locate the company in the database (drive_search + sheets_read_range with save_to). Sheet row number: values[0] is the header = sheet row 1, so the company at values[i] is sheet row i + 1.
End your reply with:
SHEET: <sheet URL or none>
ROW: <sheet row number of the company or none>
```

For a parallel step, tell ONE analyst to start its reply with the previous step's lines copied unchanged, because the joined output contains only the parallel agents' replies:

```text
Start your reply with the TARGET, SHEET and ROW lines of the previous message copied unchanged (the next step only sees your reply).
```

The writer then uses them:

```text
If a ROW line gives a number and a SHEET line gives the sheet: invoke sheets_update_by_header with spreadsheet_id = the id in the SHEET URL, sheet = "<TAB>" and updates_json = [{"row": <ROW>, "fields": {"status": "ic", "updated_at": "<today ISO date>"}}] (the score is left as it is).
```

## 4. Default target when there is no brief

A stage pipeline can pick its own next item from the spine, which makes it schedulable:

```text
If there is no brief, find the database, read it with save_to, and take the highest-score row whose status is "screened" and whose dd_findings is empty.
```

Pair this with run inputs: a brief naming a company always wins over the default selection.

## 5. Designing a lifecycle for another domain

1. List the business stages as verbs a person would say ("found", "qualified", "contacted").
2. Make each a lowercase enum word; add one terminal negative (`rejected`, `lost`, `blocked`).
3. Assign each transition to exactly one pipeline or to a human.
4. Write the table in section 2 for the new domain before authoring any config.
5. Put the enum in the schema constant, the `include_if` strings and the write-back instructions from the same constant in the generator script.

Example (B2B leads): `found` (Sourcing) -> `qualified` (Scoring, `decide_batch_file`) -> `contacted` (Outreach, HITL-gated send) -> `replied` (Inbox reader) -> `won` / `lost` (human).
