# Service families, costs, and services without a connector

Companion to `connectors-playbook.md` (which has the step-by-step procedure and the messages). This file says, per family, what the user needs to know before connecting, which traps to warn about, what costs money, and what to do when no connector exists.

Always confirm service ids and tool names live (`melaya_connector_tools` search, `melaya_pipeline_registry` search). The catalogue grows; the notes below describe behaviour that was verified at the time of writing.

## 1. Google Workspace

| Product | Service id | Kind | What it allows | Notes for the user |
|---|---|---|---|---|
| Gmail | `gmail` | sign-in | search and read mail, send mail | The pipeline sends from the default Gmail account on the card; confirm with `gmail_my_address` |
| Calendar | `google_calendar` | sign-in | list and manage events, find free slots | |
| Drive | `google_drive` | sign-in | find, create, upload, export, share files | Only sees files Melaya created or files opened through Melaya, not the whole Drive. "It cannot find my folder" is expected behaviour, not a bug |
| Sheets | `google_sheets` | sign-in | read and edit spreadsheets you point it to | Give the sheet's link (the id is inside it). Data is often not on the first tab: check `sheets_list_tabs` |
| Docs | `google_docs` | sign-in | read and edit documents you point it to | Give the document's link |
| Meet | `google_meet` | sign-in | read meeting spaces | read-only |
| Search Console, Analytics, YouTube, Google Ads | `google_search_console`, `google_analytics`, `youtube`, `google_ads` | sign-in (Google Ads also needs a developer token field on its card) | product specific | YouTube is always a separate authorization |

Key facts to say out loud:
- Each product is a separate connection and a separate Allow. Connecting Drive does not give Sheets or Docs. For a system that stores data in a Sheet and publishes documents to Drive and emails a summary, the user connects `google_sheets`, `google_drive` and `gmail`: three short sign-ins.
- A work Google account may be managed by an administrator who blocks third-party apps. If Google shows "access blocked" or "admin approval required", the user must ask their Google Workspace administrator to allow Melaya, or use another account.
- If a permission box is unticked on Google's screen, nothing is saved and the card names the missing permission. Redo it with all boxes ticked.
- Several Google accounts: each product card keeps its own default (see playbook section 6).

## 2. Microsoft

- At the time of writing there is no Microsoft 365 connector (Outlook mail, Outlook calendar, Teams, OneDrive, SharePoint): searches for "outlook" and "onedrive" return nothing. Re-check before telling the user.
- `microsoft_ads` (Microsoft Advertising) exists: sign in with Microsoft and add the Advertising developer token on the card.

Options for a Microsoft-based client, best first:
1. Produce Office files without any Microsoft connection: `word_create`, `excel_write_data`, `pptx_create` create real .docx/.xlsx/.pptx files in the run; attach them to an email or publish them with another connector.
2. Send mail from a Microsoft mailbox through the generic `smtp` connector (card fields: host, port, user, password or app password) with `smtp_send`, if the organisation's administrator allows SMTP sending. Many Microsoft 365 tenants disable it; ask the IT contact first.
3. Read or act in Outlook on the web, Teams or SharePoint through Browser Control, on sites the user allow-lists (section 8).
4. Request a Microsoft 365 connector from Melaya (section 8).

Do not use `imap_fetch` for this: it takes the mailbox password as a tool argument, which would place a password inside the pipeline configuration.

## 3. Slack

- Kind: key paste. The card asks for a Bot Token (starts with `xoxb-`), a default channel, and an optional app-level token (only needed for instant event triggers).
- What the user does: create a Slack app in their workspace (api.slack.com, "Create New App"), give it the bot permissions needed (reading channels and history, posting messages), install it to the workspace, copy the Bot User OAuth Token, paste it on the Melaya Slack card, Save. Then invite the bot to each channel it must read or post in (`/invite @<bot name>` in the channel).
- Slack tools appear under the `melaya_core` family in the catalogue, but they still need the Slack card filled. A test on an unconfigured Slack answers "Bot token not set".
- Verify with `slack_list_channels`. Posting (`slack_post_text`, `slack_post_blocks`) is a write: pipeline only, behind approval.
- On many company workspaces only admins can install apps; if so, the user asks their Slack admin.

## 4. Social platforms

| Platform | Service id | Kind | Notes |
|---|---|---|---|
| Facebook Page | `facebook` | sign-in | Choose the Page during sign-in |
| Instagram | `instagram` | key paste: access token + Instagram Business Account ID | Needs an Instagram Business or Creator account linked to a Facebook Page |
| X | `x` | sign-in, or bring your own X developer app (client id and secret on the card) | The Melaya-hosted X sign-in is not offered on every plan; the own-app route works on any plan |
| LinkedIn | `linkedin` | session: login window on the runner machine | Runner must be running. `linkedin_session_status` reports the session and today's usage counters. Platforms limit automated activity: keep volumes modest |
| TikTok, Reddit, YouTube, Canva | `tiktok`, `reddit`, `youtube`, `canva` | sign-in | |
| Telegram (personal account) | `telegram_user` | session wizard: app api_id and api_hash from my.telegram.org, then phone number, SMS code, optional 2FA password, all typed into the Melaya wizard | Never in chat |
| Mastodon | `mastodon` | key paste: instance URL + access token | |
| Substack | `substack` | session: email and password typed on the Melaya card | Uses an unofficial interface that can change without notice |
| Luma | `luma` | session: login window on the runner machine | `luma_session_status` checks the session |

For every social platform: posting, replying, liking, messaging and deleting are write tools. They run inside pipelines, where templates gate them behind approval, or from the assistant only with the `melaya:connectors.write` permission and the user's explicit go-ahead. Tell the user nothing is ever published without their approval unless they deliberately change the approval rules.

## 5. CRM, ERP, finance and productivity

| Service | Service id | Kind | What the user creates |
|---|---|---|---|
| HubSpot | `hubspot` | key paste | A private app (Settings > Private apps) with the CRM scopes needed, for example contacts and deals read (and write only if the system writes). Paste its access token |
| Salesforce | `salesforce` | key paste: instance URL + connected-app credentials | Usually done by the Salesforce admin; the card lists each field |
| Pipedrive, Attio | `pipedrive`, `attio` | sign-in tab, or personal API token / workspace key tab | Use the sign-in tab |
| Odoo | `odoo` | key paste (card fields) | An API key from the Odoo user preferences |
| Stripe | `stripe` | key paste: secret key | For a system that only reads, create a restricted key with read permissions (starts with `rk_`) instead of the full secret key; test keys (`sk_test_`) for trials |
| Airtable | `airtable` | key paste: personal access token | Grant the token only the scopes and bases the system needs |
| Notion | `notion` | sign-in tab or integration token | During sign-in Notion asks which pages to share: pick the pages the system needs. Instant triggers work only with the sign-in tab |
| Zoho Mail | `zoho_mail` | per the card | |
| GitHub | `github` | sign-in tab or personal token | Connecting also lifts the low anonymous rate limit of the GitHub scorecard tools |

General advice: the least privilege that works. A read-only system gets read-only keys. A key with write access can write from this chat only if the user also granted the assistant `melaya:connectors.write`; money-moving actions never run from the chat; inside pipelines, writes follow the approval rules.

## 6. Data providers

- Keyless first: registers, sanctions lists, news feeds, SEC filings, on-chain data (see `keyless-tool-catalogue.md`). No connection at all.
- Free keys (a free self-serve sign-up at the provider, pasted on the Melaya card): `companies_house` (UK companies), `fca` (UK FCA register: registered email + key), `uspto` (patents; the provider verifies identity), `fred` (US macro), `safe` (multisig data beyond exploration limits). Tell the user it is free and takes a few minutes.
- "keyless*" tools work without a key at a low public rate: connecting the provider (GitHub, for example) raises the limit.

## 7. Paid APIs: cost notes

Who pays what:
- A paid data or model service connected with the user's own key bills the user's account at that provider, at the provider's prices. Melaya does not pass that bill through; the user pays the provider directly.
- Melaya's own plan limits are separate (runs, pipelines, assistant messages, MCP calls). `melaya_account_usage` shows them. Every call this assistant makes to Melaya, read or write, counts against the MCP allowance.

| Service | Billing model | Guidance |
|---|---|---|
| Model providers (OpenAI, Anthropic, Gemini, Qwen, Mistral, ...) | per token, on the user's key | Use a cheap fast model for validation runs; several providers have free tiers that pause at the cap instead of failing for good |
| DataForSEO | pay-as-you-go credits, charged per call | Use small limits while testing; the Melaya tools use the small-depth endpoints to keep cost predictable |
| Jev (`jev_*`) | per token, hosted | The free `decide_*` tools do the same job with the same question format; use Jev only when hosted speed is needed |
| Tavily | free monthly credits, then paid | `web_search` plus `scrape_page` often suffice |
| Hunter and other enrichment APIs | plan credits at the provider | Batch lookups; enrich only shortlisted records |
| Image, video or speech generation services | per generation | Generate once per approved item, never in a retry loop |

Before connecting a paid service, tell the user in one line: "<Service> charges your <Service> account per <call/token/credit>; I will keep test runs small. You can set a spending limit on your <Service> account." Then record the cost class on the requirement-mapping row, so the handover lists paid tools separately. In pipeline instructions, cap calls ("at most N lookups") for every paid tool.

## 8. When a service has no connector

Work down this list and tell the user which option you recommend and why.

1. A keyless or built-in tool already answers it. Search `melaya_pipeline_registry` with a single capability word. Public web pages: `scrape_page`, `scrape_links`, `fetch_rss` (all keyless). This is often enough for public data.
2. A different connected service already holds the data (for example the CRM exports weekly to a Google Sheet the pipeline can read).
3. Run inputs: the user attaches an export (CSV, Excel, PDF) to each run. Good for monthly or ad-hoc work; no connection needed.
4. Browser Control: the agent operates the user's own browser, logged in as the user, on sites the user allow-lists. Start with `melaya_browser_pair` (install the Melaya extension in Chrome, Edge or Brave, then sign in or enter the pairing code), then the user allow-lists the site on the Melaya browser page. Pairing alone grants nothing. Slower and more fragile than an API, but works for services with no API. Writes still need approval.
5. Phone control, for mobile-only apps (Android): `melaya_phone_pair`, then the user allow-lists the app. Full phone and browser procedures (pairing, allow-lists, the read-act-verify loop): the `../../../modules/devices-browser/GUIDE.md` skill.
6. Generic protocols: `smtp` for sending mail from any provider that allows SMTP.
7. Ask Melaya to add a connector: on https://app.melaya.org/connectors the user clicks "Add your app" and describes the service, its website or API documentation link, and what the automation must read or do. Advise leaving the optional test-key field empty unless Melaya asks for it later. Record the row as `gap` with "connector requested <date>" and design around it with options 1 to 5 in the meantime.

Never promise a connector date, and never replace a missing connection with a workaround the user did not choose.
