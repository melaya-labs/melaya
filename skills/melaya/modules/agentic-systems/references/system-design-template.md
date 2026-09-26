# System design template

Fill every section in phase 4 and get the user's approval before authoring. Keep it in the engagement files next to the generator script; the generator's shared constants come from sections 1, 4 and 7.

## 0. Header

| Field | Value |
|---|---|
| System name | `<name>` |
| Client | `<Client>` |
| Project | `<project, created with melaya_project_create or by the user in the app>` |
| Mode | pilot / production |
| Validation model | `<provider>/<model id from melaya_model_list>` |
| Production model | `<provider>/<model id>` (may equal validation) |
| Language | `user_lang` value |

## 1. Requirements

| Id | Business outcome | User of output | Cadence | Trigger | Must never |
|---|---|---|---|---|---|
| R1 | | | | manual / cron / event | send / pay / publish without approval |

Shared policy text (becomes a generator constant, pasted into every judging agent): `<thesis / policy / scope in 2 to 4 sentences>`.

## 2. Workflows

| Id | Workflow | Starts when | Plain-words steps | Output | Covers |
|---|---|---|---|---|---|
| W1 | | | | | R1, R3 |

## 3. Pipelines

One per workflow. For each:

| Pipeline | Steps (-> sequential, [a, b] parallel, <decide>) | Tools per agent (verified ids) | Gated tools | Reads spine | Writes spine |
|---|---|---|---|---|---|
| P1 `<slug>` | Scout -> Recorder -> Mailer | Scout: ...; Recorder: ...; Mailer: gmail_my_address, gmail_send | gmail_send (production) | yes | append |

Per agent also record: tool-call budget, stop condition, output contract (the exact reply shape, for example `RECORDS:` JSON array, `SUMMARY:` line, `CITE:` lines, `FILE:` lines, `SHEET:` link).

Dependency graph (mermaid): which pipeline produces spine rows or statuses that another consumes. This is the validation order.

## 4. Data spine

| Field | Value |
|---|---|
| Store | Google Sheet `"<unique name sharing no full word set with older files>"`, tab `"<tab>"` |
| Created by | the first recorder, when `drive_search` finds nothing (schema tool -> `sheets_create`) |
| Header | row 1 |
| Dedupe key | `<company + website>` with a match threshold |
| Columns | `<field>`, `<field>__src`, `<field>__status` (SOURCE / INFERRED / MISSING) for every sourced field |
| Status lifecycle | `new -> screened -> diligence -> committee -> portfolio / passed` |
| Write-back | fixed column letters computed from the schema, exact `[row N]` targets |
| Writers | P1 append, P3 write-back `<cols>` |
| Readers | all consumers read to a file (`save_to`), never through the model |

## 5. Run inputs

| Pipeline | Brief meaning | Declared inputs (key, type, required) | Files |
|---|---|---|---|
| P1 | steer theme/region/count | none | none |
| P2 | target company | `company` text required | deck (pdf), logo |

Placeholders used in instructions: `{{brief}}`, `{{inputs.<key>}}`.

## 6. Outputs

| Pipeline | Spine effect | Documents (type, theme, published as) | Email (to, sends, attachments) |
|---|---|---|---|
| P2 | `dd_findings`, status | Doc via `word_create` + `drive_upload` | owner inbox, 1 send, FILE paths |

## 7. Governance

| Topic | Decision |
|---|---|
| `hitl_mode` | safe |
| Gated tools per agent | every send and external write |
| Pilot exceptions | e.g. mailer to the owner's own inbox ungated, agreed with the user on `<date>` |
| Schedules | `<pipeline>`: `<cron>` `<IANA tz>` requires_runner y/n |
| Event triggers | `<kind>` -> `<pipeline>`, rate caps, runs per day |
| Memory | spine is the memory; `persistent_memory` off unless the last step's reply is worth replaying |
| Evals | `loop_policy` `{"mode": "observe_only", "evaluator": "default"}` on every agent |
| Data boundary | public data + connected services only; no client data outside the project |
| Cost ceiling | per run and per month |
