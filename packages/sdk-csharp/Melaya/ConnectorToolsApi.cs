namespace Melaya;

/// <summary>
/// Connector tool calls — the same discover/call surface the Melaya MCP server exposes,
/// over plain REST. Maps to <c>/api/v1/private/connector-tools/*</c>.
/// <para>
/// Not to be confused with <see cref="ConnectorsApi"/> / <see cref="CredentialsApi"/>, which
/// store project/user connector credentials — this module only calls tools that credential
/// unlocks.
/// </para>
/// <para>
/// Read tools run immediately. Write tools default to <c>approval: "required"</c>: the call
/// stages the same approval card the Assistant raises in the Melaya app, returns
/// <c>202 { requestId }</c> (a success, not an error), and the write runs once, the first time
/// the caller polls <see cref="CallStatusAsync"/> after the user approves it. Pass
/// <c>approval: "none"</c> to run a write immediately instead — it is still audit-logged.
/// Tools that move money or trade are always refused, under both approval modes. No method
/// here ever accepts or returns a credential value.
/// </para>
/// </summary>
/// <example>
/// <code>
/// var services = await m.Agents.ConnectorTools.ServicesAsync();
/// var found    = await m.Agents.ConnectorTools.SearchAsync("unread email");
///
/// // A write, staged for approval in the Melaya app, then polled to completion.
/// var outcome = await m.Agents.ConnectorTools.CallAndWaitAsync(
///     "gmail_send", new Dictionary&lt;string, object?&gt; { ["to"] = "a@b.c", ["subject"] = "Hi" });
/// Console.WriteLine(outcome.Status); // "done" | "rejected" | "expired"
///
/// // A write that skips the approval card (still audit-logged).
/// var sent = await m.Agents.ConnectorTools.CallAsync(
///     "gmail_send", new Dictionary&lt;string, object?&gt; { ["to"] = "a@b.c" }, approval: "none");
/// </code>
/// </example>
public sealed class ConnectorToolsApi
{
    private const string Base = "/api/v1/private/connector-tools";

    private readonly MelayaHttpClient _http;

    internal ConnectorToolsApi(MelayaHttpClient http) => _http = http;

    /// <summary>Connected services, plus a per-service read/write tool count. Names only.</summary>
    public async Task<ConnectorToolServicesResult> ServicesAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<ConnectorToolServicesResult>($"{Base}/services", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Discover tools by plain business keywords (e.g. <c>"unread email"</c>), ranked by relevance.
    /// </summary>
    /// <param name="q">Business keywords. Required.</param>
    /// <param name="limit">Max results, 1-50 (server default 15).</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<ConnectorToolSearchResult> SearchAsync(string q, int? limit = null, CancellationToken ct = default)
    {
        var query = new Dictionary<string, string?> { ["q"] = q };
        if (limit is not null) query["limit"] = limit.Value.ToString();
        return await _http.GetAsync<ConnectorToolSearchResult>($"{Base}/search", query, ct).ConfigureAwait(false);
    }

    /// <summary>One tool's full description and parameters. 404 if unknown, or not unlocked by your connected services.</summary>
    public async Task<ConnectorToolInfo> DescribeAsync(string tool, CancellationToken ct = default)
    {
        return await _http.GetAsync<ConnectorToolInfo>($"{Base}/tools/{Uri.EscapeDataString(tool)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Test the STORED credential for a service. The server times out at 30s (surfaced here as <c>MelayaException.Code == "timeout"</c>, HTTP 504).</summary>
    public async Task<ConnectorToolTestResult> TestAsync(string service, CancellationToken ct = default)
    {
        return await _http.PostAsync<ConnectorToolTestResult>($"{Base}/test", new { service }, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Start connecting a service. OAuth services return a URL for the user to open;
    /// interactive-login and API-key services return where to finish in the Melaya app.
    /// Never takes, and never returns, a secret.
    /// </summary>
    public async Task<ConnectorToolConnectResult> ConnectAsync(string service, CancellationToken ct = default)
    {
        return await _http.PostAsync<ConnectorToolConnectResult>($"{Base}/connect", new { service }, ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Call a tool. Reads run immediately (200). Writes with <paramref name="approval"/>
    /// <c>"required"</c> (the default) are staged as an approval card and return 202 — that is
    /// success, not an error; poll <see cref="CallStatusAsync"/> for the outcome, or use
    /// <see cref="CallAndWaitAsync"/> to block until it resolves. <c>approval: "none"</c> runs a
    /// write immediately and still audit-logs it. Tools that move money or trade are refused
    /// under both approval modes, raised as a <see cref="MelayaException"/> with
    /// <c>Code == "money_moving_requires_app_approval"</c> (HTTP 403).
    /// </summary>
    /// <param name="tool">Tool name, as returned by <see cref="SearchAsync"/> / <see cref="DescribeAsync"/>.</param>
    /// <param name="args">Tool arguments. Omit for a tool that takes none.</param>
    /// <param name="approval"><c>"required"</c> (default) or <c>"none"</c>.</param>
    /// <param name="ct">Optional cancellation token.</param>
    public async Task<ConnectorToolCallResult> CallAsync(
        string tool,
        Dictionary<string, object?>? args = null,
        string approval = "required",
        CancellationToken ct = default)
    {
        var body = new { tool, args = args ?? new Dictionary<string, object?>(), approval };
        return await _http.PostAsync<ConnectorToolCallResult>($"{Base}/call", body, ct).ConfigureAwait(false);
    }

    /// <summary>Outcome of a staged write (the <c>RequestId</c> from a 202 <see cref="CallAsync"/> response). 404 if unknown or expired.</summary>
    public async Task<ConnectorToolCallStatusResult> CallStatusAsync(string requestId, CancellationToken ct = default)
    {
        return await _http.GetAsync<ConnectorToolCallStatusResult>(
            $"{Base}/calls/{Uri.EscapeDataString(requestId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>
    /// Call a tool and block until it resolves. Reads and <c>approval: "none"</c> writes resolve
    /// immediately; a staged (<c>approval: "required"</c>) write is polled via
    /// <see cref="CallStatusAsync"/>, at <paramref name="pollIntervalMs"/> intervals, until its
    /// status is <c>done</c>, <c>rejected</c>, or <c>expired</c> — or until
    /// <paramref name="timeoutMs"/> elapses.
    /// </summary>
    /// <param name="tool">Tool name.</param>
    /// <param name="args">Tool arguments. Omit for a tool that takes none.</param>
    /// <param name="approval"><c>"required"</c> (default) or <c>"none"</c>.</param>
    /// <param name="pollIntervalMs">Delay between polls while pending/running (default 3 000 ms).</param>
    /// <param name="timeoutMs">Give up waiting for approval + execution after this long (default 10 min).</param>
    /// <param name="ct">Optional cancellation token.</param>
    /// <exception cref="MelayaException">
    /// Thrown with <c>Code == "timeout"</c> if the call is still unresolved when
    /// <paramref name="timeoutMs"/> elapses; propagated as-is for a refused/failed call.
    /// </exception>
    public async Task<ConnectorToolCallStatusResult> CallAndWaitAsync(
        string tool,
        Dictionary<string, object?>? args = null,
        string approval = "required",
        int pollIntervalMs = 3_000,
        int timeoutMs = 600_000,
        CancellationToken ct = default)
    {
        var call = await CallAsync(tool, args, approval, ct).ConfigureAwait(false);
        if (call.Status != "pending_approval")
        {
            // Already resolved (a read, or a write run with approval: "none") — reshape into
            // the same outcome type CallStatusAsync returns, so callers have one type to handle.
            return new ConnectorToolCallStatusResult
            {
                RequestId = call.RequestId,
                Tool      = call.Tool,
                Status    = call.Status,
                Ok        = call.Status == "done" ? true : null,
                Result    = call.Result,
            };
        }

        var requestId = call.RequestId
            ?? throw new MelayaException("Melaya: pending_approval response carried no requestId.", 0, "bad_response");

        var deadline = DateTime.UtcNow.AddMilliseconds(timeoutMs);
        while (true)
        {
            var status = await CallStatusAsync(requestId, ct).ConfigureAwait(false);
            if (status.Status is "done" or "rejected" or "expired") return status;

            if (DateTime.UtcNow >= deadline)
                throw new MelayaException(
                    $"Melaya: connector tool call {requestId} did not resolve within {timeoutMs}ms.", 0, "timeout");

            await Task.Delay(pollIntervalMs, ct).ConfigureAwait(false);
        }
    }
}
