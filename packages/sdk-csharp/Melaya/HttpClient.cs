using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Melaya;

/// <summary>
/// Internal HTTP client. Sends the API key ONLY via <c>Authorization: Bearer</c>
/// (never in the query string) and enforces the Melaya envelope contract.
/// <para>
/// Reliability features:
/// <list type="bullet">
///   <item>Per-request timeout via <see cref="CancellationTokenSource"/> (default 30 s, configurable).</item>
///   <item>Bounded retry (max 2 retries, exponential back-off with jitter) for GET requests only,
///         on network errors, HTTP 429, or HTTP 5xx. Honors <c>Retry-After</c> on 429.</item>
///   <item>POST / PUT / PATCH / DELETE are never retried.</item>
/// </list>
/// </para>
/// </summary>
internal sealed class MelayaHttpClient : IDisposable
{
    private readonly string _baseUrl;
    private readonly HttpClient _http;
    private readonly int _timeoutMs;

    private const int MaxRetries   = 2;
    private static readonly Random _rng = new();

    private static readonly JsonSerializerOptions _jsonOpts = new()
    {
        PropertyNamingPolicy        = null,   // preserve field names as-is
        DefaultIgnoreCondition      = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        WriteIndented               = false,
    };

    internal MelayaHttpClient(string apiKey, string baseUrl, HttpMessageHandler? handler = null, int timeoutMs = 30_000)
    {
        _baseUrl   = baseUrl.TrimEnd('/');
        _timeoutMs = timeoutMs;
        _http      = handler is null ? new HttpClient() : new HttpClient(handler);
        _http.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", apiKey);
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    internal async Task<T> GetAsync<T>(string path,
        IReadOnlyDictionary<string, string?>? query = null,
        CancellationToken ct = default)
    {
        var url = BuildUrl(path, query);
        return await RetryGetAsync<T>(url, ct).ConfigureAwait(false);
    }

    internal async Task<T> PostAsync<T>(string path, object? body = null,
        CancellationToken ct = default)
    {
        var url     = BuildUrl(path);
        var content = body is null
            ? null
            : new StringContent(
                JsonSerializer.Serialize(body, _jsonOpts),
                Encoding.UTF8,
                "application/json");
        using var cts = MakeTimeoutCts(ct);
        var resp = await _http.PostAsync(url, content, cts.Token).ConfigureAwait(false);
        return await ParseAsync<T>(resp, cts.Token).ConfigureAwait(false);
    }

    internal async Task<T> PutAsync<T>(string path, object? body = null,
        CancellationToken ct = default)
    {
        var url     = BuildUrl(path);
        var content = body is null
            ? null
            : new StringContent(
                JsonSerializer.Serialize(body, _jsonOpts),
                Encoding.UTF8,
                "application/json");
        using var cts = MakeTimeoutCts(ct);
        var req = new HttpRequestMessage(HttpMethod.Put, url) { Content = content };
        var resp = await _http.SendAsync(req, cts.Token).ConfigureAwait(false);
        return await ParseAsync<T>(resp, cts.Token).ConfigureAwait(false);
    }

    internal async Task<T> PatchAsync<T>(string path, object? body = null,
        CancellationToken ct = default)
    {
        var url     = BuildUrl(path);
        var content = body is null
            ? null
            : new StringContent(
                JsonSerializer.Serialize(body, _jsonOpts),
                Encoding.UTF8,
                "application/json");
        using var cts = MakeTimeoutCts(ct);
        var req = new HttpRequestMessage(HttpMethod.Patch, url) { Content = content };
        var resp = await _http.SendAsync(req, cts.Token).ConfigureAwait(false);
        return await ParseAsync<T>(resp, cts.Token).ConfigureAwait(false);
    }

    internal async Task<T> DeleteAsync<T>(string path,
        IReadOnlyDictionary<string, string?>? query = null,
        CancellationToken ct = default)
    {
        var url = BuildUrl(path, query);
        using var cts = MakeTimeoutCts(ct);
        var req = new HttpRequestMessage(HttpMethod.Delete, url);
        var resp = await _http.SendAsync(req, cts.Token).ConfigureAwait(false);
        return await ParseAsync<T>(resp, cts.Token).ConfigureAwait(false);
    }

    // ── Retry logic (GET only) ─────────────────────────────────────────────────

    private async Task<T> RetryGetAsync<T>(string url, CancellationToken ct)
    {
        int attempt   = 0;
        int backoffMs = 500;
        while (true)
        {
            HttpResponseMessage resp;
            using var cts = MakeTimeoutCts(ct);
            try
            {
                resp = await _http.GetAsync(url, cts.Token).ConfigureAwait(false);
            }
            catch (OperationCanceledException) when (!ct.IsCancellationRequested)
            {
                // timed out; propagate as timeout if out of retries
                if (attempt >= MaxRetries) throw;
                attempt++;
                await BackoffAsync(backoffMs, ct).ConfigureAwait(false);
                backoffMs = Math.Min(backoffMs * 2, 16_000);
                continue;
            }
            catch (HttpRequestException)
            {
                if (attempt >= MaxRetries) throw;
                attempt++;
                await BackoffAsync(backoffMs, ct).ConfigureAwait(false);
                backoffMs = Math.Min(backoffMs * 2, 16_000);
                continue;
            }

            var status = (int)resp.StatusCode;
            bool isRetryable = status == 429 || (status >= 500 && status <= 599);

            if (isRetryable && attempt < MaxRetries)
            {
                attempt++;
                int delayMs = backoffMs;
                if (status == 429 &&
                    resp.Headers.TryGetValues("Retry-After", out var retryAfterValues))
                {
                    var raw = System.Linq.Enumerable.FirstOrDefault(retryAfterValues);
                    if (raw is not null && int.TryParse(raw, out var secs))
                        delayMs = secs * 1000;
                }
                resp.Dispose();
                await BackoffAsync(delayMs, ct).ConfigureAwait(false);
                backoffMs = Math.Min(backoffMs * 2, 16_000);
                continue;
            }

            return await ParseAsync<T>(resp, ct).ConfigureAwait(false);
        }
    }

    private CancellationTokenSource MakeTimeoutCts(CancellationToken ct)
    {
        if (_timeoutMs <= 0)
            return CancellationTokenSource.CreateLinkedTokenSource(ct);
        var cts = CancellationTokenSource.CreateLinkedTokenSource(ct);
        cts.CancelAfter(_timeoutMs);
        return cts;
    }

    private static async Task BackoffAsync(int delayMs, CancellationToken ct)
    {
        int jitter = _rng.Next(0, Math.Min(delayMs / 4 + 1, 500));
        await Task.Delay(delayMs + jitter, ct).ConfigureAwait(false);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private string BuildUrl(string path, IReadOnlyDictionary<string, string?>? query = null)
    {
        var sb = new StringBuilder();
        sb.Append(_baseUrl);
        if (!path.StartsWith('/')) sb.Append('/');
        sb.Append(path);
        // SECURITY: the API key is sent ONLY via the Authorization: Bearer
        // header (configured on the HttpClient). It must never appear in the
        // URL query string, where it would leak into logs and proxies.
        var first = true;
        if (query is not null)
        {
            foreach (var (k, v) in query)
            {
                if (v is null) continue;
                sb.Append(first ? '?' : '&');
                sb.Append(Uri.EscapeDataString(k));
                sb.Append('=');
                sb.Append(Uri.EscapeDataString(v));
                first = false;
            }
        }
        return sb.ToString();
    }

    private static async Task<T> ParseAsync<T>(HttpResponseMessage resp, CancellationToken ct)
    {
        var text = await resp.Content.ReadAsStringAsync(ct).ConfigureAwait(false);

        JsonNode? node = null;
        if (!string.IsNullOrWhiteSpace(text))
        {
            try { node = JsonNode.Parse(text); }
            catch { /* leave null */ }
        }

        // Only objects carry the {ok,error} envelope; bare arrays/values pass through.
        var objNode = node as JsonObject;

        if (!resp.IsSuccessStatusCode)
        {
            var code = objNode?["error"]?.GetValue<string>();
            var status = (int)resp.StatusCode;
            throw new MelayaException(
                $"Melaya API {status}{(code is not null ? $" ({code})" : "")}",
                status, code, node);
        }

        // Envelope: ok==false is a request-level failure even on 2xx
        if (objNode?["ok"] is JsonValue okVal &&
            okVal.TryGetValue<bool>(out var okBool) && !okBool)
        {
            var code = objNode["error"]?.GetValue<string>();
            throw new MelayaException(
                $"Melaya API request failed{(code is not null ? $": {code}" : "")}",
                (int)resp.StatusCode, code, node);
        }

        if (typeof(T) == typeof(JsonNode))
            return (T)(object)(node ?? JsonValue.Create<string?>(null)!);

        if (string.IsNullOrWhiteSpace(text))
            return default!;

        return JsonSerializer.Deserialize<T>(text, _jsonOpts)!;
    }

    public void Dispose() => _http.Dispose();
}
