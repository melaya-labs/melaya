# The Recorder pattern

A Recorder is the one agent that adds new entities to the spine. Every pipeline that discovers entities (public-data sourcing, intake of decks or forms, inbound email) ends its research step with a machine-readable contract and hands it to the same Recorder agent. Build the Recorder once in your config generator (a function returning the agent dict) and reuse it in every such pipeline.

## 1. The upstream contract (end of the research agent's instruction)

```text
For each company collect: company, website, country (ISO-2), sector, sub_sector, stage, business_model, round_type, round_size_usd, investors, traction_notes, founded_year. Put each source URL in the __src of the fields it supports, never in a key of its own.
Provenance is mandatory. For every field you fill, also set <field>__src (where it comes from: '<file name> p.N' for a deck page, a URL, or 'computed: <how>') and <field>__status: SOURCE (read directly from a document, filing, registry or the founder), INFERRED (estimated or computed by you) or MISSING (not found; leave the value empty). Never invent a number: a value without a source is INFERRED at best, otherwise MISSING.
Set status = "sourced" and updated_at = today's ISO date from the date context block.

Final reply, ALWAYS in exactly this format, even when you found few or no records (then RECORDS: [] and say why in SUMMARY):
RECORDS: <a JSON array of objects on ONE line, one per company, keys = the field names above plus <field>__src and <field>__status>
SUMMARY: <n> companies from <sources used>; <which sources failed, if any>
CITE: <company> - <source URL> (one CITE line per company)
Never answer with a narrative report instead of RECORDS.
```

Why each line matters:
- Explicit field list: keys outside the schema are dropped by `dealdb_normalize` (and reported), so data under a wrong key is lost.
- `RECORDS: []` on empty: the Recorder never has to guess whether the research failed or found nothing.
- `__src` and `__status` set by the researcher: `dealdb_normalize` marks a filled field `SOURCE` only when it has a `__src` (else `INFERRED`), and blanks a country guessed from an email, domain, name or investor.
- `SUMMARY` / `CITE` lines: the Recorder copies them unchanged so the mailer at the end can quote them.

## 2. The Recorder agent (paste, then replace the placeholders)

Tools: `drive_search`, `dealdb_schema`, `sheets_create`, `sheets_read_range`, `dealdb_dedupe`, `dealdb_normalize`, `sheets_append_row`.

```text
The previous message holds new records as a JSON array under a line "RECORDS:". Save each one to the deal database.

1. Find the deal database: invoke drive_search with name = "<SHEET NAME>" and mime = "sheet". Use the id of the newest result as SHEET_ID.
   If no sheet exists, create it: invoke dealdb_schema with tab = "<TAB>", then invoke sheets_create with title = "<SHEET NAME>", sheet_title = "<TAB>" and initial_values_json = the values_json returned by dealdb_schema. Use the new id as SHEET_ID.
2. Invoke sheets_read_range with spreadsheet_id = SHEET_ID, a1_range = "<TAB>!A1:BZ2000" and save_to = "deals.json" (the existing rows go to that file).
3. For EACH record (one parallel batch): invoke dealdb_dedupe with rows = "deals.json", new_company = the record's company and website = its website. If the best match has a score of 0.92 or more, skip the record and note "duplicate of <matched company>". If the read summary says data_rows = 0 (new sheet), skip this step: nothing can be a duplicate.
4. Invoke dealdb_normalize ONCE with rows = a JSON array of ALL the records that are not duplicates (only the field names listed in the previous step, no other keys) and save_to = "new_deals.csv". Then invoke sheets_append_row with spreadsheet_id = SHEET_ID, sheet = "<TAB>" and csv_path = "new_deals.csv" (it appends every row in the exact column order). Never pass row_json and never build a row by hand.
5. Final reply, exactly this shape:
SHEET: https://docs.google.com/spreadsheets/d/<SHEET_ID>/edit
ADDED: <n>
- <company> (<stage>, <country>): <one line on what it does>
SKIPPED: <n>
- <company>: <reason>
Keep every line of the previous message that starts with "SUMMARY" or "CITE" below that, unchanged.
```

Add one guard if the RECORDS array can be empty:

```text
If RECORDS is an empty array, do not create, read or write anything: reply with ADDED: 0 and the SUMMARY line.
```

(On a brand new system the very first run may have to create the sheet even with zero records; decide which behaviour the client wants.)

## 3. How each step behaves (observed)

| Step | Tool behaviour you rely on |
|---|---|
| drive_search | `name` is a contains/word match, results newest first, only files Melaya created or the user picked |
| sheets_create | `initial_values_json` must be a JSON 2-D array; it is written from A1 of `sheet_title` |
| sheets_read_range save_to | Full range to the file, summary back with `data_rows`; an EMPTY range returns a note instead of a file (new sheet with only the header still has a header row, so the file is written) |
| dealdb_dedupe | Errors with "no data rows" on a header-only file, hence the data_rows = 0 guard. Domain equality = 1.0; else name similarity after dropping legal suffixes (Inc, Ltd, LLC, GmbH, Labs, ...), plus domain-stem matches (0.97 x) and same stem with a different TLD (0.9). `verdict`: duplicate >= 0.92, possible_duplicate >= 0.75, else new. Returns up to `limit` (default 5) matches with `reasons` |
| dealdb_normalize | One call normalizes every row, fills derived values as INFERRED, flags inconsistencies, writes `saved_csv` with the canonical header first |
| sheets_append_row csv_path | Skips the CSV header line, drops blank lines, appends all rows in one call below the last non-empty row, entered as if typed by a person so numbers and dates parse |

## 4. Choosing the dedupe threshold

| Threshold | Effect | Use when |
|---|---|---|
| 0.92 (tool `duplicate`) | Skips only same domain or near-identical names | Many similarly named entities in the domain |
| 0.85 (messy name sources) | Also skips domain-stem and different-TLD matches | Mixed sources (news + decks) with inconsistent names |
| 0.75 (tool `possible_duplicate`) | Aggressive, may drop distinct companies | Almost never; prefer to flag for a human |

Alternative for `possible_duplicate` rows: append them anyway with a note in `traction_notes` ("possible duplicate of X") so a human merges them.

## 5. Failure patterns and fixes

| Symptom in the Sheet | Cause | Fix |
|---|---|---|
| Values shifted one or more columns right | Row built by the model and passed as `row_json` | `dealdb_normalize save_to` + `csv_path` |
| Only some records appended | One append per record, run stopped or model skipped | One normalize + one append for all |
| Same company twice | Dedupe skipped, or read without `save_to` so the model compared a truncated list | Dedupe against the saved file |
| Rows written to an old sheet | Name search matched an older file with the same words | Distinct sheet name; trash the old file |
| Extra columns after BX | Records carried keys outside the schema | List the allowed keys in the research instruction |
| Estimates marked SOURCE | Researcher left `__status` empty on a filled field | Researcher must set INFERRED explicitly |
| Numbers stored as text like "$2.5M" | Normalization skipped | Always normalize before appending |
