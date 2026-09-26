<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when: Use at the start of any Melaya agentic-system build, or whenever you must decide which Melaya tools, connectors and models a client's workflow can use. Covers melaya_setup_status and fixing each gap, discovering connected services (melaya_connector_list / _tools / _test), searching the pipeline tool registry (melaya_pipeline_registry), checking live model ids (melaya_model_list, melaya_runner_status), building a requirement -> tool mapping table, choosing KEYLESS public-data tools that prove value without client data, and asking the user to connect a missing service (melaya_connector_connect) then verifying access with a read-only melaya_connector_call. Also the full non-technical connectors playbook: kinds of connection (sign-in/OAuth, API key, cookie or session, local only), ready-to-send messages, what the user clicks, scopes and MCP consent in plain words (reads, writes with connectors.write, money-moving never), expired or revoked connections and reconnecting, multiple accounts and project connectors, per-family notes (Google, Microsoft, Slack, social, CRM/finance, data providers), paid-API costs, and what to do when no connector exists. Trigger phrases: "connect my Gmail", "how do I connect X", "my connection expired", "which account will it use", "what can Melaya do for this client", "which tools do we have for X", "is Gmail/Drive/Sheets connected", "map these requirements to tools", "discovery phase", "before we design the pipelines".

# Melaya discovery

Discovery is phase 1 of an agentic-system build. Its output is a short, verified inventory that every later phase trusts:

1. Setup state (account, runner, gaps) with each gap either closed or explicitly accepted.
2. Connected services, with a live test result for every service the system will depend on.
3. A requirement -> tool mapping table where every tool id was found in the registry (not guessed).
4. The model provider/model ids the pipelines will use, read from the live catalogue.
5. A list of missing connections the user has been asked to authorise, and their status.

Do not start designing pipelines until the mapping table has no "unverified" rows.

## 0. Ground rules

- Every tool id you write into a plan or a config must come from `melaya_pipeline_registry` (pipeline tools) or `melaya_connector_tools` (MCP-callable tools). An invented id does not error on save; it is silently dropped and the agent runs without it.
- Every model id must come from `melaya_model_list` (hosted) or `melaya_runner_status` (runner-local). A guessed id is accepted by the builder and fails only at run time.
- Never ask the user to paste a key, token or password into the chat. `melaya_connector_connect` has no field for one, on purpose.
- Never work around a missing grant (no scraping a logged-in page instead of Gmail, no asking for a CSV export "for now" unless the user chooses that). Ask, wait, verify.
- Connector access over MCP has three levels. Reads need the connectors permission. Writes (send an email, create or update a record or a file) need the user's `melaya:connectors.write` permission too; without it they are refused, and with it each one runs at once with no approval card and is audit-logged, so confirm it with the user first. Anything that moves money or trades (payments, refunds, purchases, transfers, ad budget or bid changes, orders) is refused at every level and stays an approval in the Melaya app. Repeated or unattended writes belong in a Melaya pipeline, under its approval rules. Never look for another route around a refusal.
- Pass `reason` (and `user_intent` when you know it) on every melaya_* call; it is optional but makes the audit trail readable.

## 1. Setup status first

Call `melaya_setup_status` at the start of the conversation and again whenever any tool fails with a setup-shaped error (no runner, no key, not connected, not reachable).

Read these fields:

| Field | Meaning |
|---|---|
| `ready` | true only when every step is done. Often false for reasons irrelevant to a cloud pipeline build (e.g. phone offline). |
| `account` | username, tier, role. The tier limits schedules, triggers and seats later. |
| `client.kind` | `local-cli` means you have a shell on the user's machine; hosted surfaces do not. |
| `runner` | `connected`, detected `models`, `claudeCodeAvailable`. |
| `steps[]` | each requirement with `done` and, when not done, an `action` that says exactly what fixes it. |
| `nextStep` | the single most important open gap. |

Act on each gap by relevance to THIS build:

| Gap (`steps[].id`) | Needed when | What to do |
|---|---|---|
| `account` | always | If not signed in, the OAuth consent flow handles sign-up; there is no create-account tool. |
| `runner` | the system uses runner-served models (`claude_code`, `codex`, `github_copilot`, `ollama`, `lmstudio`), local files/folders, or a session login that opens on the user's machine (LinkedIn, Luma) | Ask the user first (it is a long-lived process on their machine). Then `melaya_runner_setup`; with a shell, run the returned `npx` command yourself in the background and poll `melaya_runner_status`; without a shell, hand the command over and say which machine it must run on. Full runner and local-model procedure: the `../../modules/runners-models/GUIDE.md` skill. |
| `claude_code` | pipelines use provider `claude_code` | Ask the user to run `claude` once to sign in, then restart the runner. No key or connector needed. Other subscription CLI providers (`codex`, `github_copilot`): see the `../../modules/runners-models/GUIDE.md` skill. |
| `phone_paired` / `phone_online` / `allowed_apps` | only for phone-control systems | Ignore for a cloud data/document system; say so explicitly in the inventory. |

A cloud-only system (hosted models such as a qwen-class model, Google Workspace output, keyless public data) does not need the runner. Record "runner: not required" instead of blocking on it.

## 2. Discover connected services

### 2.1 List

`melaya_connector_list` returns:
- `services`: every service id the user has stored credentials for (names only, never values). The list also contains model providers (their API keys live in Connectors too) and a few internal profile entries; ignore those when mapping third-party services.
- `toolCounts`: per service, how many read and write tools it unlocks. `melaya_core` is the always-available keyless built-in family (web search, fetch, scraping, data utilities).

Write the relevant subset into the inventory. Typical ids for a document-producing system: `gmail`, `google_drive`, `google_sheets`, `google_docs`, `google_calendar`. Each Google product is its own connector and its own grant: Drive connected does NOT imply Sheets or Docs. Drive only sees files Melaya created or opened; Sheets and Docs open a file by the id in its link.

### 2.2 Find tools per service

`melaya_connector_tools` has two modes:
- `search`: plain business keywords ("unread email", "spreadsheet rows", "drive files"). Required for searching; the catalogue is thousands of entries. Single words match more than phrases.
- `tool`: an exact name, returns the full parameter list and its access label.

Results show `[service]` plus one label: `[read-only]` (callable with the connectors permission), `[write - requires the connectors.write scope]` (callable only when the user granted it) or `[moves money or trades - Melaya app approval only]` (never callable over MCP). Existing MCP connections made before these permissions existed must re-authorise to get them.

### 2.3 Test what the system depends on

`melaya_connector_test` with `service` = the exact id from the list. It calls the provider with the STORED credential (a few seconds) and returns `{"success": true|false, "message": ...}`. The message can name the connected account (an email): use it to confirm the right account with the user, never copy it into documents. Run it for every service a pipeline will depend on, and again when a run fails with an auth error. A failed test means reconnect (section 5), not "try another tool".

## 3. Search the pipeline tool registry

`melaya_pipeline_registry` searches the tools and subagents a pipeline config can reference.

- `search` (required, 2+ chars): capability words; all words must match name, category or description. Prefer one or two words ("sanctions", "edgar", "deck", "traction").
- `kind`: `tools`, `agents` or `both` (default).
- `limit`: up to 50 (default 20). Results are not ranked by relevance: if the obvious tool is missing, search its exact name or raise the limit (a search for "sanctions" with limit 5 can return only the OFAC helpers; "sanctions_screen" returns the multi-list screener).
- Each tool comes back with `id`, `category` (module/group), `read_only`, `description`. The `id` is what goes in an agent's `agent_tools`; write tools you plan to gate go in `human_approval_tools` later.

Important difference between the two catalogues:

| Catalogue | Tool | Contains | Callable from the conversation |
|---|---|---|---|
| Pipeline registry | `melaya_pipeline_registry` | every tool a pipeline agent can use (thousands, including all keyless domain tools such as sanctions, GLEIF, EDGAR, GDELT, DefiLlama, deal-DB, decide) | No. Only inside a pipeline run. |
| Connector catalogue | `melaya_connector_tools` | tools of connected services + the `melaya_core` family (e.g. `web_search`, `web_fetch`, `scrape_page`, `scrape_links`, `fetch_rss`) | Yes, via `melaya_connector_call`: reads always, writes with `melaya:connectors.write`, money-moving never. |

So a keyless domain tool (e.g. `sanctions_screen`, `gleif_search_entities`, `edgar_form_d_search`) is verified by: (1) registry search shows the id, (2) a small validation run proves the output (see the validate-debug skill). Do not expect `melaya_connector_call` to run it; it answers "Unknown tool".

Run-time-only tools: `read_run_input` is added automatically to every agent when a run has input files; it is not in the registry. Plan for it, do not search for it.

Platform tools (projects, runs, traces, RAG stores, costs) not covered by a dedicated melaya_* tool: `melaya_gateway_list` with `search`, then `melaya_gateway_call`. This is the platform toolkit, not third-party connectors.

## 4. Discover models

`melaya_model_list` with `provider` (enum, e.g. `qwen`, `openai`, `anthropic`, `google`, `mistral`, `deepseek`, `nvidia`, `openrouter`, `melaya_ai`, ...). Read `status` first:

| status | Meaning | Action |
|---|---|---|
| `ok` | live list, newest first | pick ids from it verbatim |
| `no_key` | account has no key for this provider | ask the user to connect it in Melaya Connectors; do not guess ids |
| `invalid_key` | stored key rejected | ask the user to replace it |
| `error` | transient | retry; `force_refresh: true` bypasses the 60-minute cache |

Runner-served providers (`claude_code`, `codex`, `github_copilot`, `ollama`, `lmstudio`) are not served by `melaya_model_list`: read `melaya_runner_status` (`models`, `claudeCodeModels`). An empty Claude Code list on a connected runner means the `claude` CLI is not signed in on that machine. Picking, loading and troubleshooting runner and local models: the `../../modules/runners-models/GUIDE.md` skill.

Selection guidance for the build:
- Validation runs: a cheap, fast hosted model (a qwen "plus" class model is a good default) so iteration is minutes, not hours.
- Record the exact provider + model id pair in the inventory; the authoring phase copies it into every agent.
- Avoid CPU-hosted local models for multi-step tool agents during validation: each turn can take minutes.

## 5. Asking the user to connect a missing service

Full procedure, ready-to-send messages for every kind of connection, failure table, expiry and reconnect, multiple accounts and project connectors: `references/connectors-playbook.md`. Per-family notes (Google, Microsoft, Slack, social, CRM/finance, data providers), paid-API cost notes and the "no connector exists" path: `references/service-families.md`.

Short version:

1. Confirm the exact service id (`melaya_connector_tools` search, or the card name on https://app.melaya.org/connectors). Do not guess: `melaya_connector_connect` answers an unknown id with the generic "enter the key on the Connectors page" text instead of an error.
2. Call `melaya_connector_connect` with `service`. Verified answer shapes:
   - Sign-in (OAuth) services (Google products, Facebook, ...): `{"authorizationUrl": "https://app.melaya.org/connectors?highlight=<id>"}`. The link opens the Melaya Connectors page on that card; the user clicks Authorize, then consents on the provider's own screen.
   - API-key services: a sentence telling the user to open https://app.melaya.org/connectors, pick the service and enter the key there (the page validates it before saving).
   - Session logins (LinkedIn, Luma, Telegram personal account, NotebookLM, Substack): the card has a Connect button or a wizard; LinkedIn and Luma open the login window on the runner machine, so the runner must be running.
3. Send one short message: what the service is for in their system, the link, what to click, that each Google product is a separate grant, that no key or password goes through the chat, and that you will verify.
4. Wait. Do not build around the gap and do not substitute a different service.
5. When the user says done: `melaya_connector_list` (service present) -> `melaya_connector_test` -> one read-only `melaya_connector_call` (section 6). Confirm with the user which account is connected.

Explaining MCP consent (say it plainly when the user asks why you can or cannot send the email or write the sheet from the chat):
- Reading connected services needs the connectors permission. Writing through them from the chat needs the extra "connectors.write" permission the user ticks when connecting the assistant to Melaya. Without it, write tools are refused in the MCP tool and again server-side, whatever the instructions.
- With it, a write from the chat runs immediately, with no approval card, and is recorded in the audit log. Confirm the exact send or change with the user before calling it.
- Anything that moves money or trades is refused from the chat at every level; the user approves it in the Melaya app.
- Writes the system must repeat (every run, on a schedule, on an event) belong inside a Melaya pipeline, where the user controls approval gates (HITL).
- If a capability is missing from your tool list entirely, the user did not grant that scope when connecting the MCP (connections made before a permission existed must re-authorise); offer to have them reconnect with it. Do not look for another route.

Template message (sign-in service):

```
To <purpose>, the system needs access to <Service>. Please open this link: <authorizationUrl>
On the <Service> card click "Authorize", choose the account Melaya should use, and click Allow
on <Provider>'s own screen (leave every permission ticked).
No key or password goes through this chat. Tell me when it is done and I will verify the
connection with a read-only check before we continue.
```

## 6. Verify access with a read-only call

After any connect, and before relying on a service in a design, prove it with one cheap read:

| Service | Tool (via `melaya_connector_call`) | Example `args` | Proves |
|---|---|---|---|
| google_drive | `drive_search` | `{"name": "<expected file name>", "limit": 5}` | grant works; also finds existing files with that name (Drive name search is a WORD match, so check for collisions with older files) |
| google_sheets | `sheets_read_range` | `{"spreadsheet_id": "<id>", "a1_range": "A1:Z2"}` | the sheet is readable; header row matches the schema |
| google_docs | `docs_read` | `{"document_id": "<id>"}` | a document is readable |
| gmail | `gmail_search` | `{"query": "newer_than:1d", "limit": 3}` | mailbox reachable |
| gmail | `gmail_my_address` | `{}` | the address the mailer step will send from |
| melaya_core | `fetch_rss` | `{"url": "https://www.bing.com/news/search?format=rss&q=<query>", "max_items": 3}` | keyless news feed works from the platform |
| melaya_core | `web_fetch` / `scrape_page` | `{"url": "<known page>"}` | a known URL is readable |

Rules:
- Always look up the exact parameters first with `melaya_connector_tools` `tool=<name>`; the table above is a starting point, not a contract.
- Never print ids, emails or document content you read into shared documentation; record only "verified <date>, tool X returned N rows".
- If the read fails: `melaya_connector_test` for the service; if that fails, reconnect (section 5); if it passes, fix the arguments.

## 7. Map every client requirement to tools

Build the table in `references/requirement-mapping-template.md` with the client (or from their brief). Procedure:

1. Split the brief into atomic requirements, one verb each ("find new companies in sector X", "check founders against sanctions", "produce an investment memo as a Google Doc", "email the owner a weekly digest").
2. For each, choose the data source class in this priority order (the tool reliability order used later in prompts):
   1. Registry / structured data tools (official registers, APIs with typed output).
   2. A KNOWN URL read with `scrape_page` (or `scrape_links` on the home page to find the right path; do not guess paths).
   3. Feeds: `rss_search_entries` / `rss_fetch_multiple` (targeted Bing News RSS URLs are reliable), GDELT.
   4. `web_search` only to discover URLs, then open the page.
3. Prefer KEYLESS tools (catalogue: `references/keyless-tool-catalogue.md`). They prove value on day one without any client data or procurement.
4. Search the registry for each candidate, copy the exact `id`, note `read_only`. Write tools (send, append, create, share) are marked for approval gating.
5. Mark each row's access: `keyless`, `free key` (a free self-serve key the user stores in Connectors), `connector` (OAuth or paid key), `run input` (files the user attaches per run).
6. Mark each row's status: `verified-registry`, `verified-live` (read call or validation run returned real data), `needs-connect`, `gap` (no tool; propose an alternative or a code change request).
7. Add the output side: which document or sheet each workflow produces (word_create / pptx_create / excel_write_data / pdf_from_html, published with drive_upload; data spine in Google Sheets; one mailer step via gmail_send).

## 8. Keyless catalogue (summary)

Full table with tool ids and what each proves: `references/keyless-tool-catalogue.md`. Domains:

| Domain | Lead tools (registry ids) |
|---|---|
| Company registries / KYB | `gleif_*`, `annuaire_*` (FR), `brreg_*` (NO), `acra_*` (SG), `vies_*` (EU VAT); free key: `companies_house_*` (UK) |
| Sanctions | `sanctions_screen`, `sanctions_screen_batch` (OFAC SDN, UN, EU, UK); `ofac_*`; `goplus_address_security` for wallets |
| Regulatory | `mica_check`, `mica_*` (ESMA MiCA register); `vara_register_check` (Dubai VARA), `adgm_fsra_check` (Abu Dhabi ADGM), `dfsa_register_check` (DIFC); free key: `fca_*` (UK FCA) |
| News / signals | `rss_*`, `fetch_rss`, `gdelt_*`, `hn_*`, `ground_*`, `web_search` |
| Filings / capital raises | `edgar_*` incl. `edgar_form_d_search` (US private placements) |
| On-chain / DeFi | `defillama_find_protocol` (slug from a name or domain) then `defillama_*`, `blockscout_*`, `goplus_*`, `sourcify_*`, `geckoterminal_*`, `snapshot_*`, `growthepie_*`, `hlinfo_*`, `safe_*` |
| Code / developer traction | `github_developer_scorecard`, `github_org_overview`, `github_repo_activity`, `npm_*`, `pypi_*`, `osv_*` |
| Web traction / domain | `webtraffic_traction_report`, `rdap_domain_age`, `crtsh_subdomains`, `wayback_*`, `dns_lookup` |
| Investors / directories | `openvc_search_investors`, `thesaasdir_*` |
| Documents / OCR | `deck_extract`, `pdf_to_text`, `pdf_extract_tables`, `ocr_image`, `read_run_input` (auto) |
| Deal DB and scoring | `dealdb_*`, `decide`, `decide_batch`, `decide_batch_file` (free); `jev_*` (paid hosted twin) |
| Output (connector) | `sheets_*` (incl. `sheets_update_by_header`, `sheets_delete_rows`), `docs_*`, `drive_*` (incl. `drive_create_folder`, `drive_move`), `gmail_send`; local files: `word_create`, `pptx_create`, `excel_write_data`, `pdf_from_html` |

## 9. Discovery checklist (definition of done)

- [ ] `melaya_setup_status` read; each open step marked "fixed", "not required for this system" or "waiting on user".
- [ ] `melaya_connector_list` captured; every service the design depends on passed `melaya_connector_test`.
- [ ] Every missing service requested with a `melaya_connector_connect` link; none worked around.
- [ ] For services with several accounts, the account each pipeline uses is confirmed (default account, or project connectors).
- [ ] Paid services flagged with their billing model; services with no connector have a chosen alternative or a connector request.
- [ ] One read-only `melaya_connector_call` succeeded for each connected dependency.
- [ ] Every tool in the mapping table has an exact id from `melaya_pipeline_registry` or `melaya_connector_tools`.
- [ ] Keyless-first: each requirement that public data can answer uses a keyless tool, with a reliability order noted.
- [ ] Model provider + model id copied from `melaya_model_list` (status `ok`) or `melaya_runner_status`.
- [ ] Write tools listed separately for HITL gating in the authoring phase; money-moving tools flagged as app-approval only.
- [ ] Gaps listed with a proposed resolution (connect, free key, run input, code change request).

Next: create or choose the project (projects-templates skill), then design and author (pipeline-authoring skill).
