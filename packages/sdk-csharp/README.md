# Melaya .NET SDK

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.
Normalized market data, paper/live strategies, backtesting, and WebSocket streaming
across 70+ venues — with zero external NuGet dependencies.

**Melaya products:** [Melaya Agents](https://melaya.org/en/product/agentic-framework) · [Melaya Assistant](https://melaya.org/en/product/assistant) · [Device Control](https://melaya.org/en/product/agentic-device-control) · [Browser Control](https://melaya.org/en/product/agentic-browser-control) · [MCP Server](https://melaya.org/en/product/mcp) · [Melaya Marketing](https://melaya.org/en/product/marketing)

## Installation

```xml
<!-- In your .csproj, once the package is published to NuGet -->
<PackageReference Include="Melaya.SDK" Version="0.3.0" />
```

Or reference the project directly during development:

```xml
<ProjectReference Include="../sdk-csharp/Melaya/Melaya.csproj" />
```

## Authentication

Every Melaya API key is prefixed `mk_`. Create one at **melaya.org → Settings → API Keys**.
On REST requests the SDK sends the key **only** as an `Authorization: Bearer mk_…` header —
never in the query string. Public WebSocket market streams authenticate with an `?apiKey=`
query parameter on the `wss://` URL (the server's protocol for public feeds); private
WebSocket streams never carry the key — they use a short-lived `?wsTicket=` minted over REST.

**Never commit your key.** Read it from an environment variable:

```csharp
var mk = Environment.GetEnvironmentVariable("MK")
    ?? throw new InvalidOperationException("MK env var not set");
```

## Quick start: Agent Builder & Device Control

The generally available surface. Pair an Android phone, then let a pipeline agent
operate approved apps through the visible interface.

```csharp
using Melaya;

var mk = Environment.GetEnvironmentVariable("MK")!;
await using var m = new MelayaClient(new MelayaOptions { ApiKey = mk });

// 1. Pair a phone — enter the code in the Melaya mobile app
var pair = await m.Phone.PairAsync();
Console.WriteLine($"Pair code: {pair.Code} (expires in {pair.ExpiresInSeconds}s)");

var devices = await m.Phone.ListDevicesAsync();
var apps    = await m.Phone.ListAppsAsync();

// Give the agent the smallest practical app allowlist
await m.Phone.SetAllowedAppsAsync(new[] { "com.android.chrome" });
```

Configure provider credentials through Melaya Connectors first. Never include a
provider key in pipeline configuration or per-run overrides.

```csharp
using System.Text.Json;

// 2. Create a pipeline (extra config fields go through Config extension data).
//
//    The run is generated ONLY from `steps` — a top-level `agents` array by itself
//    produces an EMPTY pipeline. Each step embeds its own full agent definition.
//    There is no `prompt` field: the task goes in `instruction`, and
//    `system_prompt_override` replaces the persona prompt entirely.
await m.Pipelines.CreateAsync(new PipelineCreateRequest
{
    Name    = "mobile-review",
    Project = "Operations",
    Config  = new Dictionary<string, JsonElement>
    {
        ["steps"] = JsonSerializer.SerializeToElement(new object[]
        {
            new
            {
                kind  = "agent",
                agent = new
                {
                    name        = "mobile-operator",
                    role        = "Careful mobile operator",
                    instruction = "Read before acting. Never send, publish, or delete.",
                    model       = new { provider = "anthropic", name = "claude-sonnet-4-6" },
                    agent_tools = new[]
                    {
                        "phone_get_screen_tree",
                        "phone_current_app",
                        "phone_open_app",
                        "phone_click_text",
                        "phone_back",
                        "phone_wait",
                    },
                    human_approval_tools = Array.Empty<string>(),
                },
            },
        }),
        ["maxCostUsd"] = JsonSerializer.SerializeToElement(1.00),
        // Other config knobs worth knowing:
        //   hitl_mode         "safe" (default) | "autonomous" | "payments_only" —
        //                     only "safe" honours each agent's human_approval_tools.
        //   connector_source  "personal" | "project"
        //   force_local_runner  bool — force execution on the caller's own runner.
        //   inputs[]          declares the run_inputs schema accepted by RunAsync().
    },
});

// 3. Run it, hand the phone to the run, and watch status.
//    `executionTarget` below is used ONLY for the tier/capacity check — it does NOT
//    decide where the run executes (that's the stored config: local model providers,
//    force_local_runner). env_overrides' MEL_* / MELAYA_* keys are stripped server-side.
var run = await m.Pipelines.RunAsync("mobile-review", new PipelineRunRequest { Project = "Operations" });
await m.Phone.RegisterActiveRunAsync(run.RunId!);

m.Events.OnRunUpdate(run.RunId!, e => Console.WriteLine($"{e.EventType}: {e.Message}"));

var status = await m.Pipelines.RunStatusAsync("mobile-review", run.RunId!);
Console.WriteLine(status.Status);
```

### Read → edit → save a pipeline config

`GetAsync` returns the full envelope `{ name, client, config, code, docs }` — the editable
pipeline config lives under `config`. Mutate it (e.g. via `JsonNode`) and pass it straight
back to `UpdateAsync`:

```csharp
using System.Text.Json;
using System.Text.Json.Nodes;

var envelope = await m.Pipelines.GetAsync("mobile-review", "Operations");
var config   = JsonNode.Parse(envelope.GetProperty("config").GetRawText())!;

// Swap the model on the first (and only) step's agent.
config["steps"]![0]!["agent"]!["model"] = JsonNode.Parse("""{"provider":"anthropic","name":"claude-opus-4-8"}""");

await m.Pipelines.UpdateAsync("mobile-review", new PipelineUpdateRequest
{
    Config  = JsonSerializer.SerializeToElement(config),
    Project = "Operations",
});
```

### Run inputs and file uploads

```csharp
// Upload a file first (single-use, valid 24h), then reference it by file_id.
var upload = await m.Pipelines.UploadRunFileAsync(
    "mobile-review", key: "screenshot", file: File.ReadAllBytes("shot.png"), filename: "shot.png");

var run = await m.Pipelines.RunAsync("mobile-review", new PipelineRunRequest
{
    Project    = "Operations",
    RunInputs  = new PipelineRunInputs
    {
        Brief  = "Review the attached screenshot and summarize any issues.",
        Values = new Dictionary<string, object>
        {
            ["screenshot"] = new { file_id = upload.FileId },
        },
    },
});

var inputs = await m.Pipelines.RunInputsAsync("mobile-review", run.RunId!);
var active = await m.Pipelines.RunActiveAsync("mobile-review", run.RunId!);
```

The platform catalogs currently expose **8,000+ scoped tools**, **100+ specialized
subagents**, and **48 AI providers** (`m.Pipelines.ToolsAsync()`,
`m.Pipelines.SubagentsAsync()`, `m.Agents.Models.ListModelsAsync()`; live counts via
`m.Market.CatalogCountsAsync()`).

## GA API surface

All namespaces below are generally available. Flat accessors (`m.Pipelines`, …) and
domain accessors (`m.Agents.Pipelines`, `m.Platform.Projects`, …) point to the same objects.

### auth (`m.Auth`) + mfa (`m.Mfa`)

| Method | Description |
|--------|-------------|
| `LoginAsync(username, password)` | Password login (may return an MFA challenge) |
| `VerifyMfaAsync(challengeToken, code)` | Complete MFA login |
| `RegisterAsync(username, email, password)` | Create an account |
| `VerifySignupAsync(token)` | Confirm signup email |
| `ResendVerificationAsync(email)` | Resend signup email |
| `ForgotPasswordAsync(email)` | Start password reset |
| `ResetPasswordAsync(token, newPassword)` | Complete password reset |
| `MeAsync()` | Current user profile |
| `CheckAsync()` | Validate the session/key |
| `ChangePasswordAsync(current, next)` | Change password |
| `MobileHandoffAsync()` | Mint a mobile handoff code |
| `PermissionsAsync()` | Effective permissions |
| `RefreshAsync()` | Refresh the session token |
| `Mfa.StatusAsync()` | TOTP status |
| `Mfa.SetupAsync()` | Begin TOTP setup (QR + secret) |
| `Mfa.ConfirmSetupAsync(code)` | Confirm TOTP setup |

### projects (`m.Projects`)

| Method | Description |
|--------|-------------|
| `ListAsync()` | Projects you belong to |
| `CreateAsync(body)` | Create a project |
| `RenameAsync(oldName, newName)` | Rename a project |
| `RunnerProjectsAsync()` | Projects visible to your runner |

### connectors (`m.Connectors`)

| Method | Description |
|--------|-------------|
| `ConnectedServicesAsync(project)` | Connected services for a project |
| `SetAsync(project, service, body)` | Store a project-scoped connector credential |
| `DeleteAsync(project, service)` | Remove a connector credential |
| `EnvHandleAsync(project)` | Env-handle summary for pipeline config |
| `GoogleOAuthStartAsync(project, body?)` | Start project-scoped Google OAuth |
| `ApplyPersonalAsync(project, service, googleCapabilities?)` | Share the caller's own personal connector into a project |
| `SharedByAsync(project)` | Connectors shared into a project by other members |
| `GoogleStatusAsync(project)` | Project-scoped Google connector status |
| `GoogleSetDefaultAsync(project, capability, accountId)` | Set default Google account for a capability |
| `GoogleDisconnectAsync(project, accountId, capability?)` | Disconnect a Google account (or one capability) |
| `DbTestStartAsync(project, service, credentials?)` / `DbTestStatusAsync(project, sessionId)` | Probe a database from the user's runner |

### credentials (`m.Credentials`)

| Method | Description |
|--------|-------------|
| `ListAsync()` | Stored user-scoped credentials (masked) |
| `ConnectedServicesAsync()` | Connected services |
| `GetAsync(service, key?)` | One credential (masked) |
| `SetAsync(service, body)` | Store a credential |
| `DeleteAsync(service)` | Delete a credential |
| `TestAsync(service)` | Validate a credential |
| `GetOperatorProfileAsync()` / `SetOperatorProfileAsync(profile)` | Operator profile |
| `ListModelsAsync(provider?, capability?)` | Available AI models (also `m.Agents.Models`) |
| `RagIngestStartAsync(body)` / `RagIngestStatusAsync(sessionId)` | RAG ingestion |
| `RagRetrieveStartAsync(body)` / `RagRetrieveStatusAsync(sessionId)` | RAG retrieval |
| `GoogleOAuthStartAsync(body?)`, `CliAuthStartAsync()`, `LinkedInConnect*`, `LumaConnect*`, `TelegramAuth*`, `NotebookLM*` | Guided connect flows |
| `GoogleStatusAsync()` | Caller's Google connector status |
| `GoogleSetDefaultAsync(capability, accountId)` | Set default Google account for a capability |
| `GoogleDisconnectAsync(accountId, capability?)` | Disconnect a Google account (or one capability) |
| `DbTestStartAsync(service, credentials?)` / `DbTestStatusAsync(sessionId)` | Probe a database from the user's runner |
| `TelegramQrStartAsync(apiId, apiHash)` / `TelegramQrPollAsync(handle)` | Telegram QR-code login |
| `WhatsappSignupConfigAsync()` / `WhatsappSignupExchangeAsync(body)` | WhatsApp embedded signup |
| `TiktokCreatorInfoAsync()` | TikTok creator account info |
| `SubstackEmailLinkSendAsync(email)` / `SubstackEmailLinkRedeemAsync(link, email?)` | Substack email-link sign-in |

### pipelines (`m.Pipelines`)

| Method | Description |
|--------|-------------|
| `ListPipelinesAsync()` | Pipeline definitions |
| `CreateAsync(body)` | Create a pipeline |
| `GetAsync(name, project?)` | One pipeline definition |
| `UpdateAsync(name, body)` | Update a pipeline |
| `DeleteAsync(name, project?)` | Delete a pipeline |
| `RunAsync(name, body?)` | Enqueue a run (`body.RunInputs` for a brief/values) |
| `UploadRunFileAsync(name, key, file, filename, contentType?, project?)` | Upload a file for `run_inputs.values` (24h, single-use) |
| `RunIdsAsync(name)` | Run IDs for a pipeline |
| `RunStatusAsync(name, runId)` | Status + cost of a run |
| `RunInputsAsync(name, runId)` | Read back a run's brief/values/file metadata |
| `RunInputFileAsync(name, runId, index)` | Download one run-input file's raw bytes |
| `RunActiveAsync(name, runId)` | Whether a run is still queued/running |
| `CancelRunAsync(name, runId)` | Cancel a run |
| `OutputsAsync(name)` / `OutputAsync(name, path, download?)` | Run artifacts |
| `ToolsAsync()` | Scoped tool catalog (8,000+ tools) |
| `SubagentsAsync()` | Subagent catalog (100+ subagents) |
| `InstantiateTemplateAsync(templateId, body)` | Instantiate a template |
| `PreviewCodeAsync(body)` / `BuildWithAIAsync(body)` | Authoring helpers |
| `OverviewAsync()`, `CountAsync()`, `ListAsync(...)`, `RecentAsync()` | Run overview |
| `TracesAsync(runId)`, `TraceAsync(runId, traceId)`, `TraceStatsAsync(runId, traceId)`, `DeleteTracesAsync(runId)` | Traces |
| `ProjectToolCallsAsync(project, ...)` / `ProjectToolCallFacetsAsync(project)` | Tool-call audit log + filter facets |
| `ToolCallDetailAsync(runId, spanId)` | Full untruncated tool-call input/output |
| `ListSchedulesAsync()`, `GetScheduleAsync(project, name)`, `UpsertScheduleAsync(...)`, `PauseScheduleAsync(...)`, `ResumeScheduleAsync(...)` | Cron schedules |
| `UsageSummaryAsync()`, `ModelPricesAsync()`, `ChartDataAsync()`, `CostBreakdownAsync()` | Cost dashboard |
| `ListDocsAsync(name)` / `UploadDocAsync(name, file, filename, contentType?)` / `DeleteDocAsync(name, filename)` | Static-context documents |
| `UploadRetrievalDocAsync(name, file, filename, contentType?)` / `IngestRetrievalAsync(name, body?, timeoutMs?)` / `DeleteRetrievalDocAsync(name, filename)` | RAG retrieval documents |

### templates (`m.Templates`)

| Method | Description |
|--------|-------------|
| `ListAsync()` | Templates visible to you |
| `ListGlobalAsync()` | Community templates |
| `ListValidatedAsync()` | Platform-validated template IDs |
| `SaveAsync(body)` | Create a template |
| `UpdateAsync(templateId, body)` | Update a template |
| `DuplicateAsync(templateId, newName?)` | Duplicate into your library |
| `DeleteAsync(templateId)` | Delete a template |
| `ShareAsync(templateId, visibility)` | Change visibility |
| `ListAssignmentsAsync(templateId)` | List assignments |
| `AssignAsync(templateId, new TemplateAssignRequest { UserId = … })` | Assign to a user **or** project (set exactly one of `UserId` / `ProjectId`) |
| `UnassignAsync(templateId, new TemplateAssignRequest { ProjectId = … })` | Remove an assignment (target sent as query parameters) |
| `ShareTargetsAsync()` | Projects you can share into |

### phone (`m.Phone`)

| Method | Description |
|--------|-------------|
| `PairAsync()` | Mint a pairing code |
| `ListDevicesAsync()` | Paired devices |
| `RevokeDeviceAsync(deviceId)` | Revoke a device |
| `ScreenTreeAsync()` | Current screen tree |
| `ListAppsAsync()` | Installed apps |
| `SetAllowedAppsAsync(packages)` | Set the app allowlist |
| `RegisterActiveRunAsync(runId)` | Attach the phone to a run |
| `GrantAppAsync(package, label?)` | Grant an app package to the agent allowlist |
| `RequestCastAsync(deviceId?)` | Request the phone start/refresh a screen cast |

### hitl (`m.Hitl`)

| Method | Description |
|--------|-------------|
| `PendingAsync()` | Pending approvals |
| `HistoryAsync(limit?, offset?)` | Decision history |
| `ApproveAsync(requestId, body?)` / `RejectAsync(requestId, body?)` | Decide one request |
| `BulkDecideAsync(body)` | Decide many requests |
| `RunToolStatsAsync(runId)` / `RunToolStatsByAgentAsync(runId)` | Tool-call stats |
| `RunMessagesAsync(runId, limit?, cursor?)` | Run messages |
| `RunToolCallsAsync(runId)` | Run tool calls |

### evals (`m.Evals`)

| Method | Description |
|--------|-------------|
| `ListRunsAsync()` | Eval runs |
| `SummaryAsync()` | Aggregate summary |
| `RunDetailAsync(runId)` | One eval run |
| `CompareAsync(runIds?)` | Compare runs |
| `MemoryGraphAsync()` / `RunMemoryAsync(runId)` / `CrewMemoryAsync(pipeline?, project?)` | Memory graphs |
| `EditCrewMemoryEntryAsync(body)` / `DeleteCrewMemoryEntryAsync(pipeline, entryId, project?)` | Edit/delete a persisted crew-memory entry |
| `BenchmarksAsync()` | Benchmarks |

### connector tools (`m.ConnectorTools`, also `m.Agents.ConnectorTools`)

The same discover/call surface the Melaya MCP server exposes, over plain REST — not to be
confused with `m.Connectors` / `m.Credentials`, which store the credentials this module's
tools use. Reads run immediately. Writes default to `approval: "required"`: the call is
staged as the same approval card the Assistant raises in the Melaya app and returns **202**
(a success, not an error) with a `requestId` to poll; `approval: "none"` runs a write
immediately and is still audit-logged. Tools that move money or trade are always refused,
under both approval modes. No method here ever accepts or returns a credential value.

| Method | Description |
|--------|-------------|
| `ServicesAsync()` | Connected services + per-service read/write tool counts |
| `SearchAsync(q, limit?)` | Discover tools by plain business keywords, ranked by relevance |
| `DescribeAsync(tool)` | One tool's full description + parameters |
| `TestAsync(service)` | Test the stored credential for a service |
| `ConnectAsync(service)` | Start connecting a service (never takes/returns a secret) |
| `CallAsync(tool, args?, approval?)` | Call a tool — 200 `done` for reads/`approval: "none"`, 202 `pending_approval` for a staged write |
| `CallStatusAsync(requestId)` | Outcome of a staged write: `pending` / `running` / `expired` / `done` / `rejected` |
| `CallAndWaitAsync(tool, args?, approval?, pollIntervalMs?, timeoutMs?)` | Calls, then polls a staged write to completion (default poll 3s, timeout 10min) |

```csharp
var found = await m.ConnectorTools.SearchAsync("unread email");

// Staged for approval in the Melaya app, then polled to completion.
var outcome = await m.ConnectorTools.CallAndWaitAsync(
    "gmail_send", new Dictionary<string, object?> { ["to"] = "a@b.c", ["subject"] = "Hi" });
Console.WriteLine(outcome.Status); // "done" | "rejected" | "expired"

// Skips the approval card; still audit-logged. Money-moving/trading tools are refused either way.
var sent = await m.ConnectorTools.CallAsync(
    "gmail_send", new Dictionary<string, object?> { ["to"] = "a@b.c" }, approval: "none");
```

### events (`m.Events`)

Connects lazily on the first subscription — constructing `MelayaClient` opens no socket.

| Method | Description |
|--------|-------------|
| `OnRunUpdate(runId, handler)` | Run events (filtered to that `runId`) |
| `OnInitPhase(runId, handler)` | Init-phase progress |
| `OnProjectEvent(project, handler)` | All events in a project |
| `OnHitlApproval(handler)` | HITL notifications |
| `OnPipelineCreated/Updated/Deleted(project, handler)` | Pipeline CRUD events |
| `LeaveRun(runId)` / `LeaveProject(project)` | Leave a room and remove its listeners |
| `OnError` | Optional connection-error callback |
| `Dispose()` | Close the connection |

### billing (`m.Billing`)

| Method | Description |
|--------|-------------|
| `SubscriptionAsync()` | Subscription status |
| `CreateCheckoutAsync(body)` | Stripe Checkout session |
| `CreatePortalAsync()` | Stripe Customer Portal |
| `PlansAsync()` | Pricing plans |
| `AmbassadorPerkAsync()` | Ambassador-program perk status |
| `RedeemCodeAsync(code)` | Redeem a promo/discount code |
| `ReservedPromoAsync()` | Reserved promotional pricing |

### accounts (`m.AccountManagement`)

| Method | Description |
|--------|-------------|
| `ExportMyDataAsync()` | GDPR data export |
| `UpdateProfileAsync(body)` | Update profile |
| `CreditsAsync()`, `AiCreditsAsync()`, `PortfolioIdeasCreditsAsync()`, `RiskMonitoringCreditsAsync()` | Credit balances |
| `RemoveKeyAsync(keyId)` | Remove a connected CEX key |
| `ResendEmailVerificationAsync()` | Re-send account email verification |
| `VerifyEmailAsync(token)` | Confirm account email from a verification token |

### runner (`m.Runner`)

| Method | Description |
|--------|-------------|
| `CreateTokenAsync(body?)` | Mint a `mel_run_` token (plaintext shown once) |
| `ListTokensAsync()` | Runner tokens (masked) |
| `RevokeTokenAsync(tokenId)` | Revoke a token |

### team (`m.Team`)

| Method | Description |
|--------|-------------|
| `ListMembersAsync(project)` | Project members |
| `InviteAsync(project, username)` | Invite by username |
| `CreateInviteLinkAsync(project)` | Mint an invite link |
| `AcceptInviteAsync(token)` | Accept an invite |
| `UpdateMemberRoleAsync(project, userId, role)` | Change a member role |
| `RemoveMemberAsync(project, userId)` | Remove a member |
| `TransferOwnershipAsync(project, newOwnerUserId)` | Transfer project ownership to another member |
| `GetPipelineVisibilityAsync(project, pipeline)` / `SetPipelineVisibilityAsync(project, pipeline, body)` | Per-pipeline visibility |

### assistant (`m.Assistant`)

| Method | Description |
|--------|-------------|
| `GetProfileAsync()` | Assistant onboarding profile |
| `SetProfileAsync(profile)` | Save the profile |

### bugs (`m.Bugs`)

| Method | Description |
|--------|-------------|
| `CreateAsync(body)` | Submit a bug report |
| `ListMineAsync()` | Your reports |
| `GetAsync(bugId)` | One report |
| `AddCommentAsync(bugId, comment)` | Comment on a report |
| `ListNotificationsAsync()` / `MarkNotificationsReadAsync()` | Notifications |

## Quick start: trading (preview)

> Trading namespaces are preview-only and not generally available. Do not use them with real funds.

```csharp
using Melaya;

var mk = Environment.GetEnvironmentVariable("MK")!;
await using var m = new MelayaClient(new MelayaOptions { ApiKey = mk });

// Market data
var ticker = await m.Market.TickerAsync("binance", "BTC/USDT", "spot");
Console.WriteLine($"BTC last: {ticker.GetProperty("last")}");

// Launch a custom paper strategy
var created = await m.Strategies.CreateAsync(new
{
    name         = "my-rhai-bot",
    strategyType = "custom",
    exchange     = "binance",
    symbol       = "BTC/USDT",
    market       = "spot",
    dryRun       = true,
    @params      = new
    {
        language   = "rhai",
        definition = "fn evaluate() { emit_long(param(\"qty\")); }",
        qty        = 0.001,
    },
});
string sid = created.StrategyId!;
Console.WriteLine($"Strategy: {sid}");

// Sim: virtual balance
var balance = await m.Sim.BalanceAsync(sid);
Console.WriteLine($"Balance: {balance}");

// Backtest (completes in seconds)
var job = await m.Backtest.StartAsync(new
{
    strategyType = "custom",
    language     = "rhai",
    definition   = "fn evaluate() { emit_long(param(\"qty\")); }",
    exchange     = "binance",
    symbol       = "BTC/USDT",
    timeframe    = "1h",
    since_ms     = DateTimeOffset.UtcNow.AddDays(-30).ToUnixTimeMilliseconds(),
    until_ms     = DateTimeOffset.UtcNow.ToUnixTimeMilliseconds(),
    @params      = new { qty = 0.001 },
});
Console.WriteLine($"Backtest job: {job.JobId}");

// Clean up
await m.Strategies.StopAsync(sid);
await m.Strategies.DeleteAsync(sid);
```

## Streaming (preview)

```csharp
// Public ticker stream
await foreach (var frame in m.Stream.TickerAsync("binance", "BTC/USDT", "spot"))
{
    Console.WriteLine(frame);
    break; // take one frame then stop
}

// Private strategy events (mints a short-lived WS ticket)
await foreach (var frame in m.Stream.StrategiesAsync())
{
    Console.WriteLine(frame);
    break;
}
```

## Trading method tables (preview)

### market

| Method | Description |
|--------|-------------|
| `ListExchangesAsync()` | All supported venues |
| `TickerAsync(exchange, symbol, market?)` | Best bid/ask + 24h stats |
| `OrderbookAsync(...)` | Order book to a given depth |
| `OhlcvAsync(...)` | OHLCV candles |
| `TradesAsync(...)` | Recent public trades |
| `MarketsAsync(exchange)` | Tradable markets on a venue |
| `CurrenciesAsync(exchange)` | Listed currencies |
| `StatusAsync(exchange)` | Operational status |
| `TimeAsync(exchange)` | Exchange server time |
| `TickersAsync(exchange, symbols, market?)` | Batch tickers |
| `FundingRatesAsync(exchange, symbols, market?)` | Latest funding rates |
| `FundingRateHistoryAsync(exchange, symbol, hours?, market?)` | Funding-rate history |
| `OpenInterestAsync(exchange, symbols, market?)` | Open interest |
| `OpenInterestHistoryAsync(exchange, symbol, hours?, market?)` | Open-interest history |
| `InstrumentsAsync(exchange, market?)` | Instrument list + constraints |
| `LiquidationEventsAsync(...)` | Historical liquidation events |
| `OhlcvMultiAsync(...)` | Multi-symbol OHLCV |
| `MarketConstraintsAsync(exchange, symbol, market?)` | Trading constraints |
| `FundingRateHistoryMultiAsync(exchanges, symbol, hours?)` | Multi-venue funding history |
| `OpenInterestHistoryMultiAsync(exchanges, symbol, hours?)` | Multi-venue OI history |
| `PredictionMarketsAsync(venue?)` | Prediction market listings |
| `CatalogCountsAsync()` | Platform catalog counts |

### account

| Method | Description |
|--------|-------------|
| `KeysAsync()` | Connected exchange API keys (masked) |
| `UsageAsync()` | Tier, plan limits, live usage counters |
| `ApiKeyStatusAsync()` | Platform key status |

### sim (paper trading)

| Method | Description |
|--------|-------------|
| `ListAccountsAsync()` | Virtual wallets |
| `BalanceAsync(strategyId, asset?)` | Virtual balance |
| `PositionsAsync(strategyId)` | Open positions |
| `OpenOrdersAsync(strategyId)` | Resting orders |
| `MyTradesAsync(strategyId)` | Filled trades |
| `CreateOrderAsync(...)` | Place a paper order |
| `CancelOrderAsync(...)` | Cancel a resting order |

### strategies

| Method | Description |
|--------|-------------|
| `ListAsync()` | All your strategies |
| `GetAsync(strategyId)` | Single strategy |
| `CreateAsync(body)` | Launch a strategy |
| `PauseAsync(strategyId)` | Pause |
| `ResumeAsync(strategyId)` | Resume |
| `StopAsync(strategyId)` | Stop + tear down |
| `DeleteAsync(strategyId)` | Soft-delete |
| `UpdateParamsAsync(strategyId, params)` | Update params |
| `StatusAsync(strategyId)` | Runtime status |
| `PerformanceAsync(strategyId)` | Performance series |
| `ExecutionsAsync(strategyId)` | Execution rows |
| `TradesAsync(strategyId)` | Fill rows |
| `LogsAsync(strategyId)` | Log rows |
| `AiOptStartAsync(...)` | Start AI optimizer |
| `AiOptStatusAsync(strategyId)` | Optimizer status |
| `AiOptApproveAsync(strategyId)` | Apply optimizer params |
| `AiOptStopAsync(strategyId)` | Stop optimizer |
| `AiOptRunsAsync(strategyId)` | Past optimizer runs |

### backtest

| Method | Description |
|--------|-------------|
| `StartAsync(body)` | Start a backtest |
| `JobAsync(jobId)` | Job status + progress |
| `ResultsAsync(jobId)` | Metrics + equity curve |
| `TradesAsync(jobId, limit?, offset?)` | Trade list |
| `SweepAsync(parentId, objective?, limit?)` | Sweep rankings |
| `ListAsync(limit?, offset?)` | Your jobs |
| `FavoritesAsync(limit?, offset?)` | Favorited jobs |
| `FundingRangeAsync(exchange, symbol)` | Earliest funding-rate ms |
| `CancelAsync(jobId)` | Cancel in-flight job |
| `DeleteAsync(jobId)` | Delete a job |
| `DeleteAllAsync()` | Delete all non-favorited |

### stream

| Method | Description |
|--------|-------------|
| `TickerAsync(exchange, symbol, market?)` | Live ticker frames |
| `OrderbookAsync(exchange, symbol, ...)` | Live order-book frames |
| `OhlcvAsync(exchange, symbol, timeframe, ...)` | Live OHLCV frames |
| `TradesAsync(exchange, symbol, ...)` | Live public-trade frames |
| `LiquidationsAsync(exchange?)` | Liquidation firehose |
| `StrategiesAsync()` | Private strategy events |
| `PrivateAsync(exchange, ...)` | Private account feed |
