<?php

declare(strict_types=1);

namespace Melaya;

/**
 * The Melaya unified SDK client.
 *
 * Supports two authentication modes:
 *   - Platform API key (prefixed `mk_*`): supply `apiKey`
 *   - Session JWT: supply `jwt` (returned by auth->login())
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * PRIMARY API — namespaced accessors (recommended)
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * @example
 * ```php
 * require_once 'vendor/autoload.php';
 * use Melaya\Melaya;
 *
 * $m = new Melaya(apiKey: getenv('MK'));
 *
 * // Trading namespace
 * $ticker   = $m->trading->market->ticker('binance', 'BTC/USDT', 'spot');
 * $ws       = $m->trading->stream->ticker('binance', 'BTC/USDT', 'spot');
 * $job      = $m->trading->backtest->start([...]);
 * $opt      = $m->trading->optimize->start([...]);
 *
 * // Agents namespace
 * $runs     = $m->agents->pipelines->list(['project' => 'my-project']);
 * $pending  = $m->agents->hitl->pending();
 * $m->agents->hitl->approve($pending[0]['requestId']);
 * $tree     = $m->agents->phone->screenTree();
 * $summary  = $m->agents->evals->summary();
 *
 * // Platform namespace
 * $projects = $m->platform->projects->list();
 * $m->platform->credentials->set('openai', ['value' => 'sk-...']);
 * $sub      = $m->platform->billing->subscription();
 * $me       = $m->platform->auth->me();
 * $m->platform->events->connect();
 * $m->platform->events->onRunUpdate('run-123', fn($e) => print_r($e));
 * $m->platform->events->poll();
 * ```
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * FLAT ALIASES — kept for backwards compatibility
 * ─────────────────────────────────────────────────────────────────────────────
 *
 * All individual module properties (`$m->market`, `$m->hitl`, `$m->projects`, …)
 * remain available unchanged.  They point to the exact same object instances as
 * the namespaced accessors — there is no duplication or extra overhead.
 *
 * @example
 * ```php
 * // These are identical:
 * $m->trading->market->ticker(...);
 * $m->market->ticker(...);         // flat alias — same object
 * ```
 */
class Melaya
{
    // ── Namespaced accessors (primary API) ────────────────────────────────────

    /**
     * Trading plane: market, account, sim, strategies, trade, backtest, stream, optimize.
     *
     * @see TradingNamespace
     */
    public readonly TradingNamespace $trading;

    /**
     * Agents plane: pipelines (runs), hitl, assistant, phone, evals.
     *
     * @see AgentsNamespace
     */
    public readonly AgentsNamespace $agents;

    /**
     * Platform plane: projects, credentials, connectors, billing, team,
     * templates, overview, runner, auth, accounts, bugs, events.
     *
     * @see PlatformNamespace
     */
    public readonly PlatformNamespace $platform;

    // ── Flat aliases (backwards-compatible) ───────────────────────────────────
    // Each property below is the SAME object as the corresponding namespace sub-property.

    // Trading
    /** @see TradingNamespace::$market */
    public readonly MarketAPI     $market;
    /** @see TradingNamespace::$account */
    public readonly AccountAPI    $account;
    /** @see TradingNamespace::$sim */
    public readonly SimAPI        $sim;
    /** @see TradingNamespace::$strategies */
    public readonly StrategiesAPI $strategies;
    /** @see TradingNamespace::$backtest */
    public readonly BacktestAPI   $backtest;
    /** @see TradingNamespace::$trade */
    public readonly TradeAPI      $trade;
    /** @see TradingNamespace::$stream */
    public readonly StreamAPI     $stream;
    /** @see TradingNamespace::$optimize */
    public readonly OptimizeAPI   $optimize;

    // Platform / agents
    /** @see PlatformNamespace::$auth */
    public readonly AuthAPI       $auth;
    /** @see PlatformNamespace::$accounts */
    public readonly AccountsAPI   $accounts;
    /** @see PlatformNamespace::$billing */
    public readonly BillingAPI    $billing;
    /** @see PlatformNamespace::$runner */
    public readonly RunnerAPI     $runner;
    /** @see PlatformNamespace::$projects */
    public readonly ProjectsAPI   $projects;
    /** @see AgentsNamespace::$pipelines */
    public readonly PipelinesAPI  $pipelines;
    /** @see PlatformNamespace::$overview */
    public readonly OverviewAPI   $overview;
    /** @see AgentsNamespace::$hitl */
    public readonly HitlAPI       $hitl;
    /** @see PlatformNamespace::$credentials */
    public readonly CredentialsAPI $credentials;
    /** @see PlatformNamespace::$connectors */
    public readonly ConnectorsAPI  $connectors;
    /** @see AgentsNamespace::$phone */
    public readonly PhoneAPI       $phone;
    /** @see PlatformNamespace::$team */
    public readonly TeamAPI        $team;
    /** @see PlatformNamespace::$templates */
    public readonly TemplatesAPI   $templates;
    /** @see AgentsNamespace::$assistant */
    public readonly AssistantAPI   $assistant;
    /** @see AgentsNamespace::$evals */
    public readonly EvalsAPI       $evals;
    /** @see PlatformNamespace::$bugs */
    public readonly BugsAPI        $bugs;
    /**
     * Platform real-time events over Socket.IO at /api/v1/events.
     * PHP's synchronous model requires polling; call connect() then poll() in a loop.
     * @see PlatformNamespace::$events
     */
    public readonly EventsClient   $events;

    /**
     * @param string      $apiKey   Platform API key (prefixed `mk_`). Mutually exclusive with $jwt.
     * @param string      $jwt      Session JWT (from auth->login()). Mutually exclusive with $apiKey.
     * @param string      $baseUrl  REST base URL. Defaults to https://api.melaya.org
     * @param string      $wsUrl    WebSocket base URL. Defaults to wss://wss.melaya.org
     * @param int         $timeout  HTTP timeout in seconds. Defaults to 30.
     * @param int         $retries  Max retry attempts on 429/5xx. Defaults to 3.
     */
    public function __construct(
        string $apiKey  = '',
        string $jwt     = '',
        string $baseUrl = 'https://api.melaya.org',
        string $wsUrl   = 'wss://wss.melaya.org',
        int    $timeout = 30,
        int    $retries = 3,
    ) {
        if ($apiKey === '' && $jwt === '') {
            throw new \InvalidArgumentException(
                'Melaya: either `apiKey` or `jwt` is required. '
                . 'Create an API key at melaya.org → Settings → API Keys.',
            );
        }
        if ($apiKey !== '' && $jwt !== '') {
            throw new \InvalidArgumentException(
                'Melaya: supply either `apiKey` OR `jwt`, not both.',
            );
        }

        $isApiKey = $apiKey !== '';
        $bearer   = $isApiKey ? $apiKey : $jwt;

        if ($isApiKey && !str_starts_with($bearer, 'mk_')) {
            throw new \InvalidArgumentException('Melaya: platform API keys must be prefixed `mk_`.');
        }

        $http = new HttpClient(
            bearer: $bearer,
            baseUrl: $baseUrl,
            injectApiKeyParam: $isApiKey,
            timeoutSec: $timeout,
            maxRetries: $retries,
        );

        // ── Instantiate every module once ────────────────────────────────────
        // Trading plane
        $market     = new MarketAPI($http);
        $account    = new AccountAPI($http);
        $sim        = new SimAPI($http);
        $strategies = new StrategiesAPI($http);
        $backtest   = new BacktestAPI($http);
        $trade      = new TradeAPI($http);
        $stream     = new StreamAPI($bearer, $wsUrl, $http);
        $optimize   = new OptimizeAPI($http);

        // Platform / agents plane
        $auth        = new AuthAPI($http);
        $accounts    = new AccountsAPI($http);
        $billing     = new BillingAPI($http);
        $runner      = new RunnerAPI($http);
        $projects    = new ProjectsAPI($http);
        $pipelines   = new PipelinesAPI($http);
        $overview    = new OverviewAPI($http);
        $hitl        = new HitlAPI($http);
        $credentials = new CredentialsAPI($http);
        $connectors  = new ConnectorsAPI($http);
        $phone       = new PhoneAPI($http);
        $team        = new TeamAPI($http);
        $templates   = new TemplatesAPI($http);
        $assistant   = new AssistantAPI($http);
        $evals       = new EvalsAPI($http);
        $bugs        = new BugsAPI($http);
        $events      = new EventsClient($bearer, $baseUrl);

        // ── Namespaced accessors ──────────────────────────────────────────────
        $this->trading  = new TradingNamespace(
            market:     $market,
            account:    $account,
            sim:        $sim,
            strategies: $strategies,
            trade:      $trade,
            backtest:   $backtest,
            stream:     $stream,
            optimize:   $optimize,
        );

        $this->agents   = new AgentsNamespace(
            pipelines: $pipelines,
            hitl:      $hitl,
            assistant: $assistant,
            phone:     $phone,
            evals:     $evals,
        );

        $this->platform = new PlatformNamespace(
            projects:    $projects,
            credentials: $credentials,
            connectors:  $connectors,
            billing:     $billing,
            team:        $team,
            templates:   $templates,
            overview:    $overview,
            runner:      $runner,
            auth:        $auth,
            accounts:    $accounts,
            bugs:        $bugs,
            events:      $events,
        );

        // ── Flat aliases (same object instances — no duplication) ─────────────
        $this->market     = $market;
        $this->account    = $account;
        $this->sim        = $sim;
        $this->strategies = $strategies;
        $this->backtest   = $backtest;
        $this->trade      = $trade;
        $this->stream     = $stream;
        $this->optimize   = $optimize;

        $this->auth        = $auth;
        $this->accounts    = $accounts;
        $this->billing     = $billing;
        $this->runner      = $runner;
        $this->projects    = $projects;
        $this->pipelines   = $pipelines;
        $this->overview    = $overview;
        $this->hitl        = $hitl;
        $this->credentials = $credentials;
        $this->connectors  = $connectors;
        $this->phone       = $phone;
        $this->team        = $team;
        $this->templates   = $templates;
        $this->assistant   = $assistant;
        $this->evals       = $evals;
        $this->bugs        = $bugs;
        $this->events      = $events;
    }
}
