# Requirement -> tool mapping template

Fill one row per atomic requirement (one verb, one outcome). Every `Tool ids` cell must be copied from `melaya_pipeline_registry` or `melaya_connector_tools` output, never typed from memory. Keep this table in the working notes of the build; the authoring phase turns each workflow group into a pipeline and each row into agent tools and instructions.

## Columns

| Column | Content |
|---|---|
| # | R1, R2, ... |
| Workflow | the business workflow the row belongs to (becomes one pipeline) |
| Requirement | one sentence, client words |
| Evidence needed | what fact or artifact proves the requirement is met |
| Source class | 1 registry/data tool, 2 known URL (`scrape_page`), 3 feed (RSS/GDELT), 4 `web_search` to discover URLs, R run input (files), C connector data |
| Tool ids | exact registry ids, in the order the agent should try them |
| Access | keyless / keyless* / free key / connector:<service id> / run input / platform |
| Read or write | read, or write (write tools go to `human_approval_tools` in the authoring phase) |
| Output | where the result lands: sheet column(s), document section, email |
| Status | verified-registry / verified-live / needs-connect / gap |
| Notes | limits, rate caps, fallbacks, open questions |

## Blank table

```
| # | Workflow | Requirement | Evidence needed | Source class | Tool ids | Access | R/W | Output | Status | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| R1 |  |  |  |  |  |  |  |  |  |  |
```

## Worked example (an early-stage investment team, generic)

| # | Workflow | Requirement | Evidence needed | Source class | Tool ids | Access | R/W | Output | Status | Notes |
|---|---|---|---|---|---|---|---|---|---|---|
| R1 | Sourcing | Find newly funded startups in the thesis sectors every week | company name, website, round, source URL | 3, then 2 | `rss_fetch_multiple` (Bing News RSS per sector), `gdelt_search_articles`, `edgar_form_d_search`, `scrape_page` | keyless | read | deal DB new rows | verified-registry | bounded phases: one batch of feeds, one triage call, one batch of confirmations |
| R2 | Sourcing | Drop candidates outside the thesis before any deep work | keep/drop per candidate with reason | local | `decide_batch_file` | platform | read | status column | verified-registry | one free call over the saved candidate file |
| R3 | Intake | Avoid duplicates of companies already in the DB | match score per new record | local | `dealdb_dedupe` | local | read | skip or append | verified-registry | score >= 0.92 = duplicate |
| R4 | Intake | Store every deal in one shared sheet with provenance | row with `<field>__src` / `<field>__status` | C | `dealdb_schema`, `drive_search`, `sheets_create`, `sheets_read_range` (save_to), `dealdb_normalize` (save_to CSV), `sheets_append_row` (csv_path) | connector:google_drive, connector:google_sheets | write | the deal DB | needs-connect | ask for Sheets and Drive grants separately |
| R5 | Screening | Read the pitch deck the founder sent | claims with "deck p.N" citations | R | `read_run_input`, `deck_extract` | run input | read | screening memo facts | verified-registry | `read_run_input` appears only when the run has files |
| R6 | Due diligence | Confirm the company legally exists | register record + id | 1 | `gleif_search_entities`, `annuaire_get_company` / `brreg_get_entity` / `acra_entity`, `vies_check_vat` | keyless | read | DD section "Identity" | verified-registry | pick the register by country |
| R7 | Due diligence | Screen founders and company for sanctions | verdict per name + list dates | 1 | `sanctions_screen_batch`, `sanctions_list_status` | keyless | read | DD section "Compliance" | verified-registry | tool error = MISSING, never "clear" |
| R8 | Due diligence | Check crypto licensing where relevant | register verdict | 1 | `mica_check` | keyless | read | DD section "Regulatory" | verified-registry | UK FCA needs a free key |
| R9 | Due diligence | Assess product traction | scorecards with sources | 1 | `webtraffic_traction_report`, `rdap_domain_age`, `github_developer_scorecard` | keyless* | read | DD section "Traction" | verified-registry | GitHub anonymous limit 60/h |
| R10 | Memo | Produce a designed investment memo | Google Doc link | local, C | `word_create` (blocks + theme), `drive_upload` (google-apps mime) | connector:google_drive | write | memo document | needs-connect | theme from run inputs or default |
| R11 | Memo | Email the owner the memo | one email with attachment | C | `gmail_my_address`, `gmail_send` | connector:gmail | write | owner inbox | needs-connect | exactly one send; gate with HITL unless owner-only demo |
| R12 | Portfolio | Monitor on-chain treasury of portfolio companies | balances and inflows | 1 | `blockscout_address_token_balances`, `safe_multisig_health` | keyless / keyless* | read | portfolio sheet | verified-registry | Safe keyless is exploration-limited |

## Review before leaving discovery

- Any `gap` row has a proposed resolution: connect a service, a free key, a run input, or a code change request with the exact capability missing.
- Every `needs-connect` row has a `melaya_connector_connect` link sent to the user.
- Every connector row turned `verified-live` after one read-only `melaya_connector_call`.
- Every write row is listed for approval gating.
- Source class 4 (`web_search`) never stands alone: it is always followed by a class 2 read of the discovered URL.
