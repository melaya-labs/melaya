# Artifact verification (read-only)

Verify what the client will see, directly, with `melaya_connector_call`, using
READ tools only. Write tools run there only when the user granted
`melaya:connectors.write`, and are never used for verification. Find a tool's exact parameters with
`melaya_connector_tools { "tool": "<name>" }` before calling it.

Get file ids and links from the run itself (`melaya_run_inspect` with
`include_tool_calls`: the `sheets_create`, `drive_upload`, `word_create`
replies) or by name with `drive_search`. Never paste ids into skills or docs.

## Read-only tools used for checks

| Tool | Service | Use |
|---|---|---|
| `drive_search(name, mime, folder_id, limit)` | google_drive | Find the files a run created (only files created by Melaya or picked by the user are visible) |
| `drive_list(query, page_size)` | google_drive | List visible files with a Drive query filter |
| `sheets_list_tabs(spreadsheet_id)` | google_sheets | Tabs and sizes; find the data tab |
| `sheets_read_range(spreadsheet_id, a1_range)` | google_sheets | Read rows for checks |
| `docs_read(document_id)` | google_docs | Title, body text (up to about 30,000 characters), `length` of the full body |
| `gmail_search(query, limit)` | gmail | Rows with sender, subject, date, attachment names |

## Data spine Sheet checklist

1. Locate: `drive_search { "name": "<store name>", "mime": "sheet" }`.
   Exactly ONE match expected. Two matches = the name collides with an older
   file: Drive name search matches words, not the exact title, so "Acme Deal
   DB" also finds "Acme Old Deal DB". Rename the store in the generator to a
   name that shares no full word set with other files, and check the id the
   run actually wrote to (from the trace), not just the first search hit.
2. Tabs: `sheets_list_tabs`. Always quote the tab in ranges; an unnamed range
   reads only the first tab.
3. Header: read row 1 only, for example `"'Deals'!1:1"`. Compare to the
   schema (`dealdb_schema` output recorded in the generator). Any drift = the
   writer or the schema changed.
4. New rows: read a narrow, bounded window rather than the whole grid, for
   example `"'Deals'!A1:H40"` for identity columns, then the provenance and
   status columns for the same rows. Wide reads are truncated in the reply.
   Ignore the `range` echo for counts; count the rows actually returned.
5. Alignment: for 3 sampled new rows, check that each value sits under the
   right header (company under company, country under country, money fields
   numeric). One shifted cell means the row was retyped: see failure
   catalogue ("never make the model retype big data").
6. Provenance: each filled field has `<field>__src` (a URL or tool name) and
   `<field>__status` in SOURCE / INFERRED / MISSING. No numeric value without a
   source. Failed sources show MISSING, never "clear".
7. Status lifecycle: rows advanced to the expected status for this pipeline
   (for example `sourced` -> `screened`), and `updated_at` moved.
8. Write-back: scores/status landed on the exact `[row N]` rows the scorer
   returned, in the fixed columns, and nothing else changed.
9. Duplicates: no company/website appears twice among new rows or against
   existing rows.
10. Row delta: rows before vs after the run match the agent's claimed count.

Record the counts in the validation ledger (before, after, sampled rows OK).

## Document checklist (DD report, memo, benchmark, forms)

1. Locate by name with `drive_search` (`mime: "doc"`), or take the id from the
   `drive_upload` reply in the trace.
2. `docs_read { "document_id": "<id>" }`.
3. Title follows the naming rule and names the subject of this run.
4. Structure present: cover, headings, tables, callouts as designed. A plain
   text dump means the pipeline skipped `word_create` + `drive_upload`.
5. Content: the subject is the one in the run brief/inputs; numbers match the
   Sheet row (SOURCE fields); citations present; MISSING stated honestly.
6. No placeholders (`[TODO]`, `<company>`, `[START EDIT ME]`), no raw tool
   errors, no JSON fragments, no leaked internal paths.
7. `length` is plausible; `text` is capped at about 30,000 characters so a short `text` on a very long
   doc is expected, a small `length` is not. To check the rest of a long doc
   (anything past about 30,000 characters), do not conclude from
   the returned text. Either ask the user to open the link and confirm the later
   sections exist, or verify through the pipeline: a reader agent calls
   `drive_export` with `mime: "txt"` (or `"pdf"`) and then `file_read` /
   `pdf_to_text` on the returned `path`, which gives the full text. The same
   applies when an AGENT must read a long reference Doc: instruct it to export
   and read the file, not to rely on `docs_read`.
8. Local files (docx/xlsx/pptx/pdf in the run folder): read with `word_read`,
   `excel_read_sheet`, `pptx_read`, `file_read` when they are reachable from
   the runner; otherwise check the Drive copy.

## Mail checklist

Validation mail goes only to the owner's own inbox.

1. `gmail_search { "query": "newer_than:1d subject:\"<subject prefix>\"", "limit": 10 }`
2. Exactly one message per run.
3. Subject differs from the body opening; plain ASCII.
4. Attachment names present and match the files the run created.
5. No raw errors; failures name the service and give the reconnect link.

## When a check fails

- Wrong data in the right place: prompt/contract defect -> regenerate config.
- Right data in the wrong place: write-back/column mapping defect -> config.
- Tool wrote something different from what it was given (compare trace args
  with the artifact): platform defect -> `fix-handoff.md`.
- Never correct the artifact by hand to make validation pass.
