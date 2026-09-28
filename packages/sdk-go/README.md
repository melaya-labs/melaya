# melaya-go

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.

**Melaya products:** [Melaya Agents](https://melaya.org/en/product/agentic-framework) · [Melaya Assistant](https://melaya.org/en/product/assistant) · [Device Control](https://melaya.org/en/product/agentic-device-control) · [Browser Control](https://melaya.org/en/product/agentic-browser-control) · [MCP Server](https://melaya.org/en/product/mcp) · [Melaya Marketing](https://melaya.org/en/product/marketing)

## Install

```sh
go get github.com/melaya-labs/melaya/packages/sdk-go
```

Requires Go 1.22+. The only external dependency is `github.com/gorilla/websocket` for WebSocket streaming.

## Quick start — Agent Builder & Device Control

Pair an Android phone, restrict which apps an agent may operate, then create
and run an agent pipeline. Configure provider credentials through Melaya
Connectors first — never include a provider key in pipeline configuration or
per-run overrides.

```go
package main

import (
    "context"
    "fmt"
    "log"
    "os"

    melaya "github.com/melaya-labs/melaya/packages/sdk-go/melaya"
)

func main() {
    m, err := melaya.New(os.Getenv("MELAYA_API_KEY"))
    if err != nil { log.Fatal(err) }

    ctx := context.Background()

    // Pair an Android phone (Device Control)
    pair, err := m.Phone.Pair(ctx)
    if err != nil { log.Fatal(err) }
    fmt.Printf("pair code %s (expires in %ds)\n", pair.Code, pair.ExpiresInSeconds)

    devices, _ := m.Phone.ListDevices(ctx)
    apps, _ := m.Phone.ListApps(ctx)
    fmt.Printf("%d devices, %d apps\n", len(devices), len(apps))

    _, _ = m.Phone.SetAllowedApps(ctx, []string{"com.android.chrome"})

    // Create an agent pipeline. IMPORTANT: the run is generated ONLY from
    // steps[] — a top-level "agents" array alone produces an EMPTY pipeline.
    // Embed each agent's full definition inside its own step.
    _, err = m.Pipelines.Create(ctx, melaya.PipelineConfig{
        "name":    "mobile-review",
        "project": "Operations",
        "steps": []map[string]interface{}{{
            "kind": "agent",
            "agent": map[string]interface{}{
                "name":        "mobile-operator",
                "role":        "Careful mobile operator",
                "instruction": "Read before acting. Never send, publish, or delete.",
                "model":       map[string]interface{}{"provider": "anthropic", "name": "claude-sonnet-4-6"},
                "agent_tools": []string{
                    "phone_get_screen_tree",
                    "phone_current_app",
                    "phone_open_app",
                    "phone_click_text",
                    "phone_back",
                    "phone_wait",
                },
            },
        }},
        "maxCostUsd": 1.00,
    })
    if err != nil { log.Fatal(err) }

    // Run it and hand the run to the phone
    run, err := m.Pipelines.Run(ctx, "mobile-review", &melaya.PipelineRunOptions{Project: "Operations"})
    if err != nil { log.Fatal(err) }

    _, _ = m.Phone.RegisterActiveRun(ctx, run.RunID)

    status, err := m.Pipelines.RunStatus(ctx, "mobile-review", run.RunID)
    if err != nil { log.Fatal(err) }
    fmt.Println(status.Status)

    // Live run events (Socket.IO) — connects lazily on first subscription
    unsub := m.Events.OnRunUpdate(run.RunID, func(e melaya.RunPushEvent) {
        fmt.Println(e.EventType, e.Status)
    })
    defer unsub()
    defer m.Events.Close()
}
```

## Quick start — Trading (preview)

```go
package main

import (
    "context"
    "fmt"
    "log"

    melaya "github.com/melaya-labs/melaya/packages/sdk-go/melaya"
)

func main() {
    m, err := melaya.New("mk_YOUR_KEY_HERE")
    if err != nil { log.Fatal(err) }

    ctx := context.Background()

    // Market data
    t, err := m.Market.Ticker(ctx, melaya.SymbolQuery{
        Exchange: "binance", Symbol: "BTC/USDT", Market: "spot",
    })
    if err != nil { log.Fatal(err) }
    fmt.Printf("BTC/USDT last: %v\n", *t.Last)

    // Launch a custom paper strategy
    result, err := m.Strategies.Create(ctx, melaya.StrategyCreate{
        Name:         "my-rhai-strategy",
        StrategyType: "custom",
        Exchange:     "binance",
        Symbol:       "BTC/USDT",
        Market:       "spot",
        DryRun:       true,  // paper mode — no real orders
        Params: map[string]interface{}{
            "language":   "rhai",
            "definition": `fn evaluate() { emit_long(param("qty")); }`,
            "qty":        0.001,
        },
    })
    if err != nil { log.Fatal(err) }
    fmt.Printf("Strategy created: %s\n", result.StrategyID)

    // Clean up
    m.Strategies.Stop(ctx, result.StrategyID)
    m.Strategies.Delete(ctx, result.StrategyID)

    // Run a backtest
    since := int64(1_700_000_000_000) // ms
    until := int64(1_700_604_800_000) // ms (~7 days later)
    bt, err := m.Backtest.Start(ctx, melaya.BacktestStart{
        StrategyType: "custom",
        Exchange:     "binance",
        Symbol:       "BTC/USDT",
        Timeframe:    "1h",
        SinceMS:      &since,
        UntilMS:      &until,
        Language:     "rhai",
        Definition:   `fn evaluate() { emit_long(param("qty")); }`,
        Params:       map[string]interface{}{"qty": 0.001},
    })
    if err != nil { log.Fatal(err) }
    fmt.Printf("Backtest job: %v\n", bt["job_id"])
}
```

## Auth

API keys are created at **melaya.org → Settings → API Keys**. Keys must be prefixed `mk_`.

Pass the key to `melaya.New(...)`. On the wire:

- **REST** sends the key **only** as an `Authorization: Bearer mk_...` header — never in the query string.
- **Public WebSocket streams** (`m.Stream.Ticker`, `Orderbook`, …) authenticate with `?apiKey=` in the `wss://` URL (server protocol).
- **Private WebSocket streams** (`m.Stream.Strategies`, `Private`) first mint a short-lived ticket over REST and connect with `?wsTicket=` — the API key itself never rides a private stream URL.

Never hard-code keys in source files. Use environment variables:

```go
m, _ := melaya.New(os.Getenv("MELAYA_API_KEY"))
```

## Embeddable pipeline lifecycle — run inputs, uploads, docs, and audit

- **`Get`/`Update` envelope:** `Get` returns the server's envelope
  `{name, client, config, code, docs}` — the editable config lives under the
  `"config"` key. To edit a pipeline, mutate `envelope["config"]` and pass
  *that* to `Update`, not the envelope itself. `Update` takes
  `PipelineUpdateBody{Config, Project}`.
- **`Run` with inputs:** pass `PipelineRunOptions.RunInputs` to thread a brief
  and/or named input values (`RunInputs{Brief, Values}`) through to a run. A
  file value inside `Values` can be `{"file_id": ...}` (from `UploadRunFile`),
  `{"url": ...}` (≤25 MB), or `{"base64": ..., "name": ...}` (≤7 MB). The
  response echoes the resolved `RunInputs` back.
- **File uploads:** `UploadRunFile`, `UploadDoc` (static-context docs),
  and `UploadRetrievalDoc` (RAG docs) each send a `multipart/form-data`
  request built with the standard library's `mime/multipart` — pass any
  `io.Reader` plus a filename.
- **`IngestRetrieval`** can take minutes on a large corpus; since every
  request on a `Client` shares its `Options.Timeout`, construct one with a
  longer timeout (e.g. 300s) if you have a lot of documents to embed.
- **Config fields worth knowing:** `hitl_mode` (`"safe"` default |
  `"autonomous"` | `"payments_only"` — only `"safe"` honors
  `human_approval_tools`), `connector_source` (`"personal"` | `"project"`),
  `force_local_runner`, `inputs[]`. See `PipelineConfig`'s doc comment for
  the full reference, including why a top-level `agents[]` array alone
  produces an empty pipeline.

## Connector tools — calling tools directly over REST

`m.ConnectorTools` (alias `m.Agents.ConnectorTools`) exposes the same
list/discover/test/connect/call surface the Melaya MCP server gives an
assistant — for calling a connector tool directly from your own code. Do not
confuse it with `m.Connectors`, which only stores project-scoped connector
*credentials*.

- **Reads run immediately.** `Call` returns `Status: "done"` with `Result` set.
- **Writes default to an approval card** raised in the Melaya app (the same
  card the Assistant raises) — `Call` returns HTTP 202, decoded as
  `Status: "pending_approval"` with `RequestID` set (a 202 is a success, not
  an error). Poll `CallStatus`, or use `CallAndWait` to block until the user
  decides.
- **`ConnectorToolCallOptions{Approval: "none"}`** runs a write immediately
  instead of staging it — it is still audit-logged.
- **Money-moving and trading tools are always refused**, under both approval
  modes — the call returns a `*MelayaError` with
  `Code == "money_moving_requires_app_approval"`; check it with
  `err.(*melaya.MelayaError).IsMoneyMovingRefused()`.
- **No method here ever accepts or returns a credential value.**

```go
tools, _ := m.ConnectorTools.Search(ctx, "unread email", nil)
res, _ := m.ConnectorTools.Call(ctx, "gmail_list_messages", nil, nil)
fmt.Println(res.Result)

// A write, approved in the Melaya app, waited on synchronously:
out, err := m.ConnectorTools.CallAndWait(ctx, "gmail_send",
    map[string]interface{}{"to": "a@b.com", "subject": "hi", "body": "…"}, nil)
if err != nil {
    if melErr, ok := err.(*melaya.MelayaError); ok && melErr.IsMoneyMovingRefused() {
        log.Fatal("refused: money-moving tools never run over the API")
    }
    log.Fatal(err)
}
fmt.Println(out.Status, out.Result)
```

### ConnectorTools (`m.ConnectorTools.*`)

| Method | Description |
|---|---|
| `Services(ctx)` | Connected services + read/write tool counts |
| `Search(ctx, q, *ConnectorToolSearchOptions)` | Discover tools by business keywords (`Limit` 1-50, default 15) |
| `Describe(ctx, tool)` | One tool's full description + parameters |
| `Test(ctx, service)` | Test the stored credential for a service |
| `Connect(ctx, service)` | Start connecting a service (never takes a secret) |
| `Call(ctx, tool, args, *ConnectorToolCallOptions)` | Invoke a tool — immediate for reads, staged for approval by default on writes |
| `CallStatus(ctx, requestID)` | Outcome of a staged write |
| `CallAndWait(ctx, tool, args, *ConnectorToolCallAndWaitOptions)` | `Call` plus polling `CallStatus` to a terminal outcome (`PollInterval` default 3s, `Timeout` default 10m) |

## Event triggers: read, diagnose, and dry-run

`m.Triggers` (alias `m.Agents.Triggers`) reads your event triggers and helps
you find out why one did or did not fire. Creating, editing, deleting and
rotating a trigger are done in the Melaya app; they are not on the REST
surface. Secrets are never returned.

- **`Test` and `PollTest` are dry runs.** The prefilter, decision and routing
  run for real, the action never executes. `PollNow` queues a real poll.
- **`Events` shows what left no receipt**: filtered events, shed events and
  ingress rejections, next to the normal verdicts. Filter with
  `TriggerEventsOptions` (`TriggerID`, `Since` in epoch ms, `Verdicts`,
  `Limit` 1-200). `TriggerVerdicts` lists every verdict.
- **A failed dry poll is a result, not an error.** When the poll's tool call
  fails, `PollTest` returns `OK == false` with `Error` set to the poll error
  code. HTTP errors, such as a 429 when dry polls are throttled, still return
  a `*MelayaError`.

```go
res, _ := m.Triggers.Test(ctx, triggerID, map[string]interface{}{"amount": 42})
ev, _ := m.Triggers.Events(ctx, &melaya.TriggerEventsOptions{TriggerID: triggerID, Limit: 20})
for _, e := range ev.Events {
    if e.EventID == res.EventID {
        fmt.Println(e.Verdict)
    }
}
```

### Triggers (`m.Triggers.*`)

| Method | Description |
|---|---|
| `List(ctx, *TriggerListOptions)` | Your triggers, optionally filtered by `Project` and `PipelineName` |
| `Get(ctx, id)` | One trigger with its config |
| `Deliveries(ctx, id, limit)` | Recent delivery receipts (`limit` 1-200, 0 = default 50) |
| `Stats(ctx, id, hours)` | Counts and latency by verdict (`hours` 1-168, 0 = default 24) |
| `PendingApprovals(ctx, id)` | Tool calls of this trigger waiting on a human decision |
| `Test(ctx, id, payload)` | Send one event as a dry run |
| `Events(ctx, *TriggerEventsOptions)` | Live event log, newest first |
| `PollStatus(ctx, id)` | Poll runtime state: status, last error, next poll, baseline, intervals |
| `PollTest(ctx, id)` | Dry poll: what the tool found and would publish |
| `PollNow(ctx, id)` | Queue a real poll now |
| `PollSync(ctx, id)` | Re-create the poll runtime row from the saved config |
| `Presets(ctx)` | Trigger presets for your connected services and tier |
| `Limits(ctx)` | Plan caps and usage |
| `Sources(ctx)` | WebSocket and SSE stream sources |

## API surface — Agent Builder & platform (GA)

Flat accessors shown; the same pointers are grouped under `m.Agents.*` and
`m.Platform.*` domain namespaces (`m.Platform.Accounts` is an alias of
`m.Auth`).

| Namespace | Methods |
|---|---|
| `m.Auth` | `Login`, `VerifyMFA`, `Register`, `VerifySignup`, `ResendVerification`, `Me`, `Check`, `ChangePassword`, `ForgotPassword`, `ResetPassword`, `CreateMobileHandoff`, `MyPermissions`, `Refresh`, `MFAStatus`, `MFASetup`, `MFAConfirm`, `ResendEmailVerification`, `VerifyEmail`, `ExportMyData`, `RemoveKey`, `UpdateProfile`, `Credits`, `AICredits`, `PortfolioIdeasCredits`, `RiskMonitoringCredits`, `Version` |
| `m.Projects` | `List`, `Create`, `Rename`, `RunnerProjects` |
| `m.Connectors` | `ConnectedServices`, `Set`, `Delete`, `EnvHandle`, `GoogleOAuthStart`, `ApplyPersonal`, `SharedBy`, `GoogleStatus`, `GoogleSetDefault`, `GoogleDisconnect`, `DBTestStart`, `DBTestStatus` |
| `m.Credentials` | `List`, `ConnectedServices`, `Get`, `Set`, `Delete`, `Test`, `GetOperatorProfile`, `SetOperatorProfile`, `ListModels`, `MelayaAccounts`, `GoogleStatus`, `GoogleSetDefault`, `GoogleDisconnect`, `DBTestStart`, `DBTestStatus`, `TelegramQrStart`, `TelegramQrPoll`, `WhatsappSignupConfig`, `WhatsappSignupExchange`, `TiktokCreatorInfo`, `SubstackEmailLinkSend`, `SubstackEmailLinkRedeem`, plus connector auth flows (`RagIngest*`, `RagRetrieve*`, `PickFolder*`, `LinkedInConnect*`, `LumaConnect*`, `GoogleOAuthStart`, `CliAuthStart`, `NotebookLM*`, `TelegramAuth*`) |
| `m.Pipelines` | `ListPipelines`, `Create`, `Get`, `Update`, `Delete`, `Run`, `RunIDs`, `RunStatus`, `CancelRun`, `UploadRunFile`, `RunInputs`, `RunInputFile`, `RunActive`, `ListDocs`, `UploadDoc`, `DeleteDoc`, `UploadRetrievalDoc`, `IngestRetrieval`, `DeleteRetrievalDoc`, `Outputs`, `Output`, `PreviewCode`, `Tools`, `Subagents`, `InstantiateTemplate`, `BuildWithAI`, `Overview`, `Count`, `List`, `Recent`, `Traces`, `Trace`, `TraceStats`, `DeleteTraces`, `ProjectToolCalls`, `ProjectToolCallFacets`, `ToolCallDetail`, `ListSchedules`, `GetSchedule`, `UpsertSchedule`, `PauseSchedule`, `ResumeSchedule` |
| `m.Templates` | `List`, `ListGlobal`, `ListValidated`, `Save`, `Update`, `Duplicate`, `Delete`, `Share`, `ListAssignments`, `Assign`, `Unassign`, `ShareTargets` — `Assign`/`Unassign` take a `TemplateAssignBody` with exactly one of `UserID` or `ProjectID` set (both UUIDs) |
| `m.Phone` | `Pair`, `ListDevices`, `RevokeDevice`, `ScreenTree`, `ListApps`, `SetAllowedApps`, `GrantApp`, `RegisterActiveRun`, `RequestCast` |
| `m.Hitl` | `Pending`, `History`, `Approve`, `Reject`, `BulkDecide`, `RunToolStats`, `RunToolStatsByAgent`, `RunMessages`, `RunToolCalls` |
| `m.Evals` | `ListRuns`, `Summary`, `RunDetail`, `Compare`, `MemoryGraph`, `RunMemory`, `CrewMemory`, `EditMemoryEntry`, `DeleteMemoryEntry`, `Benchmarks` |
| `m.ConnectorTools` | `Services`, `Search`, `Describe`, `Test`, `Connect`, `Call`, `CallStatus`, `CallAndWait` — the same tool-call surface the MCP server exposes; see "Connector tools" above |
| `m.Triggers` | `List`, `Get`, `Deliveries`, `Stats`, `PendingApprovals`, `Test`, `Events`, `PollStatus`, `PollTest`, `PollNow`, `PollSync`, `Presets`, `Limits`, `Sources`; see "Event triggers" above |
| `m.Events` | `OnRunUpdate`, `OnInitPhase`, `OnProjectEvent`, `OnHitlApproval`, `OnPipelineCreated`, `OnPipelineUpdated`, `OnPipelineDeleted`, `LeaveRun`, `LeaveProject`, `Close` — connects lazily on first subscription; nothing is opened at client construction |
| `m.Billing` | `Subscription`, `CreateCheckout`, `CreatePortal`, `Plans`, `AmbassadorPerk`, `RedeemCode`, `ReservedPromo` |
| `m.Runner` | `CreateToken`, `ListTokens`, `RevokeToken` |
| `m.Team` | `ListMembers`, `Invite`, `CreateInviteLink`, `AcceptInvite`, `UpdateMemberRole`, `RemoveMember`, `TransferOwnership`, `GetPipelineVisibility`, `SetPipelineVisibility` |
| `m.Assistant` | `GetProfile`, `SetProfile` |
| `m.Bugs` | `Create`, `ListMine`, `Get`, `AddComment`, `ListNotifications`, `MarkNotificationsRead` |
| `m.Overview` | `ModelPrices`, `ChartData`, `UsageSummary`, `CostBreakdown` |

## Method table — Trading (preview)

> Preview surface only — not generally available. Do not use with real funds.

### Market (`m.Market.*`)

| Method | Description |
|---|---|
| `ListExchanges(ctx)` | All supported exchanges |
| `Ticker(ctx, SymbolQuery)` | Best bid/ask + 24h aggregates |
| `Orderbook(ctx, OrderBookQuery)` | Order book to a given depth |
| `Ohlcv(ctx, OhlcvQuery)` | OHLCV candles |
| `Trades(ctx, SymbolQuery)` | Recent public trades |
| `Markets(ctx, ExchangeQuery)` | Tradable markets on a venue |
| `Currencies(ctx, ExchangeQuery)` | Listed currencies |
| `Status(ctx, ExchangeQuery)` | Operational status |
| `Time(ctx, ExchangeQuery)` | Exchange server time |
| `Tickers(ctx, body)` | Multi-symbol tickers (POST) |
| `FundingRates(ctx, body)` | Latest funding rates |
| `FundingRateHistory(ctx, body)` | Funding-rate history |
| `OpenInterest(ctx, body)` | Open interest |
| `OpenInterestHistory(ctx, body)` | Open-interest history |
| `Instruments(ctx, body)` | Instruments + constraints |
| `LiquidationEvents(ctx, body)` | Historical liquidation events |
| `OhlcvMulti(ctx, body)` | Multi-symbol OHLCV |
| `MarketConstraints(ctx, body)` | Trading constraints for one symbol |
| `FundingRateHistoryMulti(ctx, body)` | Funding rate history across venues |
| `OpenInterestHistoryMulti(ctx, body)` | Open interest history across venues |
| `PredictionMarkets(ctx, body)` | Prediction market listings |
| `CatalogCounts(ctx)` | Platform catalog counts |

### Account (`m.Account.*`)

| Method | Description |
|---|---|
| `Keys(ctx)` | Connected exchange API keys |
| `Usage(ctx)` | Tier + live usage counters |
| `APIKeyStatus(ctx)` | Platform key status |

### Sim (`m.Sim.*`)

| Method | Description |
|---|---|
| `ListAccounts(ctx)` | Paper accounts |
| `Balance(ctx, strategyID, asset)` | Virtual balance |
| `Positions(ctx, strategyID)` | Open paper positions |
| `OpenOrders(ctx, strategyID)` | Resting paper orders |
| `MyTrades(ctx, strategyID)` | Filled paper trades |
| `CreateOrder(ctx, SimCreateOrder)` | Place a paper order |
| `CancelOrder(ctx, strategyID, orderID, symbol, exchange)` | Cancel a paper order |

### Strategies (`m.Strategies.*`)

| Method | Description |
|---|---|
| `List(ctx)` | All your strategies |
| `Get(ctx, strategyID)` | Single strategy by id |
| `Create(ctx, StrategyCreate)` | Launch a strategy |
| `Pause(ctx, strategyID)` | Pause |
| `Resume(ctx, strategyID)` | Resume |
| `Stop(ctx, strategyID)` | Stop and tear down |
| `Delete(ctx, strategyID)` | Soft-delete |
| `UpdateParams(ctx, strategyID, params)` | Update running params |
| `Status(ctx, strategyID)` | Runtime status |
| `Performance(ctx, strategyID)` | Equity / PnL series |
| `Executions(ctx, strategyID)` | Order rows |
| `Trades(ctx, strategyID)` | Fill rows |
| `Logs(ctx, strategyID)` | Log rows |
| `AIOptStart(ctx, strategyID, body)` | Start AI optimizer |
| `AIOptStatus(ctx, strategyID)` | Optimizer status |
| `AIOptApprove(ctx, strategyID, body)` | Apply proposed params |
| `AIOptStop(ctx, strategyID)` | Stop optimizer |
| `AIOptRuns(ctx, strategyID)` | Past optimizer runs |

### Backtest (`m.Backtest.*`)

| Method | Description |
|---|---|
| `Start(ctx, BacktestStart)` | Start a backtest |
| `Job(ctx, jobID)` | Job status + progress |
| `Results(ctx, jobID)` | Metrics + equity curve |
| `Trades(ctx, jobID, limit, offset)` | Trade list |
| `Sweep(ctx, parentID, objective, limit)` | Sweep children |
| `List(ctx, limit, offset)` | Your jobs |
| `Favorites(ctx, limit, offset)` | Favorited jobs |
| `FundingRange(ctx, exchange, symbol)` | Earliest funding timestamp |
| `Cancel(ctx, jobID)` | Cancel in-flight job |
| `Delete(ctx, jobID)` | Delete one job |
| `DeleteAll(ctx)` | Delete all non-favorited jobs |

### Stream (`m.Stream.*`)

| Method | Description |
|---|---|
| `Ticker(exchange, symbol, market)` | Live ticker frames |
| `Orderbook(exchange, symbol, market, limit)` | Live order-book frames |
| `Ohlcv(exchange, symbol, timeframe, market)` | Live OHLCV frames |
| `Trades(exchange, symbol, market)` | Live public-trade frames |
| `Liquidations(exchange)` | Liquidation firehose |
| `Strategies()` | Private: strategy events |
| `Private(exchange, market, apiKeyID, keyID, symbol)` | Private: account feed |

Each stream method returns a `*Stream` with a `Ch <-chan Frame` channel and a `Close()` method.

```go
s, err := m.Stream.Ticker("binance", "BTC/USDT", "spot")
if err != nil { log.Fatal(err) }
defer s.Close()
for frame := range s.Ch {
    fmt.Println(frame["last"])
}
```

## License

Apache-2.0 — see [LICENSE](./LICENSE).
