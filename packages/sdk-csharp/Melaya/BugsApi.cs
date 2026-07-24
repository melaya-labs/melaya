using System.Text.Json;

namespace Melaya;

/// <summary>
/// Bugs API — submit bug reports and read notifications.
/// Maps to <c>/api/v1/private/bugs/*</c>.
/// </summary>
public sealed class BugsApi
{
    private readonly MelayaHttpClient _http;

    internal BugsApi(MelayaHttpClient http) => _http = http;

    /// <summary>Submit a bug report.</summary>
    public async Task<BugReport> CreateAsync(BugReportCreateRequest body, CancellationToken ct = default)
    {
        return await _http.PostAsync<BugReport>("/api/v1/private/bugs", body, ct).ConfigureAwait(false);
    }

    /// <summary>List bug reports submitted by the caller.</summary>
    public async Task<List<BugReport>> ListMineAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<BugReport>>("/api/v1/private/bugs/mine", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Get a single bug report by ID.</summary>
    public async Task<BugReport> GetAsync(string bugId, CancellationToken ct = default)
    {
        return await _http.GetAsync<BugReport>(
            $"/api/v1/private/bugs/{Uri.EscapeDataString(bugId)}", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Add a comment to a bug report.</summary>
    public async Task<JsonElement> AddCommentAsync(string bugId, string comment, CancellationToken ct = default)
    {
        var body = new { comment };
        return await _http.PostAsync<JsonElement>(
            $"/api/v1/private/bugs/{Uri.EscapeDataString(bugId)}/comments", body, ct).ConfigureAwait(false);
    }

    /// <summary>List unread bug-related notifications for the caller.</summary>
    public async Task<List<JsonElement>> ListNotificationsAsync(CancellationToken ct = default)
    {
        return await _http.GetAsync<List<JsonElement>>("/api/v1/private/bugs/notifications", ct: ct).ConfigureAwait(false);
    }

    /// <summary>Mark bug notifications as read.</summary>
    public async Task<BoolResult> MarkNotificationsReadAsync(CancellationToken ct = default)
    {
        return await _http.PostAsync<BoolResult>("/api/v1/private/bugs/notifications/read", null, ct).ConfigureAwait(false);
    }
}
