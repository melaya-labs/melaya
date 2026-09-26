# Trigger sources: how each kind of event gets in

Pick the source in this order. The first one that fits wins. For what the user sees and clicks in the app, follow `triggers-ui-walkthrough.md` step by step.

| # | Situation | Source | Who sets it up |
|---|---|---|---|
| 1 | the app is a connected Melaya connector with an instant (push) option | **push** (instant) | the user, in the app (MCP cannot) |
| 2 | the provider only streams over a socket (Discord, Slack Socket Mode, market or SSE feeds) | **push** gateway (Discord, Slack) or **stream source** (`wss`) | push: the user in the app; stream: MCP `source_create` + `create` |
| 3 | Melaya's own exchange events (fills, orders, liquidations) | **engine** | MCP `create` |
| 4 | any product that can POST signed JSON | **webhook** | MCP `create`; the user pastes URL + secret into the product |
| 5 | the provider has no push at all, the push option is not ready, or the user lacks the admin role push needs | **poll** | MCP `create` |

Latency: push, webhook, stream and engine are near real time. Poll is as slow as the plan's poll floor (15 min on the smallest plans, 1 min on the largest).

The app offers a preset gallery (Schedule & Triggers tab): instant presets first, then poll and webhook presets. Every preset's default action is `notify` ("Just tell me"). Some webhook presets carry one yes/no System One question; instant presets start without one, with only their first event ticked. Presets never write or start a run by themselves until the user picks an action.

## 1. Webhook (kind `webhook`)

One trigger = one URL. The sender signs each POST.

| Scheme | Sender sends | Replay protection |
|---|---|---|
| `melaya` (default) | header `X-Melaya-Signature: t=<unix seconds>,v1=<hex HMAC-SHA256(secret, t + "." + raw body)>` | 5-minute timestamp window; the same signed request twice is a duplicate, two identical bodies with different timestamps are two events |
| `stripe` | Stripe's own `Stripe-Signature` | 5-minute window; event id = Stripe's `id` |
| `github` | `X-Hub-Signature-256` | no timestamp; a GitHub "Redeliver" of the same body is a duplicate; receipts kept 30 days |
| `slack` | `X-Slack-Signature` + timestamp | 5-minute window; Slack's `url_verification` challenge is answered automatically |

Steps (melaya scheme):
1. `create` (kind `webhook`). Note the `webhookUrl` from the response.
2. The secret is never returned over MCP, and the app shows a secret only once, in the session that created or rotated it. So tell the user to open the trigger in the app, Setup tab, press "Rotate secret" then "Rotate now", copy the secret shown once into the sender and click "I saved it".
3. The sending product (or the user's developer) signs each POST as in the table. Any content type is accepted; the signature covers the raw bytes.
4. `test`, then ask the user to send one real event, then `deliveries`.

Steps (stripe, github, slack):
1. `create` with that `signing_scheme` to get the URL.
2. The user registers the URL in the provider's dashboard; the provider shows its own signing secret.
3. The user pastes it on the trigger in the app (Setup tab: the "Paste the signing secret" box, or "Rotate secret" with the provider scheme). Preferred: the secret never passes through the chat. `rotate_secret {id, secret: "<provider secret>"}` works too if the user insists on giving it to you.
4. Test and verify.

Answers the sender sees: `202` accepted (on the queue, not yet acted), `200` duplicate, `401` bad signature, `404` unknown or paused trigger, `413` too large, `429` rate limited (with `Retry-After`), `503` temporary (retry). Stripe retries for a long time; GitHub does not retry automatically on 429/503 (the user presses Redeliver).

Limits: 256 KB raw body; 32 KB carried payload (else `413` unless `accept_truncated`).

When a connected app has a ready push option (GitHub, Stripe, Slack, ...), prefer push: Melaya then creates, verifies, renews and repairs the provider side itself.

## 2. Push, instant (kind `push`, app only)

Melaya creates the subscription on the user's connected account (a webhook, watch channel or Pub/Sub watch) after the user ticks a consent box, renews it, repairs it daily and catches missed notifications with safety polls. Because it writes to the user's account, MCP can never create, re-point or re-enable a push trigger (`[push_ui_only]`). MCP may list it, read it, pause it, test it, read its deliveries and delete it.

What to tell the user (step by step; exact labels in `triggers-ui-walkthrough.md` section 2.1):
1. Make sure the app is connected in Melaya (Connectors) with the account to watch. There is no account picker in the trigger: it always uses the account connected on the Connectors page. If not connected, `melaya_connector_connect` gives the link.
2. Open the pipeline in the Agent Builder, tab **Schedule & Triggers**, **Add trigger**, and pick the instant preset for the app (badge "Instant" or "Live connection").
3. Fill "What to watch" (repository, calendar, base, channel...), tick the events (1-32; only the FIRST event is ticked by default, so check the list), tick the consent box and press **Create trigger**.
4. The panel has no action picker: the new trigger starts as "Just tell me" (it only records events) and without a System One question. Open its card, tab **Filter & actions**, click "Start this pipeline" (or another action), add the prefilter under "Advanced", and **Save**.
5. The card shows a status chip. "Active" = working. Then tell me and I will test it and read its deliveries.

Status chips and what to do:

| Status | Meaning | Fix |
|---|---|---|
| `active` | receiving events | none |
| `pending_challenge` | a manual adapter waits for the user to paste the URL or secret at the provider | follow the hint on the connector field, then **Resync** |
| `needs_reauth` | the connector credential expired or was revoked | reconnect the connector; it repairs itself |
| `needs_attention` | the provider removed the hook, the account changed, or the option is not ready | **Resync**; if it returns, check the provider side |
| `failed` | renewals failed until expiry | read the last error, fix, **Resync** |
| paused `push_subscribe_failed: <code>` | creation failed: `push_not_ready` (not available on this deployment yet), `push_scope_missing` (connector scope or plan), `push_admin_required` (needs provider admin), `push_reauth` (reconnect), `push_limit` (provider cap) | fix the cause, then **Resync** |

While a subscription is parked (`needs_reauth`, `needs_attention`, `failed`) its triggers are paused `push_parked: <status>`; they resume on their own when it recovers. A trigger the user paused by hand stays paused. Disconnecting the connector stops the subscription and pauses its triggers.

Several triggers of one user on the same resource share one provider subscription (providers cap them: Stripe 16 endpoints, GitHub 20 hooks). Each push trigger counts toward the plan's trigger count.

Push-capable apps (a push option exists; some need a provider admin role or a pasted secret): Affonso, Airtable, Alibaba EventBridge (manual), Attio, AWS SNS, Azure Event Grid, Calendly (paid Calendly plan), ClickUp, Cloudflare (Pro zone for webhook destinations), Discord (live connection; enable the Message Content intent), GCP Pub/Sub, Gitea, GitHub (repo admin; org hooks need a personal access token), GitLab (Maintainer), Gmail (Inbox of the connected account; events carry a short body preview, not the full body), Google Calendar, Google Drive (only files Melaya created or opened), Jira (Jira admin), Klaviyo (Advanced KDP or an approved app), Linear (workspace admin), Loops (manual), Mailchimp, Mailgun, Marsel, Meta (Facebook Pages, WhatsApp, Meta Ads), monday, Notion (OAuth-connected users), Odoo (admin), OpenAI (manual), Pipedrive (admin or webhooks permission), Resend, SendGrid, Shopify, Slack (live connection via Socket Mode: app token and Socket Mode on), static-header products (Databricks, Finnhub, PostHog, ServiceNow, Snowflake; manual), Stripe, Telegram (will not replace another webhook unless the user chooses takeover), Trello, Twilio, Webflow, YouTube (channel uploads; comments have no push), Zendesk (admin), Zoom (manual). A few of these may show "setup pending" on a given deployment: then the poll or webhook preset stays the default. Manual options show the URL; the user configures the provider by hand and pastes the provider's secret into the connector field, then presses Resync.

No push available (use poll or a manual webhook): Reddit, Medium, Substack, Search Console, Analytics, most ad platforms, Zoho Mail (poll by default), HubSpot, LinkedIn, X, Instagram, Threads, Outlook and other Microsoft 365 apps, keyless public-data sources (registries, news, job boards, on-chain data). Startup or lead submissions arriving by email: use the Gmail instant preset on the intake pipeline, with a fixed subject format and a `startswith` prefilter (worked example: `triggers-ui-walkthrough.md` section 7.1).

## 3. Stream source (kind `wss`)

Melaya holds a live connection to a feed: `wss://` (WebSocket) or `https://` (Server-Sent Events, resumed after a drop). Create the source, then a trigger bound to it.

```json
melaya_pipeline_trigger { "action": "source_create", "name": "Price feed", "url": "wss://stream.example.com/ws",
  "auth_header_name": "X-Api-Key", "auth_value": "<feed key>",
  "subscribe_frame": { "op": "subscribe", "channel": "trades" }, "event_id_path": "data.id" }
melaya_pipeline_trigger { "action": "create", "kind": "wss", "name": "Big trades", "project": "<Project>",
  "pipeline": "<canonical_name>", "source_id": "<from sources_list>",
  "config": { "prefilter": "payload.data.size >= 100000", "action": { "type": "notify" } } }
```

Rules:
- Public hosts on port 443 only, TLS verified, no redirects, no credentials in the URL. Private, local and internal addresses are refused (`blocked`).
- Auth header name must be one of `Authorization`, `X-Api-Key`, `X-Auth-Token`, `Api-Key`, or a vendor header like `X-<Vendor>-Key` / `-Token` / `-Signature` / `-Timestamp` / `-Passphrase`. The value is stored encrypted and never returned. Prefer that the user enters it in the app.
- "Sign in with" a connector (Kalshi, Polymarket, Coinglass, Mastodon) is set in the app: the credential stays in the connector and only goes to that provider. These are WebSocket only.
- Fast feeds: above 50 frames per second only the newest frame of each 100 ms slot is kept. Without `event_id_path`, every frame is a new event (no dedupe).
- Frames over 256 KB are dropped; payloads over 32 KB need `accept_truncated`.
- Status (`sources_list`): `idle`, `connecting`, `connected`, `error` (after 10 failed connects, or a connector credential problem), `blocked` (refused address or header; fixed only by changing the source). Short error codes: `dns_failed`, `http_<status>`, `not_event_stream`, `tls_error`, `handshake_timeout`, `connection_refused`, `connection_cap_reached` (plan socket cap), ...
- Stream sources need a plan with stream sources (plan-limits.md).

## 4. Exchange engine (kind `engine`)

`config.engine: {event, exchange?, symbol?, key_id?, market?}` with `event` one of `private.fill`, `private.order`, `private.position`, `private.balance`, `private.notification`, `liquidation`.

- Private events fire only while that exchange account's private stream is active: either the user has the account open in the app (plus about 10 minutes grace), or the trigger names the stored venue key (`key_id`, optional `market`, default spot) so Melaya keeps the stream alive. The key is typed as an id (the id of the exchange key stored in Connectors), in the app too: there is no picker. The number of such kept-alive streams is capped per plan.
- Symbols match loosely (`BTC/USDT` = `btcusdt`).
- Liquidations are thinned to at most 4 per second per market. On the Sandbox plan a liquidation trigger must name an exchange.
- Trading itself is not exposed over MCP; any trading crew is always gated.

## 5. Poll (kind `poll`)

Melaya calls ONE read-only connector tool as the owner on an interval and fires once per new item.

```json
{
  "kind": "poll",
  "config": {
    "poll": {
      "service": "melaya_core",
      "tool": "fetch_rss",
      "args": { "url": "https://www.bing.com/news/search?format=rss&q=seed+round+fintech", "max_items": 20 },
      "format": "text_lines",
      "line_marker": "link: ",
      "marker_at_line_start": true,
      "interval_sec": 3600,
      "max_items_per_poll": 20
    },
    "action": { "type": "pipeline_run" }
  }
}
```

| Field | Rule |
|---|---|
| `service` | the connector service id that owns the tool |
| `tool` | must exist, be read-only and belong to `service`. Connector tools are allowed; of the keyless core tools only `fetch_rss` (`service: "melaya_core"`). Web fetch, scraping and file tools are refused (`poll_tool_not_allowed`) |
| `args` | static JSON (strings up to 2 KB, whole up to 8 KB). The only placeholder is `{{cursor}}` (needs `cursor_path`). URLs must be public https on port 443; local file paths are refused (`poll_args_refused`) |
| `format` | `json` (default) or `text_lines` |
| `items_path` / `item_id_path` | json: where the list is (`""` = the result itself) and the id inside one item (required) |
| `item_version_path` | json, optional: an update also fires (for example `updatedAt`) |
| `line_marker`, `marker_at_line_start`, `id_from` (`after_marker` or `line_start`), `id_digits_only` | text_lines: a line containing the marker starts an item |
| `cursor_path` | json only |
| `interval_sec` | 60-86400, default 900; raised to the plan's poll floor |
| `max_items_per_poll` | 1-50, default 20; the rest waits for the next poll |

Behaviour:
- The first poll after creating, editing or re-enabling is a silent baseline: it records what exists and fires nothing. Enabling a mail poll never replays the inbox.
- New items fire oldest first. The last 500 ids are remembered.
- A result without the expected shape is an error (`unexpected_result_shape`), never "no items".
- Each poll is a real API call on the user's provider quota, and counts toward the plan's daily poll budget (`poll_daily_cap` when spent).
- `fetch_rss` prints text: one block per item (`[n] <title>`, then `published:`, `source:`, `link:`, `summary:` lines). `[n]` is a position, never an id: key items on the `link:` line (the example). The title of the next item lands in the previous item's text, so let the agent re-open the URL when it needs the title.
- Before creating a connector poll, run the same tool with `melaya_connector_call` and read the real result shape; set the paths from what you see. Never guess. Then ask the user to press "Check now" on the trigger's Setup tab in the app: it shows what would fire without firing.
- Custom polls (a read tool of your choice, not a preset) can be created only over MCP; the app has presets only. Once created, the user sees and edits it like a preset.
- Errors: blocked (`config_invalid`, `unknown_tool`, `not_read_only`, `poll_tool_not_allowed`, `poll_args_refused`: re-checked hourly), not connected or forbidden (backs off up to 6 h), transient (retries within minutes).

Ready poll presets in the app (with their real tools): new Gmail email (`gmail_read`), new Zoho Mail email, Facebook post comments, Instagram media comments, LinkedIn page posts and page inbox, X mentions (hourly, X bills every read), Marsel notifications, Discord channel messages, new Gitea issue or pull request, HubSpot deal changed, new Airtable record, upcoming Google Calendar events, plus keyless ones that need nothing connected (new Greenhouse or Lever jobs, Bluesky posts, DEV articles, new GLEIF entities, new Norwegian or French company registrations, Snapshot proposals, stablecoin depegs, treasury wallet inflows, new DEX pools) and Safe queued transactions (Safe connector). Prefer an instant preset when the same app has one.

## 6. Form responses

No native Typeform or Tally trigger: have the form tool's webhook relayed by a backend or automation that signs it with the `melaya` scheme (the "form response" preset), or send the responses to an email inbox and use the Gmail instant trigger.
