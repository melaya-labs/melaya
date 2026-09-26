# Connectors playbook: getting every service connected

Use this when a Melaya system needs a third-party service (email, files, calendar, CRM, social, a data provider) and the user may not be technical. It covers what kind of connection each service uses, exactly what to tell the user, what they click, how you confirm it worked, and what to do when it breaks. Per-family details (Google, Microsoft, Slack, social, CRM/finance, data providers, paid APIs) and the "no connector exists" path are in `service-families.md`.

## 0. Words to use with the user (define once, then use)

| Word | Say it like this |
|---|---|
| Connector | "the saved link between your Melaya account and one of your services, for example your Gmail" |
| Sign-in (OAuth) | "you log in on the service's own page and click Allow; Melaya never sees your password" |
| API key / token | "a long code the service gives you so a program can use your account; it is like a password for software" |
| Scope / permission | "the list of things you allow, for example 'read email' but not 'delete email'" |
| Runner | "a small Melaya program running on your own computer, used for things that must happen on your machine" |
| Pipeline | "the automated workflow we are building; it runs in Melaya with your approval rules" |
| Approval (HITL) | "before an agent sends, posts or changes anything, Melaya asks you to click Approve" |

## 1. The four kinds of connection

| Kind | What the user does | Typical services | Needs the runner |
|---|---|---|---|
| A. Platform sign-in (OAuth) | Opens the service card in Melaya Connectors, clicks Authorize, logs in on the provider's page, clicks Allow | Gmail, Google Calendar, Drive, Sheets, Docs, Meet, Search Console, YouTube, Google Ads, Facebook Page, and the sign-in tab of dual connectors (Notion, GitHub, X, TikTok, Reddit, Canva, Pipedrive, Attio, Klaviyo, Mailchimp, Webflow, Microsoft Advertising, Amazon Ads) | No |
| B. API key paste (in the app, never in chat) | Creates a key on the provider's website, pastes it into the Melaya Connectors card, clicks Save; the page checks it before saving | Model providers (OpenAI, Anthropic, Gemini, Qwen, ...), Slack bot token, HubSpot private app token, Stripe, Airtable, Odoo, Salesforce, Instagram token, Jev, Tavily, DataForSEO, free register keys (Companies House, FCA, USPTO, FRED), and the "manual key" tab of dual connectors | No |
| C. Cookie or session login | Clicks Connect; a real login window opens (on the user's own computer for some services) and the user logs in normally; Melaya keeps the resulting session | LinkedIn, Luma (login window on the runner machine), Telegram personal account (phone number, then SMS code, then 2FA password if set, typed into the Melaya wizard), NotebookLM, Substack | LinkedIn and Luma: yes, the login window opens on the runner machine |
| D. Local only | Starts the runner on their computer; nothing is stored in the cloud | Runner models (Claude Code, Codex, GitHub Copilot, Ollama, LM Studio), local folders and files, databases only reachable from the user's network ("Test via runner") | Yes |

Dual connectors (sign-in tab AND manual key tab): if both are filled, the manual key wins. Tell the user to use only one, normally the sign-in tab.

Keyless services (public registers, news feeds, sanctions lists, on-chain data, `melaya_core` web tools) need nothing at all. Prefer them whenever they answer the requirement (see `keyless-tool-catalogue.md`).

## 2. Hard rules for you

1. Never ask for, accept or repeat a key, token, password, SMS code or 2FA code in the chat. If the user pastes one anyway: do not use it, tell them it is now in the chat history, ask them to revoke it at the provider and create a new one, and to enter the new one only in Melaya Connectors.
2. Never work around a missing connection (no scraping their logged-in page, no "just export a CSV for now" unless the user chooses that). Ask, wait, verify.
3. Connect only what the system needs, one service at a time, and say why each is needed.
4. You verify with reads. A write from the conversation needs the user's `melaya:connectors.write` permission, runs at once with no approval card, and is audit-logged: confirm it with the user first, and put anything recurring inside a pipeline, behind approvals. Money-moving and trading tools are refused from the conversation at every level.
5. Do not copy account emails, file ids or data you read during verification into shared documents. Record "verified <date>, tool X returned N rows".

## 3. Procedure: connect one service

### Step 1. Check what is already there

1. `melaya_connector_list`. `services` lists every service id with stored credentials. It also contains model providers and a few internal profile entries; ignore those for connector work. `toolCounts` shows read/write tool counts per service; `melaya_core` is the always-on keyless family.
2. If the service is listed, go straight to Step 5 (test). A listed service can still be expired.

### Step 2. Find the exact service id

- Guessing is dangerous: `melaya_connector_connect` answers an unknown id with the generic "enter the key on the Connectors page" message instead of an error, so a typo sends the user looking for a card that does not exist.
- Find the id: `melaya_connector_tools` with `search` = the service name ("hubspot", "calendar"). Each result shows `[service id]`. For a service not yet connected, its tools may not appear; then use the id as written on the card in https://app.melaya.org/connectors (lowercase, underscores, for example `google_sheets`, `zoho_mail`, `telegram_user`).
- Each Google product is its own id and its own grant: `gmail`, `google_calendar`, `google_drive`, `google_sheets`, `google_docs`, `google_meet`, `google_search_console`, `google_analytics`, `youtube`, `google_ads`.

### Step 3. Get the link

Call `melaya_connector_connect` with `service` (and `reason`). Two answer shapes (verified live):

| Answer | Meaning | What you give the user |
|---|---|---|
| `{"service": "...", "authorizationUrl": "https://app.melaya.org/connectors?highlight=<id>"}` | Sign-in service. The link opens the Melaya Connectors page with that card highlighted; the user clicks Authorize there and then consents on the provider's page | the link + the sign-in message below |
| A sentence saying the service authenticates with an API key and the user should open https://app.melaya.org/connectors, pick the service and enter the key there | Key service (also returned for unknown ids, see Step 2) | the link + the key message below |

For cookie/session services (kind C) the tool usually returns the key-style answer; the card itself shows a Connect button or a wizard. Use the session message below.

### Step 4. Ask the user (ready-to-send messages)

Sign-in (kind A):

```
To <purpose, e.g. "save each deal in your shared Google Sheet">, Melaya needs permission to use
your <Service>. It takes about a minute:

1. Open this link: <authorizationUrl>
2. On the <Service> card, click "Authorize" (for Google: "Authorize with Google").
3. A <Provider> window opens. Choose the account you want Melaya to use, then click Allow.
   Leave every requested permission ticked; if one is unticked, the connection is refused
   and the card tells you which permission is missing.
4. The card turns connected. Come back here and say "done".

You never type your password into Melaya or into this chat. I will then run a read-only check.
```

API key (kind B):

```
To <purpose>, Melaya needs an API key for <Service> (a code that lets software use your account).

1. Create the key on <Service>: <where, e.g. "Settings > Private apps > Create" - see the
   service notes; if unsure, use the help link on the Melaya card>.
   <If the service offers read-only or restricted keys and the system only reads, say:
   "choose read-only access, that is all we need".>
2. Open https://app.melaya.org/connectors and pick <Service>.
3. Paste the key into the card's field(s) and click Save. The page checks the key before saving.
4. Come back and say "done".

Please do NOT paste the key in this chat: anything typed here stays in the conversation history.
```

Cookie or session (kind C):

```
<Service> does not offer a normal sign-in for apps, so Melaya uses a login session instead.

1. <LinkedIn / Luma only:> Make sure the Melaya runner is running on this computer
   (I can help set it up).
2. Open https://app.melaya.org/connectors, pick <Service> and click Connect.
3. <A login window opens on your computer / follow the steps shown: phone number, then the
   code you receive, then your two-step password if you have one.> Log in normally.
4. Say "done" when the card shows connected.

Sessions expire from time to time; if the automation later says the session is missing,
we simply repeat this step.
```

Local only (kind D): use the runner steps from SKILL.md section 1 (`melaya_runner_setup`, then `melaya_runner_status`).

### Step 5. Confirm it worked (in this order)

1. `melaya_connector_list`: the id is now in `services`.
2. `melaya_connector_test` with `service`: calls the provider with the stored credential. Success looks like `{"success": true, "message": "Google Sheets connected as <account>. ..."}`. The message can include the account email; use it only to confirm the right account with the user, do not write it into documents. Failure looks like `{"success": false, "message": "Bot token not set"}`.
3. One cheap read with `melaya_connector_call` (look up exact parameters first with `melaya_connector_tools` `tool=<name>`):

| Service | Read-only check | `args` |
|---|---|---|
| gmail | `gmail_my_address` | `{}` |
| gmail | `gmail_search` | `{"query": "newer_than:1d", "limit": 3}` |
| google_calendar | `gcal_list_events` | `{"range_days": 7, "max_results": 5}` |
| google_drive | `drive_search` | `{"name": "<file name>", "limit": 5}` |
| google_sheets | `sheets_list_tabs` then `sheets_read_range` | `{"spreadsheet_id": "<id from the sheet link>"}`, then `{"spreadsheet_id": "...", "a1_range": "A1:Z2"}` |
| google_docs | `docs_read` | `{"document_id": "<id from the doc link>"}` |
| slack | `slack_list_channels` | `{}` |
| linkedin | `linkedin_session_status` | `{}` |
| luma | `luma_session_status` | `{}` |
| any other | `melaya_connector_tools` `search=<service name>`; pick a `[read-only]` list/get/me/status tool with no or few required parameters | per the parameter list |

A Google Sheet or Doc id is the long code in its link (`https://docs.google.com/spreadsheets/d/<id>/edit`). Asking the user for the link is fine; a file link is not a secret. Do not copy it into shared documentation.

4. Tell the user in one line: "<Service> is connected as <account they confirmed>; Melaya could read <what>." Then continue the build.

### Step 6. When confirmation fails

| Symptom | Likely cause | What to do |
|---|---|---|
| id not in `services` after "done" | Not saved, wrong card, or the popup was closed early | Ask them to reopen the card; check it shows connected; for sign-in, check the browser did not block the popup |
| test `success: false` on a key service | Wrong key, a key for another environment (test vs live), missing field (a second field such as instance URL or account id) | Ask them to re-enter the key on the card; mention which field the message names |
| test passes, read call fails with a permission error | Key or app created without the needed scope (for example a HubSpot private app without the contacts scope) | Ask them to add the scope at the provider, regenerate the key if the provider requires it, save it again |
| Google card shows a missing-permission message | A permission box was unticked on Google's screen | Repeat the sign-in and leave all boxes ticked |
| Read works but "file not found" on Drive | Drive access only covers files Melaya created or files opened by id (see `service-families.md`) | Use the Sheets/Docs id from the link, or let the pipeline create the file |
| "runner_offline" or nothing happens on LinkedIn/Luma Connect | The login window opens on the runner machine | Start the runner (`melaya_runner_setup`), then click Connect again |
| Everything fails with setup-shaped errors | Account or runner problem | `melaya_setup_status` and fix the named step |

## 4. Scopes and read-only consent, in plain words

There are two separate permission layers. Explain them when the user asks "why can't you just do it from here?":

1. Service permissions (scopes): what Melaya may do in, say, Gmail. Granted on the provider's screen when they connect. Melaya asks for one product at a time, with the narrowest permission that works:
   - Gmail: read and search mail, send mail.
   - Calendar: manage events and see free/busy times.
   - Drive: only files Melaya creates or that are opened through Melaya, not the whole Drive.
   - Sheets / Docs: read and edit the spreadsheets or documents you point it to.
   - Meet: read meeting spaces only.
2. This assistant connection (MCP consent): what the AI assistant in this chat may do in Melaya. It was chosen, box by box, when the user connected the assistant to Melaya. The "connectors" permission lets it list connected services, test them and run read tools. The separate "connectors.write" permission adds write tools (send an email, create or update a record or a file); each such write runs at once and is audit-logged, so the assistant confirms it with the user first. Tools that move money or trade (payments, refunds, purchases, transfers, ad budget or bid changes, orders) are refused from the chat whatever is granted; the user approves them in the Melaya app. If a whole capability is missing from your tools, the user left that box unticked (or connected before that permission existed); offer to have them reconnect the assistant to Melaya with it ticked. Never look for another route.

Ready-to-send explanation:

```
Good question. From this chat I can READ your connected services to check things and look up
data. I can only send or change something there if you gave this assistant the extra
"connectors.write" permission, and even then I ask you first and every write is logged.
Anything that moves money is never done from here: it waits for your approval in Melaya.
Work that repeats is done by your Melaya pipeline, where every outside action waits for your
"Approve" click unless you decide otherwise.
```

Also useful: every call this assistant makes to Melaya counts against the plan's MCP allowance, reads included. `melaya_account_usage` shows usage against plan limits; check it before long verification sessions.

## 5. Expired, revoked or broken connections

Signs: a run fails with an auth error, `melaya_run_diagnosis` shows an `auth` finding, a tool says access expired, `melaya_connector_test` fails for a service that used to pass, a session tool reports no session, or an event trigger bound to the service is paused.

Common causes, in plain words:
- The user changed their password, removed Melaya's access in the provider's security settings, or an admin revoked it.
- The key was deleted or rotated at the provider.
- A cookie or session login expired (LinkedIn, Luma, Substack, NotebookLM expire more often than sign-in connections).
- Google: a sign-in token lasts about an hour and Melaya refreshes it at the start of each run. If Google still refuses mid-run the tool says access expired; re-running the task is usually enough. If it keeps failing, reconnect.

Reconnect procedure:
1. `melaya_connector_test` to confirm the service really fails (not a wrong argument).
2. `melaya_connector_connect` for the same id; send the same message as the first connection, starting with: "Your <Service> connection has expired (this happens when <likely cause>). Please reconnect it:".
3. Sign-in services: authorize again with the same account. Key services: create a new key, paste it on the card, Save (the new key replaces the old one). Session services: click Connect again.
4. Confirm with Step 5 (list, test, one read).
5. If an event trigger used this service, it may have been paused when the connection was removed: ask the user to open the trigger in the Agent Builder and press Resync / re-enable it (trigger setup is covered by the automation-governance skill).
6. Re-run the failed pipeline.

Disconnecting: the user removes a connection from its card in Melaya Connectors ("Disconnect"). That removes it from every pipeline and the assistant, and pauses triggers that depend on it. Removing a key from Melaya does not revoke it at the provider; for a key they no longer want, tell them to also delete it on the provider's site.

## 6. Multiple accounts for one service

Questions to settle with the user: "Which account should the automation use: your personal one or the team one? Should replies go out from the same account?"

How Melaya decides which account a pipeline uses:

| Situation | What decides the account | What the user does |
|---|---|---|
| Google products, personal connection | Each Google product card has its own list of authorized Google accounts and its own default account. A pipeline uses the product's default account | Authorize the extra account on that product's card, then choose it as the default for that product. Gmail can default to one account while Sheets defaults to another |
| Most other services, personal connection | One stored credential per service | To switch accounts, reconnect the card with the other account (it replaces the first) |
| A team or client project | Project connectors: shared credentials managed by the project owner in the project's Connectors page (https://app.melaya.org/projects/<project name>/connectors). A pipeline uses them when its credential source is set to project (Agent Builder, Config tab, Credentials toggle; config field `connector_source: "project"`) | The project owner connects the team account once; members' pipelines use it. Viewers cannot see or use the secrets |
| The assistant or Browser Control | They keep their own per-product selection | Pick the account in that surface's selector |

Rules of thumb:
- A newly authorized Google account becomes the default for the products it was just granted; say this to the user so an extra login does not silently switch the pipeline's sending address.
- After any change, verify the account in use: `gmail_my_address` for mail, `melaya_connector_test` (its message names the connected account) for others.
- For a client system, prefer project connectors with a client-owned account, so the system keeps working if the builder leaves the project.

## 7. Checklist per service

- [ ] Service id confirmed (not guessed).
- [ ] Kind identified (sign-in, key, session, local) and the matching message sent.
- [ ] User said done; `melaya_connector_list` shows it; `melaya_connector_test` passed.
- [ ] One read-only `melaya_connector_call` returned real data.
- [ ] The account in use confirmed with the user (and the default account set if several).
- [ ] Personal vs project credentials decided for the pipelines.
- [ ] Cost class noted if the service is paid (see `service-families.md`).
- [ ] Nothing secret or personal copied into notes or documents.
