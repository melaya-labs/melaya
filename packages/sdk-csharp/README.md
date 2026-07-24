# Melaya .NET SDK

> **Current production scope:** Agent Builder and Mobile Device Control are available now. Melaya Trading namespaces are preview-only and not generally available; do not use them with real funds.

Official SDK for the **[Melaya](https://melaya.org)** Agent Builder and flagship Mobile Device Control APIs. Trading namespaces are included only as a preview of a later product.
Normalized market data, paper/live strategies, backtesting, and WebSocket streaming
across 70+ venues — with zero external NuGet dependencies.

## Installation

```xml
<!-- In your .csproj, once the package is published to NuGet -->
<PackageReference Include="Melaya.SDK" Version="0.2.0" />
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

// 2. Create a pipeline (extra config fields go through Config extension data)
await m.Pipelines.CreateAsync(new PipelineCreateRequest
{
    Name    = "mobile-review",
    Project = "Operations",
    Config  = new Dictionary<string, JsonElement>
    {
        ["model_provider"] = JsonSerializer.SerializeToElement("anthropic"),
        ["model_name"]     = JsonSerializer.SerializeToElement("claude-sonnet-4-6"),
        ["agents"]         = JsonSerializer.SerializeToElement(new[]
        {
            new
            {
                name        = "mobile-operator",
                role        = "Careful mobile operator",
                instruction = "Read before acting. Never send, publish, or delete.",
                agent_tools = new[]
                {
                    "phone_get_screen_tree",
                    "phone_current_app",
                    "phone_open_app",
                    "phone_click_text",
                    "phone_back",
                    "phone_wait",
                },
            },
        }),
        ["steps"]      = JsonSerializer.SerializeToElement(new[]
        {
            new { kind = "agent", agent = new { name = "mobile-operator" } },
        }),
        ["maxCostUsd"] = JsonSerializer.SerializeToElement(1.00),
    },
});

// 3. Run it, hand the phone to the run, and watch status
var run = await m.Pipelines.RunAsync("mobile-review", new PipelineRunRequest { Project = "Operations" });
await m.Phone.RegisterActiveRunAsync(run.RunId!);

m.Events.OnRunUpdate(run.RunId!, e => Console.WriteLine($"{e.EventType}: {e.Message}"));

var status = await m.Pipelines.RunStatusAsync("mobile-review", run.RunId!);
Console.WriteLine(status.Status);
```

The platform catalogs currently expose **1,500+ scoped tools**, **100+ specialized
subagents**, and **20+ model providers** (`m.Pipelines.ToolsAsync()`,
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

### pipelines (`m.Pipelines`)

| Method | Description |
|--------|-------------|
| `ListPipelinesAsync()` | Pipeline definitions |
| `CreateAsync(body)` | Create a pipeline |
| `GetAsync(name, project?)` | One pipeline definition |
| `UpdateAsync(name, body)` | Update a pipeline |
| `DeleteAsync(name, project?)` | Delete a pipeline |
| `RunAsync(name, body?)` | Enqueue a run |
| `RunIdsAsync(name)` | Run IDs for a pipeline |
| `RunStatusAsync(name, runId)` | Status + cost of a run |
| `CancelRunAsync(name, runId)` | Cancel a run |
| `OutputsAsync(name)` / `OutputAsync(name, path, download?)` | Run artifacts |
| `ToolsAsync()` | Scoped tool catalog (1,500+ tools) |
| `SubagentsAsync()` | Subagent catalog (100+ subagents) |
| `InstantiateTemplateAsync(templateId, body)` | Instantiate a template |
| `PreviewCodeAsync(body)` / `BuildWithAIAsync(body)` | Authoring helpers |
| `OverviewAsync()`, `CountAsync()`, `ListAsync(...)`, `RecentAsync()` | Run overview |
| `TracesAsync(runId)`, `TraceAsync(runId, traceId)`, `TraceStatsAsync(runId, traceId)`, `DeleteTracesAsync(runId)` | Traces |
| `ListSchedulesAsync()`, `GetScheduleAsync(project, name)`, `UpsertScheduleAsync(...)`, `PauseScheduleAsync(...)`, `ResumeScheduleAsync(...)` | Cron schedules |
| `UsageSummaryAsync()`, `ModelPricesAsync()`, `ChartDataAsync()`, `CostBreakdownAsync()` | Cost dashboard |

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
| `BenchmarksAsync()` | Benchmarks |

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

### accounts (`m.AccountManagement`)

| Method | Description |
|--------|-------------|
| `ExportMyDataAsync()` | GDPR data export |
| `UpdateProfileAsync(body)` | Update profile |
| `CreditsAsync()`, `AiCreditsAsync()`, `PortfolioIdeasCreditsAsync()`, `RiskMonitoringCreditsAsync()` | Credit balances |
| `RemoveKeyAsync(keyId)` | Remove a connected CEX key |

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
