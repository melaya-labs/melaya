<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Grouped accessor for all platform-plane modules.
 *
 * Access via `$melaya->platform->*`.
 *
 * @example
 * ```php
 * $m = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * // Projects
 * $projects = $m->platform->projects->list();
 *
 * // Credentials
 * $m->platform->credentials->set('openai', ['value' => 'sk-...']);
 *
 * // Billing
 * $sub = $m->platform->billing->subscription();
 *
 * // Team
 * $m->platform->team->invite('my-project', 'alice');
 *
 * // Templates
 * $templates = $m->platform->templates->listGlobal();
 *
 * // Auth
 * $me = $m->platform->auth->me();
 *
 * // Platform accounts (credits, keys, profile)
 * $credits = $m->platform->accounts->credits();
 *
 * // Runner tokens
 * $token = $m->platform->runner->createToken(['label' => 'prod-1']);
 *
 * // Connectors
 * $m->platform->connectors->set('proj', 'openai', ['value' => 'sk-...']);
 *
 * // Overview dashboard
 * $overview = $m->platform->overview->get();
 *
 * // Bug reports
 * $m->platform->bugs->create(['title' => 'stuck', 'description' => '...']);
 *
 * // Real-time Socket.IO events
 * $m->platform->events->connect();
 * $m->platform->events->onRunUpdate('run-123', fn($e) => print_r($e));
 * $m->platform->events->poll();
 * ```
 */
final class PlatformNamespace
{
    /** Create and list agent projects. */
    public readonly ProjectsAPI $projects;

    /** User-scoped credential storage (services, OAuth, env handles). */
    public readonly CredentialsAPI $credentials;

    /** Project-scoped connector credentials (per-project service keys). */
    public readonly ConnectorsAPI $connectors;

    /** Billing: subscription status, Stripe checkout / portal, pricing plans. */
    public readonly BillingAPI $billing;

    /** Project team management: members, roles, and invite links. */
    public readonly TeamAPI $team;

    /** Pipeline templates: create, share, assign, and manage visibility. */
    public readonly TemplatesAPI $templates;

    /** Dashboard overview, model prices, chart data, cost breakdowns. */
    public readonly OverviewAPI $overview;

    /** Runner token management: mint, list, revoke `mel_run_` tokens. */
    public readonly RunnerAPI $runner;

    /** Auth: login, register, MFA, password management, session control. */
    public readonly AuthAPI $auth;

    /** Platform accounts: credits, profile, GDPR export, CEX key removal. */
    public readonly AccountsAPI $accounts;

    /** Bug reports: submit, list, comment, and track notifications. */
    public readonly BugsAPI $bugs;

    /**
     * Platform real-time events over Socket.IO at /api/v1/events.
     * PHP's synchronous model requires polling; call connect() then poll() in a loop.
     */
    public readonly EventsClient $events;

    /** @internal Constructed by {@see Melaya}. */
    public function __construct(
        ProjectsAPI $projects,
        CredentialsAPI $credentials,
        ConnectorsAPI $connectors,
        BillingAPI $billing,
        TeamAPI $team,
        TemplatesAPI $templates,
        OverviewAPI $overview,
        RunnerAPI $runner,
        AuthAPI $auth,
        AccountsAPI $accounts,
        BugsAPI $bugs,
        EventsClient $events,
    ) {
        $this->projects     = $projects;
        $this->credentials  = $credentials;
        $this->connectors   = $connectors;
        $this->billing      = $billing;
        $this->team         = $team;
        $this->templates    = $templates;
        $this->overview     = $overview;
        $this->runner       = $runner;
        $this->auth         = $auth;
        $this->accounts     = $accounts;
        $this->bugs         = $bugs;
        $this->events       = $events;
    }
}
