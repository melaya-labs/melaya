package org.melaya;

/**
 * The {@code platform} namespace — groups all platform-management modules into
 * one crystal-clear entry point.
 *
 * <p>Access via {@link Melaya#platform()}:
 * <pre>{@code
 * Melaya melaya = new Melaya("mk_yourkey");
 *
 * // List agent projects
 * JsonNode projects = melaya.platform().projects().list();
 *
 * // Manage credentials
 * melaya.platform().credentials().set("openai", Map.of("value", "sk-..."));
 *
 * // Billing
 * melaya.platform().billing().subscription();
 *
 * // Real-time events
 * melaya.platform().events().onRunUpdate("run-123", frame ->
 *     System.out.println(frame.get("event_type").asText()));
 * }</pre>
 *
 * <p>Modules in this namespace:
 * <ul>
 *   <li>{@link #projects()} — create and list agent projects</li>
 *   <li>{@link #credentials()} — user-scoped credential storage, OAuth, RAG, models</li>
 *   <li>{@link #connectors()} — project-scoped connector credentials</li>
 *   <li>{@link #billing()} — subscription status, Stripe checkout / portal, plans</li>
 *   <li>{@link #team()} — project team membership, roles, and invitations</li>
 *   <li>{@link #templates()} — pipeline templates: create, share, assign, manage</li>
 *   <li>{@link #overview()} — dashboard metrics, cost breakdown, pipeline lists</li>
 *   <li>{@link #runner()} — runner token management: mint, list, revoke</li>
 *   <li>{@link #auth()} — session management, login, registration, token refresh</li>
 *   <li>{@link #mfa()} — MFA enroll, confirm, and check TOTP</li>
 *   <li>{@link #accounts()} — profile update, GDPR export, API-key removal, credits</li>
 *   <li>{@link #bugs()} — submit and track bug reports</li>
 *   <li>{@link #events()} — Socket.IO real-time platform events</li>
 * </ul>
 */
public final class PlatformNamespace {

    private final ProjectsAPI    projects;
    private final CredentialsAPI credentials;
    private final ConnectorsAPI  connectors;
    private final BillingAPI     billing;
    private final TeamAPI        team;
    private final TemplatesAPI   templates;
    private final OverviewAPI    overview;
    private final RunnerAPI      runner;
    private final AuthAPI        auth;
    private final MfaAPI         mfa;
    private final AccountsAPI    accounts;
    private final BugsAPI        bugs;
    private final MelayaEvents   events;

    PlatformNamespace(
            ProjectsAPI    projects,
            CredentialsAPI credentials,
            ConnectorsAPI  connectors,
            BillingAPI     billing,
            TeamAPI        team,
            TemplatesAPI   templates,
            OverviewAPI    overview,
            RunnerAPI      runner,
            AuthAPI        auth,
            MfaAPI         mfa,
            AccountsAPI    accounts,
            BugsAPI        bugs,
            MelayaEvents   events) {
        this.projects    = projects;
        this.credentials = credentials;
        this.connectors  = connectors;
        this.billing     = billing;
        this.team        = team;
        this.templates   = templates;
        this.overview    = overview;
        this.runner      = runner;
        this.auth        = auth;
        this.mfa         = mfa;
        this.accounts    = accounts;
        this.bugs        = bugs;
        this.events      = events;
    }

    /** Create and list agent projects. */
    public ProjectsAPI projects() { return projects; }

    /** User-scoped credential storage (services, OAuth, RAG, models). */
    public CredentialsAPI credentials() { return credentials; }

    /** Project-scoped connector credentials (per-project service keys). */
    public ConnectorsAPI connectors() { return connectors; }

    /** Billing: subscription status, Stripe checkout / portal, pricing plans. */
    public BillingAPI billing() { return billing; }

    /** Project team management: members, roles, and invite links. */
    public TeamAPI team() { return team; }

    /** Pipeline templates: create, share, assign, and manage visibility. */
    public TemplatesAPI templates() { return templates; }

    /** Dashboard metrics, cost breakdown, pipeline lists. */
    public OverviewAPI overview() { return overview; }

    /** Runner token management: mint, list, revoke {@code mel_run_} tokens. */
    public RunnerAPI runner() { return runner; }

    /** Auth: session management, login, registration, password reset, token refresh. */
    public AuthAPI auth() { return auth; }

    /** MFA: enroll, confirm, and check TOTP. */
    public MfaAPI mfa() { return mfa; }

    /**
     * Accounts: profile update, GDPR export, API-key removal, credit balances.
     * Distinct from {@link org.melaya.Melaya#account()} which covers trading CEX account reads.
     */
    public AccountsAPI accounts() { return accounts; }

    /** Bug reports: submit and track. */
    public BugsAPI bugs() { return bugs; }

    /**
     * Platform real-time events over Socket.IO at {@code /api/v1/events}.
     * Subscribe to run updates, init-phase progress, HITL notifications,
     * and pipeline CRUD events.
     */
    public MelayaEvents events() { return events; }
}
