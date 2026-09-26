# Schema design for a data spine

## 1. The provenance layout

Every data field travels with two companions:

| Column | Content | Allowed values |
|---|---|---|
| `<field>` | The value, normalized (plain numbers, ISO codes, ISO dates, enum words) | Empty when unknown |
| `<field>__src` | Where it comes from | `"<file> p.N"` (deck page), a URL, `"<registry> <id>"`, or `"computed: <how>"` |
| `<field>__status` | How much to trust it | `SOURCE`, `INFERRED`, `MISSING` |

Meaning of the statuses:
- `SOURCE`: read directly from a document, filing, registry, website or the founder.
- `INFERRED`: computed or estimated (for example runway = cash / burn). `__src` says how.
- `MISSING`: not found, or the source failed. The value stays empty. A failed check is never "clear".

Columns without companions are machine or key columns: the row key (`company`), `status`, `score`, `updated_at`.

Two layouts are available from `dealdb_schema`:
- `layout = "interleaved"` (default, recommended): `website, website__src, website__status, country, ...`. A human reads each value next to its source.
- `layout = "grouped"`: all data columns, then all `__src`, then all `__status`. Easier to hide provenance, harder to audit.

Pick one per sheet and never mix.

## 2. What `dealdb_schema` gives you

Call: `dealdb_schema(tab = "Deals", layout = "interleaved")`. The tab name must be 1 to 60 characters without `[ ] * ? / \ : '`.

Return fields:
- `columns`: 28 fields with `name`, `type` (text, url, iso2, enum, int, usd, pct, months, number, date) and `desc`.
- `optional_columns`: `cash_usd` (enables runway = cash / burn).
- `header_count`: 76 in the interleaved layout.
- `a1_range`: `Deals!A1:BX1` (tab quoted automatically when it has spaces).
- `values_json`: the header as a one-row JSON 2-D array. Pass it as `initial_values_json` to `sheets_create`, or as `values_json` to `sheets_update_range` with `a1_range`.
- `conventions`: the `__src` / `__status` rules in words, `no_provenance_for`, and the money rule.
- `enums`: `stage`, `status`, `round_type`, and the `sector` taxonomy (sector -> sub-sectors).

The 28 fields in order: company, website, country, sector, sub_sector, stage, business_model, founded_year, team_size, revenue_usd, arr_usd, growth_pct, burn_usd_month, runway_months, round_type, round_size_usd, pre_money_usd, post_money_usd, investors, token, traction_notes, strategic_fit, risks, dd_findings, ic_feedback, status, score, updated_at.

## 3. Column letters (interleaved layout)

Column number of a field with provenance = 1 (company) + 3 x (its position among provenance fields). Letters for the fields pipelines write back:

| Field | Letter |
|---|---|
| company | A |
| website | B |
| strategic_fit | BJ |
| risks | BM |
| dd_findings | BP |
| ic_feedback | BS |
| status | BV |
| score | BW |
| updated_at | BX |

`status`, `score`, `updated_at` are contiguous, which keeps them readable side by side. Write them by column NAME with `sheets_update_by_header`, never by these letters: letters are for reads and checks.

Compute letters with a tiny helper in your generator script instead of by hand:

```python
def col_letter(n: int) -> str:
    s = ""
    while n:
        n, rem = divmod(n - 1, 26)
        s = chr(65 + rem) + s
    return s

header = [...]                      # the header list you emit
STATUS_COL = col_letter(header.index("status") + 1)   # "BV" for the deal schema
```

Read ranges: use a range a few columns wider than the header and long enough for growth, for example `A1:BZ2000`. Empty trailing cells are harmless.

## 4. What `dealdb_normalize` does to a row

Useful to know so upstream agents write values the tool can parse:

- Money (`revenue_usd`, `arr_usd`, `burn_usd_month`, `round_size_usd`, `pre_money_usd`, `post_money_usd`, `cash_usd`): parses "$2.5M", "2,500,000 USD", "AED 9m", "1.2bn", "750k"; a range "2-3M" becomes the midpoint and is flagged. `fx_mode = "convert"` (default) converts to USD with a fixed table (the reply states `fx_as_of`); `fx_mode = "explicit"` keeps the currency and writes `<field>__ccy`.
- Burn is stored positive.
- stage, round_type, country (ISO-2 from names, cities, ISO-3), sector/sub_sector (taxonomy), status (synonyms such as "dd" -> diligence, "pass" -> rejected), token, updated_at (ISO date), website (adds https://).
- Derived values: runway = cash / burn when runway is blank; post = pre + round, or pre = post - round. Each is set `INFERRED` with `__src = "computed: ..."`.
- Flags (never silent): pre + round != post, round > post, ARR > 10x revenue, valuation > 100x revenue, stage-atypical round size, implausible years or headcount, growth given as a fraction, unknown enums, stated runway vs cash / burn.
- Provenance: empty -> `MISSING`; `INFERRED` -> kept; filled with a `__src` -> `SOURCE`; filled with no `__src` -> `INFERRED` (flagged "no __src"). A country whose `__src` points to an email, domain, person's name or investor is blanked to `MISSING`. Keys outside the schema are dropped.
- `output = "summary"` (default) returns per-row flags and change counts; `output = "values"` returns the rows as a 2-D array. `save_to` (must end in `.csv`) always receives the full table; the reply carries `saved_csv`.

Header aliases are accepted on input (`name` -> company, `url` / `domain` -> website, `raise` -> round_size_usd, `valuation` -> pre_money_usd, `deal_status` -> status, and more), so a client's own CSV export can be normalized directly.

## 5. Designing the same spine for another domain

The `dealdb_*` tools are specific to company / deal rows. For another domain, keep the LAYOUT and the RULES, and write the schema as one constant in your generator script.

Template:

```python
FIELDS = [                  # (name, type, has_provenance)
    ("<row key>", "text", False),
    ("<dedupe key>", "url|email|id", True),
    ("<field 1>", "...", True),
    ...
    ("status", "enum", False),
    ("score", "number", False),
    ("updated_at", "date", False),
]
HEADER = []
for name, _t, prov in FIELDS:
    HEADER.append(name)
    if prov:
        HEADER += [name + "__src", name + "__status"]
```

Then create the sheet with `sheets_create(title, sheet_title, initial_values_json = json.dumps([HEADER]))` and write every letter the instructions need from `col_letter`.

Examples of spines built this way:

| Domain | Row key | Dedupe key | Status enum | Score |
|---|---|---|---|---|
| B2B leads | company | website domain | found, qualified, contacted, replied, won, lost | ICP fit 0-10 |
| Suppliers | supplier | registry id | found, vetted, audited, approved, blocked | risk 0-10 |
| Public tenders | tender title | tender reference | found, relevant, bid, submitted, won, lost | fit 0-10 |
| Candidates | name | profile URL or email | sourced, screened, interview, offer, hired, rejected | fit 0-10 |
| Incidents | title | ticket id | new, triaged, investigating, resolved, closed | severity 0-10 |

Without `dealdb_normalize` / `dealdb_dedupe` in another domain:
- Dedupe: `decide_batch` with a `noul` question "the two records describe the same <entity>" on pairs, or compare the dedupe key exactly in the instruction.
- Do not run `dealdb_normalize` on non-deal rows: it always emits the 76 deal columns first and appends unknown keys after them, which misaligns another schema.
- Append: have the Recorder write ALL new rows ONCE as a CSV file in HEADER order (`file_write` with a `.csv` path, header line first), or as a 2-D array with `excel_write_data` then `excel_to_csv`, and append with ONE `sheets_append_row` `csv_path` call. The header line of the CSV is skipped by `sheets_append_row`. The model still writes the rows once, but never rebuilds a wide row cell by cell inside `row_json`, and a `csv_lint` call with `expected_columns` can check the file before the append.

## 6. Naming the sheet

Drive `name` search matches whole words, not the exact title. Consequences:
- "Acme - Deal DB" also returns "Acme - Old Deal DB" and "Acme - Deal DB (copy)".
- "newest first" may pick the wrong one after a test copy.

Rules:
- One name constant, used by every pipeline.
- When you replace a spine (schema change, corrupted rows), give the new one a name that does not contain all the words of the old name (for example "Acme Deal Pipeline" replacing "Acme Deal DB"), or move the old one to trash.
- Never create test copies with the production name.
