# Melaya PHP SDK v0.3.0

> **Product status:** Agent Builder and Mobile Device Control are the current public products. Melaya Trading namespaces are preview-only and planned for later public release; do not use them with real funds.

Official PHP SDK for the [Melaya](https://melaya.org) agent-builder and Device Control platform.
Three planes: agents (pipelines, HITL, phone device control, assistant, evals, models, connector tools), platform (projects, credentials, connectors, team, billing, templates, runner, auth), and trading (market data, strategies, backtesting, streaming; trading coming later under Melaya Labs).

**Melaya products:** [Melaya Agents](https://melaya.org/en/product/agentic-framework) · [Melaya Assistant](https://melaya.org/en/product/assistant) · [Device Control](https://melaya.org/en/product/agentic-device-control) · [Browser Control](https://melaya.org/en/product/agentic-browser-control) · [MCP Server](https://melaya.org/en/product/mcp) · [Melaya Marketing](https://melaya.org/en/product/marketing)

## Requirements

- PHP 8.1+
- `curl`, `json`, `openssl` extensions (bundled with most PHP distributions)

## Installation

```bash
composer require melaya/sdk
```

Without Composer (quick scripts):

```php
require_once '/path/to/sdk-php/autoload.php';
```

## Authentication

Two auth modes are supported:

### Platform API key (recommended)

Create an API key at [melaya.org → Settings → API Keys](https://melaya.org).
Keys are prefixed `mk_`.

```php
$m = new Melaya\Melaya(apiKey: getenv('MK'));
```

### Session JWT

Exchange credentials for a JWT via `auth->login()`, then pass the token:

```php
$session = (new Melaya\Melaya(apiKey: getenv('MK')))->auth->login('alice', 'secret');
$m = new Melaya\Melaya(jwt: $session['token']);
```

**Security:** Never hard-code keys or JWTs. Read from environment variables.
The SDK never logs credentials.

**Wire format:** REST requests send the credential **only** as an
`Authorization: Bearer mk_...` header — never in the URL query string. Public
market-data WebSocket streams authenticate with `?apiKey=` in the `wss://` URL
(server protocol); private WebSocket streams use a short-lived `?wsTicket=`
minted via `POST /api/v1/private/private-ticket`.

---

## Namespace Structure

The client exposes three top-level namespace objects that group related modules
so the domain structure is immediately visible:

```
$m->trading   — market, account, sim, strategies, trade, backtest, stream, optimize
$m->agents    — pipelines, hitl, assistant, phone, evals, connectorTools
$m->platform  — projects, credentials, connectors, billing, team, templates,
                 overview, runner, auth, accounts, bugs, events
```

All flat module properties (`$m->market`, `$m->hitl`, …) remain available as
**aliases** pointing to the same object instances — existing code continues to
work unchanged.

---

## Quick Start

### Agent Builder & Device Control (GA)

Pipelines draw from catalogs of 1,500+ scoped tools, 100+ specialized
subagents, and 20+ model providers.

Pair a phone:

```php
require_once 'vendor/autoload.php';
use Melaya\Melaya;

$m = new Melaya(apiKey: getenv('MK'));

$pair = $m->agents->phone->pair();
echo $pair['code'], ' (expires in ', $pair['expiresInSeconds'] ?? '?', "s)\n";

$devices = $m->agents->phone->listDevices();
$apps    = $m->agents->phone->listApps();

$m->agents->phone->setAllowedApps(['com.android.chrome']);
$m->agents->phone->grantApp('com.instagram.android', 'Instagram'); // atomic append, not a replace
$m->agents->phone->requestCast(); // re-trigger MediaProjection consent
```

Create and run an agent pipeline. Configure provider credentials through
Melaya Connectors first — never put a provider key in pipeline configuration
or per-run overrides:

```php
// The run is generated ONLY from `steps[]` — a top-level `agents[]` array with
// no matching `steps[]` entry produces an EMPTY pipeline. Embed the full agent
// object inside its step, as below. There is no `prompt` field: the task goes
// in `instruction`, and an optional `system_prompt_override` replaces the
// agent's default system prompt entirely.
$m->agents->pipelines->create([
    'name'       => 'mobile-review',
    'project'    => 'Operations',
    'steps'      => [[
        'kind'  => 'agent',
        'agent' => [
            'name'        => 'mobile-operator',
            'role'        => 'Careful mobile operator',
            'instruction' => 'Read before acting. Never send, publish, or delete.',
            'model'       => ['provider' => 'anthropic', 'name' => 'claude-sonnet-4-6'],
            'agent_tools' => [
                'phone_get_screen_tree',
                'phone_current_app',
                'phone_open_app',
                'phone_click_text',
                'phone_back',
                'phone_wait',
            ],
            'human_approval_tools' => [],
        ],
    ]],
    'maxCostUsd' => 1.00,
]);

$run = $m->agents->pipelines->run('mobile-review', ['project' => 'Operations']);

$m->agents->phone->registerActiveRun($run['run_id']);

$status = $m->agents->pipelines->runStatus('mobile-review', $run['run_id']);
```

To edit an existing pipeline, `getPipeline()` returns an ENVELOPE
`{ name, client, config, code, docs }` — mutate `envelope['config']` and pass
THAT (not the envelope) to `update()`:

```php
$envelope = $m->agents->pipelines->getPipeline('mobile-review', 'Operations');
$config = $envelope['config'];
$config['steps'][0]['agent']['model'] = ['provider' => 'anthropic', 'name' => 'claude-opus-4-8'];
$m->agents->pipelines->update('mobile-review', ['config' => $config, 'project' => 'Operations']);
```

Other config fields worth knowing: `hitl_mode` (`"safe"` default | `"autonomous"`
| `"payments_only"` — only `"safe"` honours each agent's `human_approval_tools`),
`connector_source` (`"personal"` | `"project"`), `force_local_runner`, and
`inputs[]` (declares the run-time inputs a run accepts — see `run()` and
`uploadRunFile()` below).

#### GA API surface

| GA namespace | PHP accessor | Notes |
|---|---|---|
| auth | `$m->platform->auth` / `$m->auth` | Login, session, JWT rotation |
| mfa | `$m->auth->mfaStatus()` / `mfaSetup()` / `mfaConfirm($code)` | TOTP enrollment (part of auth) |
| projects | `$m->platform->projects` / `$m->projects` | Project CRUD |
| connectors | `$m->platform->connectors` / `$m->connectors` | Project-scoped encrypted credentials |
| credentials | `$m->platform->credentials` / `$m->credentials` | User-scoped credentials, models, RAG/OAuth flows |
| pipelines | `$m->agents->pipelines` / `$m->pipelines` | Lifecycle, runs, traces, schedules, outputs |
| templates | `$m->platform->templates` / `$m->templates` | Save, share, assign pipeline templates |
| phone | `$m->agents->phone` / `$m->phone` | Device pairing and control management |
| hitl | `$m->agents->hitl` / `$m->hitl` | Human-in-the-loop approvals |
| evals | `$m->agents->evals` / `$m->evals` | Evaluation runs and comparisons |
| events | `$m->platform->events` / `$m->events` | Real-time Socket.IO events (polling) |
| billing | `$m->platform->billing` / `$m->billing` | Subscription, checkout, plans |
| accounts | `$m->platform->accounts` / `$m->accounts` | Credits, profile, GDPR export |
| runner | `$m->platform->runner` / `$m->runner` | Local-runner token management |
| team | `$m->platform->team` / `$m->team` | Members, roles, invites |
| assistant | `$m->agents->assistant` / `$m->assistant` | Assistant onboarding profile |
| connectorTools | `$m->agents->connectorTools` / `$m->connectorTools` | Discover and call connector tools (Gmail, Slack, Stripe, …) |
| bugs | `$m->platform->bugs` / `$m->bugs` | Bug reports and notifications |

Full method tables are in the [Module Reference](#module-reference) below.

### Trading namespace (preview — not GA)

> Melaya Trading namespaces are preview-only and planned for later public
> release; do not use them with real funds.

```php
require_once 'vendor/autoload.php';
use Melaya\Melaya;

$m = new Melaya(apiKey: getenv('MK'));

// Market data
$ticker  = $m->trading->market->ticker('binance', 'BTC/USDT', 'spot');
echo $ticker['last'];

$candles = $m->trading->market->ohlcv('binance', 'BTC/USDT', '1h', 100, 'spot');
$mdd     = $m->trading->market->mddPairs();
$yields  = $m->trading->market->onchainYields();

// Strategies
$r = $m->trading->strategies->create([
    'name'         => 'my-paper-bot',
    'strategyType' => 'custom',
    'exchange'     => 'binance',
    'symbol'       => 'BTC/USDT',
    'market'       => 'spot',
    'dryRun'       => true,
    'params'       => [
        'language'   => 'rhai',
        'definition' => 'fn evaluate() { emit_long(param("qty")); }',
        'qty'        => 0.001,
    ],
]);
$summaries = $m->trading->strategies->summariesBulk([$r['strategyId']]);

// Backtest
$job = $m->trading->backtest->start([
    'strategyType' => 'custom',
    'language'     => 'rhai',
    'definition'   => 'fn evaluate() { emit_long(param("qty")); }',
    'exchange'     => 'binance',
    'symbol'       => 'BTC/USDT',
    'timeframe'    => '1h',
    'since_ms'     => (int)(microtime(true) * 1000) - 90 * 24 * 3600 * 1000,
    'until_ms'     => (int)(microtime(true) * 1000),
    'params'       => ['qty' => 0.001],
]);
do {
    sleep(3);
    $j = $m->trading->backtest->job($job['job_id']);
} while (!in_array($j['status'], ['completed', 'done', 'finished']));
$results = $m->trading->backtest->results($job['job_id']);

// Parameter sweep optimizer
$opt = $m->trading->optimize->start([
    'strategyId'  => $r['strategyId'],
    'method'      => 'genetic',
    'paramBounds' => ['qty' => [0.001, 0.1]],
]);

// WebSocket stream
$ws = $m->trading->stream->ticker('binance', 'BTC/USDT', 'spot');
for ($i = 0; $i < 5; $i++) {
    $frame = $ws->readFrame();
    if ($frame) print_r($frame);
}
$ws->close();
```

### Agents namespace

```php
// Pipeline runs
$runs   = $m->agents->pipelines->list(['project' => 'my-agent-project', 'limit' => 20]);
$recent = $m->agents->pipelines->recent();
$traces = $m->agents->pipelines->traces($runId);

// Run inputs — a brief plus values, including an uploaded file
$upload = $m->agents->pipelines->uploadRunFile('mobile-review', 'report', file_get_contents('report.pdf'), [
    'filename' => 'report.pdf', 'contentType' => 'application/pdf',
]);
$run = $m->agents->pipelines->run('mobile-review', [
    'project'     => 'my-agent-project',
    'run_inputs'  => ['brief' => 'Summarize this report', 'values' => ['report' => ['file_id' => $upload['file_id']]]],
]);
$inputs = $m->agents->pipelines->runInputs('mobile-review', $run['run_id']);
$active = $m->agents->pipelines->runActive('mobile-review', $run['run_id']);

// Static-context and retrieval (RAG) documents
$m->agents->pipelines->uploadDoc('mobile-review', file_get_contents('policy.md'), ['filename' => 'policy.md']);
$docs = $m->agents->pipelines->listDocs('mobile-review');
$m->agents->pipelines->uploadRetrievalDoc('mobile-review', file_get_contents('kb.pdf'), ['filename' => 'kb.pdf']);
$m->agents->pipelines->ingestRetrieval('mobile-review');

// Tool-call audit
$calls  = $m->agents->pipelines->projectToolCalls('my-agent-project', ['limit' => 30]);
$facets = $m->agents->pipelines->projectToolCallFacets('my-agent-project');

// Cron schedules
$m->agents->pipelines->upsertSchedule('my-project', 'nightly-report', ['cron' => '0 2 * * *']);
$m->agents->pipelines->pauseSchedule('my-project', 'nightly-report');

// HITL — Human-in-the-Loop approvals
$pending = $m->agents->hitl->pending();
foreach ($pending as $req) {
    echo $req['toolName'], ': ', json_encode($req['toolInput']), PHP_EOL;
    $m->agents->hitl->approve($req['requestId'], ['comment' => 'Looks good']);
}
$m->agents->hitl->bulkDecide([
    'requestIds' => ['r1', 'r2'],
    'decision'   => 'rejected',
    'comment'    => 'Not authorized',
]);

// Assistant profile
$profile = $m->agents->assistant->getProfile();
$m->agents->assistant->setProfile([
    'name'  => 'Antoine',
    'goals' => ['Grow my derivatives edge', 'Automate research'],
]);

// Phone device control
$pair    = $m->agents->phone->pair();
$devices = $m->agents->phone->listDevices();
$tree    = $m->agents->phone->screenTree();
$m->agents->phone->setAllowedApps(['com.twitter.android', 'com.instagram.android']);

// Evals
$runs    = $m->agents->evals->listRuns();
$summary = $m->agents->evals->summary();
$detail  = $m->agents->evals->runDetail($runId);
$compare = $m->agents->evals->compare(['runIds' => [$runId1, $runId2]]);

// Connector tools — call the same tools the Assistant and MCP server expose
$services = $m->agents->connectorTools->services();
$found    = $m->agents->connectorTools->search('unread email', 5);
$info     = $m->agents->connectorTools->describe('gmail_list_messages');

// Reads run immediately.
$result = $m->agents->connectorTools->call('gmail_list_messages');

// Writes default to an approval card in the Melaya app (approval: "required").
// call() returns the 202 body — that IS success, not an error.
$staged  = $m->agents->connectorTools->call('gmail_send', ['to' => 'a@b.com']);
$outcome = $m->agents->connectorTools->callStatus($staged['requestId']);

// Or block until the approval is decided (or the timeout elapses):
$outcome = $m->agents->connectorTools->callAndWait('gmail_send', ['to' => 'a@b.com']);

// approval: "none" skips the card and runs immediately (still audit-logged).
// Tools that move money or trade are refused under BOTH approval modes.
$m->agents->connectorTools->call('gmail_send', ['to' => 'a@b.com'], 'none');
```

### Platform namespace

```php
// Auth
$session = $m->platform->auth->login('alice', 'password123');
$me      = $m->platform->auth->me();
$setup   = $m->platform->auth->mfaSetup();
$m->platform->auth->mfaConfirm('123456');

// Platform accounts (credits, profile, GDPR)
$credits   = $m->platform->accounts->credits();
$aiCredits = $m->platform->accounts->aiCredits();
$export    = $m->platform->accounts->exportMyData();
$m->platform->accounts->resendEmailVerification();
$m->platform->accounts->verifyEmail($token);

// Billing
$sub      = $m->platform->billing->subscription();
$checkout = $m->platform->billing->createCheckout(['tier' => 'bastion']);
$plans    = $m->platform->billing->plans();
$perk     = $m->platform->billing->ambassadorPerk();
$m->platform->billing->redeemCode('WELCOME10');

// Projects
$projects = $m->platform->projects->list();
$project  = $m->platform->projects->create(['name' => 'my-agent-project']);
$m->platform->projects->rename('old-name', 'new-name');

// Overview dashboard
$overview = $m->platform->overview->get();
$costs    = $m->platform->overview->costBreakdown();

// Runner tokens
$result = $m->platform->runner->createToken(['label' => 'prod-server-1']);
$tokens = $m->platform->runner->listTokens();
$m->platform->runner->revokeToken($tokens[0]['id']);

// User-scoped credentials
$m->platform->credentials->set('openai', ['value' => 'sk-...', 'label' => 'OpenAI prod']);
$m->platform->credentials->test('openai');
$models = $m->platform->credentials->listModels(['provider' => 'anthropic']);
$google = $m->platform->credentials->googleStatus();
$m->platform->credentials->googleSetDefault('gmail', $accountId);
$m->platform->credentials->googleDisconnect($accountId); // whole account
$session = $m->platform->credentials->dbTestStart('postgres', ['host' => '10.0.0.5', 'port' => 5432]);
$m->platform->credentials->dbTestStatus($session['sessionId']);

// Project connectors — sharing personal creds into a project pool
$m->platform->connectors->set('my-project', 'openai', ['value' => 'sk-...']);
$handle = $m->platform->connectors->envHandle('my-project');
$m->platform->connectors->applyPersonal('my-project', 'openai');
$sharedBy = $m->platform->connectors->sharedBy('my-project');

// Team
$members = $m->platform->team->listMembers('my-project');
$m->platform->team->invite('my-project', 'alice');
$link = $m->platform->team->createInviteLink('my-project');
$m->platform->team->updateMemberRole('my-project', $userId, 'editor');
$m->platform->team->transferOwnership('my-project', $newOwnerUserId);

// Templates
$templates = $m->platform->templates->listGlobal();
$t = $m->platform->templates->save([
    'name'    => 'Daily report',
    'payload' => ['pipeline' => [], 'params' => []],
]);
$m->platform->templates->share($t['id'], 'team');

// Bug reports
$bug = $m->platform->bugs->create(['title' => 'Pipeline stuck on init', 'description' => '...']);
$m->platform->bugs->addComment($bug['id'], 'Still happening on v2.3');
$mine = $m->platform->bugs->listMine();

// Real-time events (Socket.IO / Engine.IO polling)
$events = $m->platform->events;
$events->connect();

$events->onRunUpdate('run-123', function(array $e) {
    echo $e['event_type'] ?? '', ': ', $e['status'] ?? '', PHP_EOL;
});
$events->onHitlApproval(function(array $e) {
    echo 'HITL: ', $e['type'], ' — requestId: ', $e['requestId'] ?? '', PHP_EOL;
});
$events->onProjectEvent('my-project', function(array $e) {
    echo 'Project event: ', $e['event_type'] ?? '', PHP_EOL;
});

for ($i = 0; $i < 60; $i++) {
    $events->poll();
}
// Or: $events->listen(maxEvents: 100, totalSec: 300);
$events->close();
```

**Note:** For high-throughput event ingestion in production PHP, consider
using ReactPHP or Swoole for async I/O, or delegate real-time subscriptions
to a queue worker backed by the WebSocket feed.

---

## Flat aliases (backwards-compatible)

All module properties are also available directly on `$m` for backwards
compatibility. The flat and namespaced accessors point to the **same object**:

```php
// Equivalent — same object instance:
$m->trading->market->ticker(...);
$m->market->ticker(...);

$m->agents->pipelines->list(...);
$m->pipelines->list(...);

$m->platform->projects->list(...);
$m->projects->list(...);
```

## Module Reference

### `$m->market`

| Method | Auth | Description |
|---|---|---|
| `listExchanges()` | mk_* | List supported exchanges |
| `ticker($exchange, $symbol, $market)` | mk_* | Best bid/ask, last, 24h aggregates |
| `orderbook($exchange, $symbol, $limit, $market)` | mk_* | Order book |
| `ohlcv($exchange, $symbol, $timeframe, $limit, $market)` | mk_* | OHLCV candles |
| `trades($exchange, $symbol, $market)` | mk_* | Recent public trades |
| `markets($exchange)` | mk_* | Tradable markets |
| `currencies($exchange)` | mk_* | Listed currencies |
| `status($exchange)` | mk_* | Operational status |
| `time($exchange)` | mk_* | Exchange server time |
| `tickers($exchange, $symbols, $market)` | mk_* | Batch tickers |
| `fundingRates($exchange, $symbols, $market)` | mk_* | Latest funding rates |
| `fundingRateHistory($exchange, $symbol, $hours, $market)` | mk_* | Funding history |
| `openInterest($exchange, $symbols, $market)` | mk_* | Open interest |
| `openInterestHistory($exchange, $symbol, $hours, $market)` | mk_* | OI history |
| `instruments($exchange, $market)` | mk_* | Instrument list |
| `liquidationEvents($exchange, $symbol, $sinceMs, $limit)` | mk_* | Liquidation events |
| `ohlcvMulti($exchange, $symbols, $timeframe, $limit, $market)` | mk_* | Multi-symbol OHLCV |
| `marketConstraints($exchange, $symbol, $market)` | mk_* | Trading constraints |
| `fundingRateHistoryMulti($exchanges, $symbol, $hours)` | mk_* | Multi-venue funding |
| `openInterestHistoryMulti($exchanges, $symbol, $hours)` | mk_* | Multi-venue OI |
| `predictionMarkets($venue)` | mk_* | Prediction markets |
| `catalogCounts()` | public | Platform catalog counts |
| `cexLiquidations($body)` | auth | Aggregated CEX liquidations |
| `mddPairs()` | public | Max-drawdown screener |
| `onchainYields()` | auth | On-chain yield data (Forge+) |
| `onchainLiquidity()` | auth | On-chain liquidity data (Forge+) |
| `banner()` | public | Marketing/notification banner |
| `priceHistory($params)` | public | Price history for charts |

### `$m->auth`

| Method | Auth | Description |
|---|---|---|
| `login($username, $password)` | public | Login, returns JWT |
| `verifyMfa($challengeToken, $code)` | public | Resolve MFA challenge |
| `register($username, $email, $password)` | public | Register account |
| `verifySignup($token)` | public | Confirm email address |
| `resendVerification($email)` | public | Re-send verification email |
| `forgotPassword($email)` | public | Initiate password reset |
| `resetPassword($token, $newPassword)` | public | Complete password reset |
| `me()` | auth | Current user profile |
| `check()` | auth | Session validity check |
| `changePassword($current, $new)` | auth | Change password |
| `createMobileHandoff()` | auth | Mobile app deep-link token |
| `permissions()` | auth | Caller's permission flags |
| `refresh()` | auth | Rotate JWT |
| `mfaStatus()` | auth | MFA enrollment status |
| `mfaSetup()` | auth | Initiate TOTP setup |
| `mfaConfirm($code)` | auth | Confirm TOTP setup |
| `version()` | public | Server version string |

### `$m->accounts`

| Method | Auth | Description |
|---|---|---|
| `exportMyData()` | auth | GDPR data export |
| `removeKey($keyId)` | auth | Remove a CEX API key |
| `updateProfile($body)` | auth | Update display name / avatar |
| `credits()` | auth | Credit balance + history |
| `aiCredits()` | auth | AI/LLM credit balance |
| `portfolioIdeasCredits()` | auth | Portfolio-ideas credit balance |
| `riskMonitoringCredits()` | auth | Risk-monitoring credit balance |
| `resendEmailVerification()` | auth | Re-send saved-email verification |
| `verifyEmail($token)` | auth | Confirm saved email ownership |

### `$m->billing`

| Method | Auth | Description |
|---|---|---|
| `subscription()` | auth | Stripe subscription status |
| `createCheckout($body)` | auth | Create Stripe Checkout session |
| `createPortal()` | auth | Create Stripe Portal session |
| `plans()` | public | Public pricing plan details |
| `ambassadorPerk()` | auth | Caller's ambassador discount, or null |
| `redeemCode($code)` | auth | Redeem a single-use promo code |
| `reservedPromo()` | auth | Caller's active reserved promo, or null |

### `$m->runner`

| Method | Auth | Description |
|---|---|---|
| `createToken($body)` | auth | Mint a new mel_run_ token |
| `listTokens()` | auth | List runner tokens (masked) |
| `revokeToken($tokenId)` | auth | Revoke a runner token |

### `$m->strategies`

| Method | Auth | Description |
|---|---|---|
| `list()` | mk_* | All strategies |
| `get($id)` | mk_* | Single strategy |
| `create($body)` | mk_* | Launch a strategy |
| `pause($id)` | mk_* | Pause |
| `resume($id)` | mk_* | Resume |
| `stop($id)` | mk_* | Stop |
| `delete($id)` | mk_* | Soft-delete |
| `updateParams($id, $params)` | mk_* | Update params |
| `status($id)` | mk_* | Runtime status |
| `performance($id)` | mk_* | Performance series |
| `executions($id)` | mk_* | Execution rows |
| `trades($id)` | mk_* | Trade rows |
| `logs($id)` | mk_* | Log rows |
| `aiOptStart($id, $paramBounds, ...)` | mk_* | Start AI optimizer |
| `aiOptStatus($id)` | mk_* | Optimizer status |
| `aiOptApprove($id, $body)` | mk_* | Approve proposed params |
| `aiOptStop($id)` | mk_* | Stop optimizer |
| `aiOptRuns($id)` | mk_* | Past optimizer runs |
| `listTeam()` | auth | Team-visible strategies |
| `summariesBulk($strategyIds)` | auth | Bulk summaries |

### `$m->backtest`

| Method | Auth | Description |
|---|---|---|
| `start($body)` | mk_* | Start a backtest |
| `job($jobId)` | mk_* | Job status |
| `results($jobId)` | mk_* | Metrics + equity curve |
| `trades($jobId, $limit, $offset)` | mk_* | Trade list |
| `sweep($parentId, ...)` | mk_* | Sweep results |
| `list($limit, $offset)` | mk_* | All jobs |
| `favorites($limit, $offset)` | mk_* | Favorited jobs |
| `fundingRange($exchange, $symbol)` | mk_* | Earliest funding timestamp |
| `cancel($jobId)` | mk_* | Cancel job |
| `delete($jobId)` | mk_* | Delete job |
| `deleteAll()` | mk_* | Delete all non-favorited jobs |

### `$m->optimize`

| Method | Auth | Description |
|---|---|---|
| `start($body)` | auth | Start parameter sweep |
| `list()` | auth | List sweep runs |
| `cancel($optRunId)` | auth | Cancel sweep |
| `apply($optRunId, $body)` | auth | Apply best params to strategy |

### `$m->projects`

| Method | Auth | Description |
|---|---|---|
| `list()` | auth | List accessible projects |
| `create($body)` | auth | Create a project |
| `rename($oldName, $newName)` | auth | Rename a project |
| `runnerProjects()` | auth | Runner-facing project list |

### `$m->pipelines`

| Method | Auth | Description |
|---|---|---|
| `listPipelines()` | auth | List all pipelines |
| `create($body)` | auth | Create a pipeline |
| `getPipeline($name, $project)` | auth | Get a pipeline config |
| `update($name, $body)` | auth | Update a pipeline |
| `deletePipeline($name, $project)` | auth | Delete a pipeline |
| `run($name, $body)` | auth | Trigger a run (accepts `run_inputs`) |
| `uploadRunFile($name, $key, $file, $opts)` | auth | Upload a file for a later run |
| `runIds($name)` | auth | List run IDs |
| `runStatus($name, $runId)` | auth | Run status |
| `cancelRun($name, $runId)` | auth | Cancel a run |
| `runActive($name, $runId)` | auth | Poll whether a run is still active |
| `runInputs($name, $runId)` | auth | What a run was started with |
| `runInputFile($name, $runId, $index)` | auth | Download one run-input file (raw bytes) |
| `outputs($name)` | auth | List output artifacts |
| `output($name, $path, $download)` | auth | Get/download an artifact |
| `previewCode($config)` | auth | Preview generated code |
| `tools()` | auth | Tool registry (1,500+ scoped tools) |
| `subagents()` | auth | Subagent registry (100+ specialized subagents) |
| `instantiateTemplate($templateId, $body)` | auth | Instantiate a template |
| `buildWithAI($brief)` | auth | AI-assisted pipeline generation |
| `listDocs($name)` | auth | List static-context documents |
| `uploadDoc($name, $file, $opts)` | auth | Upload a static-context document |
| `deleteDoc($name, $filename)` | auth | Delete a static-context document |
| `uploadRetrievalDoc($name, $file, $opts)` | auth | Upload a RAG (retrieval) document |
| `ingestRetrieval($name, $body)` | auth | Embed changed retrieval documents |
| `deleteRetrievalDoc($name, $filename)` | auth | Delete a retrieval document |
| `list($params)` | auth | Paginated run list |
| `count()` | auth | Count runs by status |
| `recent()` | auth | Most recent runs |
| `traces($runId)` | auth | List traces for a run |
| `trace($runId, $traceId)` | auth | Single trace |
| `traceStats($runId, $traceId)` | auth | Trace statistics |
| `deleteTraces($runId)` | auth | Delete traces |
| `projectToolCalls($project, $params)` | auth | Project tool-call audit ledger |
| `projectToolCallFacets($project)` | auth | Tool/agent facets for the audit filters |
| `toolCallDetail($runId, $spanId)` | auth | Full untruncated tool-call span |
| `listSchedules()` | auth | List all schedules |
| `getSchedule($project, $pipeline)` | auth | Get schedule |
| `upsertSchedule($project, $pipeline, $body)` | auth | Create/update schedule |
| `pauseSchedule($project, $pipeline)` | auth | Pause schedule |
| `resumeSchedule($project, $pipeline)` | auth | Resume schedule |

### `$m->overview`

| Method | Auth | Description |
|---|---|---|
| `get()` | auth | Dashboard overview summary |
| `modelPrices()` | auth | AI model pricing |
| `chartData()` | auth | Cost/usage chart data |
| `costBreakdown()` | auth | Cost breakdown by model |
| `pipelineCount()` | auth | Pipeline count by status |
| `pipelineList($params)` | auth | Paginated pipeline runs |
| `recentPipelines()` | auth | Recent runs widget |

### `$m->hitl`

| Method | Auth | Description |
|---|---|---|
| `pending()` | auth | List pending approvals |
| `history($params)` | auth | Approval history |
| `approve($requestId, $params)` | auth | Approve a request |
| `reject($requestId, $params)` | auth | Reject a request |
| `bulkDecide($body)` | auth | Bulk approve/reject |
| `runToolStats($runId)` | auth | Tool call stats for run |
| `runMessages($runId, $params)` | auth | Paginated messages |
| `runToolStatsByAgent($runId)` | auth | Stats by agent |
| `runToolCalls($runId)` | auth | All tool calls |

### `$m->credentials`

| Method | Auth | Description |
|---|---|---|
| `list()` | auth | List all credentials |
| `connectedServices()` | auth | List connected services |
| `get($service, $key)` | auth | Get a credential |
| `set($service, $body)` | auth | Store/update a credential |
| `delete($service)` | auth | Delete a credential |
| `test($service)` | auth | Test a credential |
| `getOperatorProfile()` | auth | Get operator profile |
| `setOperatorProfile($profile)` | auth | Save operator profile |
| `listModels($params)` | auth | List AI models |
| `melayaAccounts()` | auth | List sub-accounts |
| `ragIngestStart($body)` | auth | Start RAG ingest |
| `ragIngestStatus($sessionId)` | auth | Poll RAG ingest |
| `ragRetrieveStart($body)` | auth | Start RAG retrieval |
| `ragRetrieveStatus($sessionId)` | auth | Poll RAG retrieval |
| `pickFolderStart($body)` | auth | Start folder picker |
| `pickFolderStatus($sessionId)` | auth | Poll folder picker |
| `linkedinConnectStart()` | auth | Start LinkedIn OAuth |
| `linkedinConnectCancel()` | auth | Cancel LinkedIn OAuth |
| `linkedinConnectStatus()` | auth | Poll LinkedIn OAuth |
| `lumaConnectStart()` | auth | Start Luma OAuth |
| `lumaConnectStatus()` | auth | Poll Luma OAuth |
| `lumaConnectCancel()` | auth | Cancel Luma OAuth |
| `getLumaRegistrationSchema()` | auth | Luma form schema |
| `googleOAuthStart()` | auth | Start Google OAuth |
| `cliAuthStart()` | auth | Start CLI auth |
| `notebookLMLogin($body)` | auth | Store NotebookLM creds |
| `notebookLMStatus()` | auth | NotebookLM status |
| `telegramAuthStart($phone)` | auth | Telegram phone step |
| `telegramAuthCode($code)` | auth | Telegram SMS code |
| `telegramAuth2fa($password)` | auth | Telegram 2FA |
| `googleStatus()` | auth | Google OAuth capabilities granted |
| `googleSetDefault($capability, $accountId)` | auth | Select default Google account for a capability |
| `googleDisconnect($accountId, $capability)` | auth | Disconnect a Google product or account |
| `dbTestStart($service, $credentials)` | auth | Test a DB connector from the user's runner |
| `dbTestStatus($sessionId)` | auth | Poll DB connector test result |
| `telegramQrStart($apiId, $apiHash)` | auth | Start Telegram QR login |
| `telegramQrPoll($handle)` | auth | Poll Telegram QR login |
| `whatsappSignupConfig()` | auth | WhatsApp Embedded Signup config |
| `whatsappSignupExchange($body)` | auth | WhatsApp Embedded Signup code exchange |
| `tiktokCreatorInfo()` | auth | Connected TikTok account's creator info |
| `substackEmailLinkSend($email)` | auth | Ask Substack to email a sign-in link |
| `substackEmailLinkRedeem($link, $email)` | auth | Finish Substack sign-in |

### `$m->connectors`

| Method | Auth | Description |
|---|---|---|
| `connectedServices($project)` | auth | List project services |
| `set($project, $service, $body)` | auth | Store connector credential |
| `delete($project, $service)` | auth | Delete connector credential |
| `envHandle($project)` | auth | Get env-handle token |
| `googleOAuthStart($project)` | auth | Start Google OAuth |
| `applyPersonal($project, $service, $googleCapabilities)` | auth | Share a personal connector into the project |
| `sharedBy($project)` | auth | Who shared each project connector |
| `googleStatus($project)` | auth | Project's Google OAuth capabilities |
| `googleSetDefault($project, $capability, $accountId)` | auth | Select project's default Google account |
| `googleDisconnect($project, $accountId, $capability)` | auth | Disconnect a project's Google product/account |
| `dbTestStart($project, $service, $credentials)` | auth | Test a project DB connector from the user's runner |
| `dbTestStatus($project, $sessionId)` | auth | Poll project DB connector test result |

### `$m->team`

| Method | Auth | Description |
|---|---|---|
| `listMembers($project)` | auth | List team members |
| `invite($project, $username)` | auth | Invite by username |
| `createInviteLink($project)` | auth | Create invite link |
| `acceptInvite($token)` | auth | Accept an invite |
| `updateMemberRole($project, $userId, $role)` | auth | Update member role |
| `removeMember($project, $userId)` | auth | Remove member |
| `transferOwnership($project, $newOwnerUserId)` | auth | Transfer project ownership |
| `getPipelineVisibility($project, $pipeline)` | auth | Get pipeline visibility |
| `setPipelineVisibility($project, $pipeline, $body)` | auth | Set pipeline visibility |

### `$m->templates`

| Method | Auth | Description |
|---|---|---|
| `list()` | auth | List visible templates |
| `save($body)` | auth | Create a template |
| `update($id, $body)` | auth | Update a template |
| `duplicate($id, $newName)` | auth | Duplicate a template |
| `delete($id)` | auth | Delete a template |
| `share($id, $visibility)` | auth | Change visibility |
| `listAssignments($id)` | auth | List assignments |
| `assign($id, $target)` | auth | Assign — `['userId' => ...]` XOR `['projectId' => ...]` |
| `unassign($id, $target)` | auth | Remove assignment — same target shape, sent as query params |
| `shareTargets()` | auth | List projects for sharing |
| `listValidated()` | auth | List validated IDs |
| `listGlobal()` | auth | List community templates |

### `$m->phone`

| Method | Auth | Description |
|---|---|---|
| `pair()` | auth | Start pairing |
| `listDevices()` | auth | List paired devices |
| `revokeDevice($deviceId)` | auth | Revoke a device |
| `screenTree()` | auth | Get accessibility tree |
| `listApps()` | auth | List installed apps |
| `setAllowedApps($packageNames)` | auth | Set app allowlist |
| `registerActiveRun($runId)` | auth | Register active run |
| `grantApp($pkg, $label)` | auth | Grant one app into the allowlist (atomic append) |
| `requestCast($deviceId)` | auth | Re-cast the phone screen |

### `$m->assistant`

| Method | Auth | Description |
|---|---|---|
| `getProfile()` | auth | Get onboarding profile |
| `setProfile($profile)` | auth | Save onboarding profile |

### `$m->evals`

| Method | Auth | Description |
|---|---|---|
| `listRuns()` | auth | List eval runs |
| `summary()` | auth | Aggregate summary |
| `runDetail($runId)` | auth | Detailed run results |
| `compare($params)` | auth | Compare runs |
| `memoryGraph()` | auth | Memory graph data |
| `runMemory($runId)` | auth | Memory for a run |
| `crewMemory($params)` | auth | Agent crew memory |
| `benchmarks()` | auth | Benchmark scores |
| `editEntry($body)` | auth | Edit one persisted crew-memory entry |
| `deleteEntry($body)` | auth | Delete one persisted crew-memory entry |

### `$m->connectorTools`

The same connector tool surface the Melaya app and MCP server expose (Gmail,
Slack, Stripe, …) — discover and call tools directly. **Not** the same as
`$m->connectors`, which stores project-scoped connector *credentials*; this
module *calls* the tools those credentials unlock, and no method here ever
accepts or returns a credential value.

Reads run immediately. Writes default to an approval card in the Melaya app
(`$approval = "required"`, the default on `call()`); `$approval = "none"`
runs the write immediately and is audit-logged instead. Tools that move money
or trade are always refused, under both approval modes, and raise a
`MelayaException` with `errorCode === "money_moving_requires_app_approval"`.

| Method | Auth | Description |
|---|---|---|
| `services()` | auth | Connected services + read/write tool counts |
| `search($q, $limit)` | auth | Discover tools by plain business keywords |
| `describe($tool)` | auth | Full description of one tool (params, readOnly, movesMoney) |
| `test($service)` | auth | Test the stored credential for a service |
| `connect($service)` | auth | Start connecting a service (never accepts a secret) |
| `call($tool, $args, $approval)` | auth | Call a tool — read runs immediately; write stages (202) unless `$approval = "none"` |
| `callStatus($requestId)` | auth | Poll the outcome of a staged write |
| `callAndWait($tool, $args, $approval, $pollIntervalMs, $timeoutMs)` | auth | `call()` + blocks polling `callStatus()` to a final outcome |

### `$m->bugs`

| Method | Auth | Description |
|---|---|---|
| `create($body)` | auth | Submit a bug report |
| `listMine()` | auth | List my bug reports |
| `get($bugId)` | auth | Get a bug report |
| `addComment($bugId, $comment)` | auth | Add a comment |
| `listNotifications()` | auth | List notifications |
| `markNotificationsRead()` | auth | Mark as read |

### `$m->stream` (WebSocket — market data)

| Method | Description |
|---|---|
| `ticker($exchange, $symbol, $market)` | Live ticker frames |
| `orderbook($exchange, $symbol, $limit, $market)` | Live order book |
| `ohlcv($exchange, $symbol, $timeframe, $market)` | Live OHLCV frames |
| `trades($exchange, $symbol, $market)` | Live public trades |
| `liquidations($exchange)` | Liquidation firehose |
| `strategies()` | Private strategy events |
| `private($exchange, ...)` | Private account feed |

Returns a `WsClient`. Call `readFrame()` to receive one JSON frame, `close()` when done.

Public streams authenticate with `?apiKey=` in the `wss://` URL (server
protocol). Private streams (`strategies()`, `private()`) mint a short-lived
ticket via `POST /api/v1/private/private-ticket` and connect with `?wsTicket=`.

### `$m->events` (Socket.IO real-time)

| Method | Description |
|---|---|
| `connect()` | Engine.IO handshake (call first) |
| `close()` | Close and release listeners |
| `joinRun($runId)` | Join run:<runId> room |
| `leaveRun($runId)` | Leave run room |
| `joinProject($project)` | Join project:<project> room |
| `leaveProject($project)` | Leave project room |
| `onRunUpdate($runId, $cb)` | Subscribe to run events |
| `onInitPhase($runId, $cb)` | Subscribe to init-phase events |
| `onProjectEvent($project, $cb)` | Subscribe to project events |
| `onHitlApproval($cb)` | Subscribe to HITL events |
| `onPipelineCreated($project, $cb)` | Pipeline created events |
| `onPipelineUpdated($project, $cb)` | Pipeline updated events |
| `onPipelineDeleted($project, $cb)` | Pipeline deleted events |
| `poll()` | Execute one polling cycle |
| `listen($maxEvents, $totalSec)` | Time-bounded blocking loop |

## Error Handling

All errors throw `Melaya\MelayaException`:

```php
try {
    $m->hitl->approve('nonexistent');
} catch (Melaya\MelayaException $e) {
    echo $e->getMessage();   // "Melaya API error: HTTP 404"
    echo $e->status;         // 404
    echo $e->errorCode;      // "NOT_FOUND" (from response 'error' field)
    print_r($e->body);       // raw parsed response
}
```

Both server error envelopes are handled:
- `{ "error": "tier_insufficient", "tier": "..." }` → 403
- `{ "error": "...", "message": "...", "code": "..." }` → any 4xx/5xx

Retries: bounded exponential backoff applies only to idempotent GET requests.
Mutating requests are never retried automatically.

## TLS Note

TLS certificate and hostname verification are always enabled. Install a private
development or corporate CA in PHP/cURL's configured trust store when required.

## License

[Apache-2.0](LICENSE)
