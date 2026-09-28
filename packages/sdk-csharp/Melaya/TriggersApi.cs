using System.Text.Json;

namespace Melaya;

/// <summary>
/// Event triggers: read, diagnose and dry-run the triggers that start your pipelines.
/// Maps to <c>/api/v1/private/triggers/*</c>.
/// <para>
/// Read-only apart from the dry runs and poll controls. Creating, updating, deleting a
/// trigger and rotating its signing secret stay in the Agent Builder and the MCP server,
/// where push consent and autonomy grants are enforced. No method here returns a signing secret.
/// </para>
/// <para>
/// <see cref="TestAsync"/> and <see cref="PollTestAsync"/> are dry runs: the trigger's action
/// never executes. <see cref="PollNowAsync"/> queues a real poll.
/// </para>
/// </summary>
/// <example>
/// <code>
/// var triggers = await m.Agents.Triggers.ListAsync(project: "support");
/// var stats    = await m.Agents.Triggers.StatsAsync(triggers[0].Id!, hours: 24);
///
/// // What happened recently, including events that wrote no receipt.
/// var live = await m.Agents.Triggers.EventsAsync(verdicts: new[] { "rejected", "failed" }, limit: 50);
///
/// // Dry run one event (the action never runs).
/// var test = await m.Agents.Triggers.TestAsync(triggers[0].Id!, new { type = "ping" });
/// </code>
/// </example>
public sealed class TriggersApi
{
    private const string Base = "/api/v1/private/triggers";

    private readonly MelayaHttpClient _http;

    internal TriggersApi(MelayaHttpClient http) => _http = http;

    // ── Triggers ──────────────────────────────────────────────────────────────

    /// <summary>List your event triggers, optionally filtered by project and pipeline.</summary>
    /// <param name="project">Project name filter.</param>
    /// <param name="pipelineName">Pipeline name filter.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<List<TriggerRecord>> ListAsync(
        string? project = null,
        string? pipelineName = null,
        CancellationToken ct = default)
    {
        var q = Q(("project", project), ("pipelineName", pipelineName));
        return await _http.GetAsync<List<TriggerRecord>>(Base, q, ct).ConfigureAwait(false);
    }

    /// <summary>One trigger with its config. Secrets are never included.</summary>
    public async Task<TriggerRecord> GetAsync(string id, CancellationToken ct = default)
    {
        return await _http.GetAsync<TriggerRecord>($"{Base}/{Uri.EscapeDataString(id)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Recent deliveries (receipts) of a trigger: verdict, decision answers, action, run id and timings.
    /// </summary>
    /// <param name="id">Trigger id.</param>
    /// <param name="limit">Max receipts, 1-200 (server default 50).</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<JsonElement> DeliveriesAsync(string id, int? limit = null, CancellationToken ct = default)
    {
        var q = Q(("limit", limit?.ToString()));
        return await _http.GetAsync<JsonElement>($"{Base}/{Uri.EscapeDataString(id)}/deliveries", q, ct).ConfigureAwait(false);
    }

    /// <summary>Delivery counts by verdict over a window.</summary>
    /// <param name="id">Trigger id.</param>
    /// <param name="hours">Window in hours, 1-168 (server default 24).</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<TriggerStats> StatsAsync(string id, int? hours = null, CancellationToken ct = default)
    {
        var q = Q(("hours", hours?.ToString()));
        return await _http.GetAsync<TriggerStats>($"{Base}/{Uri.EscapeDataString(id)}/stats", q, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Approvals still waiting on this trigger's runs and tool calls. Read only: decide them in
    /// the Melaya app.
    /// </summary>
    public async Task<JsonElement> PendingApprovalsAsync(string id, CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"{Base}/{Uri.EscapeDataString(id)}/approvals", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Dry run one event through the prefilter, the decide step and routing. The action never
    /// executes. The trigger's rate limits still apply, so a refused test comes back with
    /// <c>Accepted == false</c> and a <c>Reason</c> (<c>disabled</c>, <c>rate_limited</c>, ...).
    /// Follow the outcome with <see cref="EventsAsync"/> for this trigger.
    /// </summary>
    /// <param name="id">Trigger id.</param>
    /// <param name="payload">The event payload to test with. Omit for an empty object.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<TriggerTestResult> TestAsync(string id, object? payload = null, CancellationToken ct = default)
    {
        return await _http.PostAsync<TriggerTestResult>(
            $"{Base}/{Uri.EscapeDataString(id)}/test", new { payload }, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Recent live trigger events, newest first (the last 500 events or 24 hours), including the
    /// outcomes that write no receipt: filtered, shed, ingress rejections, feed refusals and push
    /// lifecycle notes. To follow new events, pass <paramref name="since"/> = the newest <c>At</c> already seen.
    /// </summary>
    /// <param name="triggerId">Only this trigger's events.</param>
    /// <param name="since">Only events after this time, epoch milliseconds.</param>
    /// <param name="verdicts">Only these verdicts, e.g. <c>rejected</c>, <c>filtered</c>, <c>failed</c>.</param>
    /// <param name="limit">Max events, 1-200.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<TriggerLiveEventsResult> EventsAsync(
        string? triggerId = null,
        long? since = null,
        IEnumerable<string>? verdicts = null,
        int? limit = null,
        CancellationToken ct = default)
    {
        var v = verdicts is null ? null : string.Join(",", verdicts);
        var q = Q(
            ("triggerId", triggerId),
            ("since", since?.ToString()),
            ("verdicts", string.IsNullOrEmpty(v) ? null : v),
            ("limit", limit?.ToString()));
        return await _http.GetAsync<TriggerLiveEventsResult>($"{Base}/events", q, ct).ConfigureAwait(false);
    }

    // ── Poll triggers ─────────────────────────────────────────────────────────

    /// <summary>
    /// Runtime state of a poll trigger: status, last error, last and next poll time, whether it is
    /// armed, the baseline, counters and the effective interval after the plan floor.
    /// </summary>
    public async Task<TriggerPollState> PollStatusAsync(string triggerId, CancellationToken ct = default)
    {
        return await _http.GetAsync<TriggerPollState>(
            $"{Base}/{Uri.EscapeDataString(triggerId)}/poll", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Dry poll: calls the poll tool now and returns what it found and would publish. Nothing is
    /// published. A poll that fails comes back as <c>Ok == false</c> with the <c>Error</c> code
    /// instead of throwing. Dry polls are throttled to one every few seconds
    /// (<c>poll_dry_run_throttled</c>, HTTP 429).
    /// </summary>
    public async Task<TriggerPollTestResult> PollTestAsync(string triggerId, CancellationToken ct = default)
    {
        try
        {
            return await _http.PostAsync<TriggerPollTestResult>(
                $"{Base}/{Uri.EscapeDataString(triggerId)}/poll/test", new { dry = true }, ct).ConfigureAwait(false);
        }
        catch (MelayaException e) when (e.Status is >= 200 and < 300 && e.Body is System.Text.Json.Nodes.JsonObject body)
        {
            // A failed dry poll is a normal 200 result ({ dry, ok: false, error }), not a request
            // failure: return it rather than let the envelope check raise it.
            return body.Deserialize<TriggerPollTestResult>()!;
        }
    }

    /// <summary>
    /// Queue a real poll now. The trigger must be enabled (<c>trigger_disabled</c> otherwise).
    /// Returns <c>Queued</c>; follow the outcome with <see cref="PollStatusAsync"/> and <see cref="EventsAsync"/>.
    /// </summary>
    public async Task<TriggerPollTestResult> PollNowAsync(string triggerId, CancellationToken ct = default)
    {
        return await _http.PostAsync<TriggerPollTestResult>(
            $"{Base}/{Uri.EscapeDataString(triggerId)}/poll/test", new { dry = false }, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Re-create a poll trigger's runtime row from its saved config. Re-arms a trigger whose
    /// poller never started. Returns <c>{ result }</c>.
    /// </summary>
    public async Task<JsonElement> PollSyncAsync(string triggerId, CancellationToken ct = default)
    {
        return await _http.PostAsync<JsonElement>(
            $"{Base}/{Uri.EscapeDataString(triggerId)}/poll/sync", new { }, ct).ConfigureAwait(false);
    }

    // ── Account-wide ──────────────────────────────────────────────────────────

    /// <summary>Trigger presets available to you, given your connected services and plan.</summary>
    public async Task<TriggerPresetsResult> PresetsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<TriggerPresetsResult>($"{Base}/presets", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Plan caps and usage: triggers and sources used against the cap, events per minute,
    /// poll interval floor and approval TTL bounds.
    /// </summary>
    public async Task<JsonElement> LimitsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"{Base}/limits", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Your WebSocket and SSE stream sources.</summary>
    public async Task<JsonElement> SourcesAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<JsonElement>($"{Base}/sources", ct: ct).ConfigureAwait(false);
    }

    private static Dictionary<string, string?> Q(params (string Key, string? Value)[] pairs)
    {
        var d = new Dictionary<string, string?>(pairs.Length);
        foreach (var (k, v) in pairs)
            if (v is not null) d[k] = v;
        return d;
    }
}
