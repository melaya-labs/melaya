# Designed documents and brand themes

Rule: every human-facing output is a DESIGNED file, never a wall of plain text. Build the file locally,
publish it to Google Drive as a native Google file, report its link AND its file path, and let the mailer
attach the file.

Relative paths resolve into the run's output folder, so instructions can simply say `path = "memo.docx"`.
On cloud runs that folder belongs to the pipeline; on runs hosted by the user's runner it is
`Documents/Melaya-Pipelines/<pipeline>/outputs` on the user's computer. Absolute paths are used as given.
Cloud output files are not a long-term archive: publish anything a person must keep (Drive, email attachment).

## 1. Word report: word_create

Parameters: `path`, `title` (optional), `paragraphs` (simple mode), `theme`, `blocks`, `page_numbers`, `export_pdf`.

Blocks (rendered top to bottom):

| Block | Shape |
|---|---|
| cover | `{"type":"cover","title":"...","subtitle":"...","meta":"<Client> \| <date>","badge":"DRAFT - FOR REVIEW","kpis":[...]}` (own page; `badge` and `kpis` optional) |
| toc | `{"type":"toc","title":"Contents","levels":2}` (a real table of contents) |
| kpis | `{"type":"kpis","items":[{"value":"USD 2.0M","label":"Ticket","delta":"+210% YoY","tone":"up"}],"columns":4}` (headline tiles; `tone` up, down or neutral) |
| heading / subheading | `{"type":"heading","text":"..."}` / `{"type":"subheading","text":"..."}` |
| paragraph | `{"type":"paragraph","text":"..."}` |
| bullets / numbered | `{"type":"bullets","items":["...","..."]}` / `{"type":"numbered","items":[...]}` |
| quote | `{"type":"quote","text":"...","cite":"..."}` |
| callout | `{"type":"callout","title":"Recommendation","text":"...","tone":"success","bold":true}` (verdicts, key numbers; `tone` info, success, warning or danger) |
| divider / spacer / page_break | `{"type":"divider","color":"#hex"}` / `{"type":"spacer"}` / `{"type":"page_break"}` |
| image | `{"type":"image","path":"...","width":6,"caption":"..."}` |
| table | `{"type":"table","rows":[["Check","Status"],["KYC","PASS"]],"header":true,"status_col":1,"caption":"Source: ..."}` |
| chart | `{"type":"chart","chart_type":"barh","title":"...","labels":["Target","Peer A"],"series":[{"name":"EV/ARR","values":[22.9,18.0]}],"value_suffix":"x","highlight":"Target","reference":18.0,"reference_label":"peer median"}` (`chart_type` bar, barh, line, area, scatter or donut) |

- `status_col` (a column index or a list) colours status words: PASS, OK, YES, SOURCE and similar green; FLAG, PARTIAL, PENDING, INFERRED amber; FAIL, NO, MISSING red. Use it for check tables and for provenance columns, so a reader sees SOURCE / INFERRED / MISSING at a glance.
- The `chart` block is rendered in-house with the theme colours and placed inline: the data never leaves the platform. No chart service, no image upload.
- `export_pdf = true` also writes a matching branded PDF next to the `.docx` (same blocks and theme) and returns both paths: attach the PDF to the email, publish the `.docx` as a Google Doc.
- `page_numbers` defaults to on for branded themes.

Word theme presets: `minimal` (default), `executive`, `brand`, `ocean`, `midnight` (dark), `melaya`.
Custom dict keys: `preset`, `page`, `heading`, `accent`, `text`, `muted`, `font_head`, `font_body`, `logo`, `logo_on_dark`, `mark`, `label`, `footer`.

## 2. Spreadsheet: excel_write_data

Parameters: `path`, `sheet`, `data` (2-D list, first row = header), `start_cell` ("A1"), `overwrite_sheet`,
`style` ("modern" or "none"), `theme`, `title` (merged title row, only when start_cell is "A1"), `header`,
`number_formats` ({"1": "$#,##0", "4": "0.0%"}, 0-based column index), `conditional` ({"3": "data_bar"}: also
`color_scale` (heatmap, high = strong), `color_scale_reverse`, `traffic`, `traffic_reverse`, `icons`),
`chart` ({"type": "bar|column|line|pie", "title": "...", "cat_col": 0, "val_cols": [1, 2], "anchor": "below"}),
`links` (URL cells become clickable links with a short label; `{"5": "Open memo"}` sets the label per column,
`false` keeps the raw URL), `autofilter`.

Theme presets: `modern` (default), `emerald`, `sunset`, `slate`, `ocean`, `melaya`.
Custom keys: `preset`, `header`, `header_text`, `band`, `accent`, `text`, `border`, `font`.

## 3. Deck: pptx_create

Parameters: `path`, `title`, `slides`, `theme`, `subtitle`.
Slide dict: `layout` (content, title, section, bullets, split, quote, closing), `title`, `subtitle`, `body` (string or bullet list),
`image`, `background`, `accent`, `notes`, and free-form `elements` (text, panel, shape, chart, table, image; x/y/w/h in inches on a 13.333 x 7.5 canvas).
Native charts: `{"type":"chart","chart":"bar|column|line|area|pie|doughnut|radar","categories":[...],"series":[{"name":"...","values":[...]}]}`.

Theme presets: `midnight` (default, dark), `aurora`, `sunset`, `ocean`, `forest`, `mono`, `brand`, `minimal_light`, `paper`, `melaya`.
Custom keys: `preset`, `bg` ("#hex" or ["#hex","#hex"] gradient), `primary`, `accent`, `text`, `muted`, `font_head`, `font_body`, `logo`, `logo_on_dark`, `mark`, `footer`.

## 4. PDF: pdf_from_html

Parameters: `html_or_url`, `output_path`, `theme`.
Write HTML with h1/h2/h3, p, ul, table and `<div class="callout">`. Theme `"melaya"` or a dict
(`preset`, `accent`, `heading`, `font`, `logo`, `label`, `footer`); empty theme = the HTML as given. A URL input is printed as is.

## 5. Publish: drive_upload

Parameters: `name`, `content` (text mode), `mime_type`, `path` (local file, up to 50 MB), `folder` (a folder id, link or name path; found or created).

| Local file | mime_type to convert | Result |
|---|---|---|
| `.docx` from word_create | `application/vnd.google-apps.document` | Google Doc keeping fonts, colors, header logo, tables, callouts |
| `.xlsx` / `.csv` | `application/vnd.google-apps.spreadsheet` | Google Sheet |
| `.pptx` | `application/vnd.google-apps.presentation` | Google Slides |
| `.pdf` | `application/pdf` (or omit conversion) | Stored as PDF |

Returns `id` and `webViewLink`. The file is created in the user's Google Drive; Melaya can later see only files it created or the user picked with it (a file the client made by hand is invisible to `drive_search` until picked).
Requires the Google Drive connector; ask the user to connect it before you rely on it.

Organise the Drive, never dump files at its root:
- `drive_create_folder` with `name` = a name path such as `"Client Pilot/Memos"`: each level is found or created, and running it again returns the SAME folder. Give every pipeline its own subfolder under one client folder.
- Pass that path (or the returned id) as `folder` to `drive_upload` and `sheets_create`.
- `drive_move` (`file_id`, `folder`) files an existing document into a folder; moving a file that is already there is a no-op.

Reading designed documents back: `docs_read` returns tables as markdown rows (`| a | b |`), so a downstream agent sees scorecards and check tables. (Older behaviour skipped tables and made designed documents look empty; if an agent reports an empty table-heavy document, re-run on the current platform before changing the design.)

## 6. Brand themes (dynamic per run)

Default: `theme = "melaya"`. For a client or any other brand, pass a dict:

```json
{"preset": "melaya", "accent": "#0A7C66", "heading": "#0B2545",
 "font_head": "Poppins", "font_body": "Calibri",
 "logo": "<path of the attached logo from the Run inputs block>",
 "mark": "<same path or empty>",
 "footer": "<Client> | Confidential"}
```

Rules:
- A dict starts from its `preset`, then your non-empty keys override it.
- A brand key set to an empty string (`"logo": ""`, `"mark": ""`) REMOVES that element. Without it, `"preset": "melaya"` keeps the Melaya logo.
- The logo file comes from the run inputs (an attached PNG): the Run inputs block shows its path.
- Instruct the agent to switch automatically: "If this run's inputs carry another brand (an attached logo image and/or brand colours or a company name in the brief), pass a theme dict instead ... so the document carries that brand and never the Melaya logo."
- Spreadsheet dicts use `header` / `accent`; decks use `primary` / `accent` / `bg`.
- Theme dicts sent as JSON strings are parsed, but say "a theme dict" explicitly.

## 7. The DESIGN rule block (paste into every writer)

```
Every document you produce is DESIGNED, never plain text. For each document: invoke word_create with
path = "<short-file-name>.docx", theme = the brand theme (see Brand), and blocks = a cover block
{"type":"cover","title":"<title>","subtitle":"<what it is>","meta":"<Client> | <today ISO date>"} followed by
{"type":"heading"} sections, {"type":"paragraph"} text, {"type":"bullets"} lists, {"type":"callout","bold":true}
for verdicts and key numbers, and {"type":"table","rows":[[header...],[row...]]} for snapshots, scorecards and
comparisons. Then publish it: invoke drive_upload with name = the document title, path = that .docx,
mime_type = "application/vnd.google-apps.document" and folder = "<Client folder>/<pipeline subfolder>".
Report "DOC: <webViewLink>" and "FILE: <the .docx path>" for each document.
```

Richer variant for decision documents (memos, due-diligence reports, scorecards): add a `badge` on the cover
("DRAFT - FOR REVIEW"), a `kpis` block with 3 to 4 headline numbers right after the cover, a `toc` for long
documents, a `table` with `status_col` for every check list and for field provenance, a `chart` for peer or trend
comparisons, a `callout` with `tone` for the verdict, and `export_pdf = true` when the email must carry a PDF.
Every number in a KPI tile, chart or table comes from a tool result or a cited source, never from the model.

Give each document type a fixed section list in the instruction (e.g. a one-pager: executive summary,
snapshot table, score callout, strengths, weaknesses, risk -> mitigant table, open questions).
Title documents with a stable prefix ("<Client> - DD Findings - <company> - <date>") so later pipelines
can find them with `drive_search`.

## 8. Checklist

- [ ] Writer tools include `word_create` and `drive_upload` (+ `drive_create_folder`, `excel_write_data` / `pptx_create` / `pdf_from_html` when used).
- [ ] Every output lands in its pipeline's Drive subfolder (`folder` on `drive_upload` / `sheets_create`), not the Drive root.
- [ ] Every document has a cover, headings, at least one callout and one table where data exists.
- [ ] Theme default + dynamic brand rule present.
- [ ] Reply contains `DOC:` and `FILE:` lines plus the key content (the next agent reads messages, not files).
- [ ] Mailer attaches the FILE paths.
- [ ] Verify one real document after the first run (open the Doc; check the logo, tables, callouts).
