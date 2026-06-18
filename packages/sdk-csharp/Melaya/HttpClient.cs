using System.Net.Http.Headers;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace Melaya;

/// <summary>
/// Internal HTTP client. Injects <c>?apiKey=</c> and <c>Authorization: Bearer</c>
/// on every request and enforces the Melaya envelope contract.
/// </summary>
internal sealed class MelayaHttpClient : IDisposable
{
    private readonly string _apiKey;
    private readonly string _baseUrl;
    private readonly HttpClient _http;

    private static readonly JsonSerializerOptions _jsonOpts = new()
    {
        PropertyNamingPolicy        = null,   // preserve field names as-is
        DefaultIgnoreCondition      = System.Text.Json.Serialization.JsonIgnoreCondition.WhenWritingNull,
        WriteIndented               = false,
    };

    internal MelayaHttpClient(string apiKey, string baseUrl, HttpMessageHandler? handler = null)
    {
        _apiKey  = apiKey;
        _baseUrl = baseUrl.TrimEnd('/');
        _http    = handler is null ? new HttpClient() : new HttpClient(handler);
        _http.DefaultRequestHeaders.Authorization =
            new AuthenticationHeaderValue("Bearer", apiKey);
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    internal async Task<T> GetAsync<T>(string path,
        IReadOnlyDictionary<string, string?>? query = null,
        CancellationToken ct = default)
    {
        var url  = BuildUrl(path, query);
        var resp = await _http.GetAsync(url, ct).ConfigureAwait(false);
        return await ParseAsync<T>(resp, ct).ConfigureAwait(false);
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
        var resp = await _http.PostAsync(url, content, ct).ConfigureAwait(false);
        return await ParseAsync<T>(resp, ct).ConfigureAwait(false);
    }

    internal async Task<T> DeleteAsync<T>(string path,
        IReadOnlyDictionary<string, string?>? query = null,
        CancellationToken ct = default)
    {
        var url = BuildUrl(path, query);
        var req = new HttpRequestMessage(HttpMethod.Delete, url);
        var resp = await _http.SendAsync(req, ct).ConfigureAwait(false);
        return await ParseAsync<T>(resp, ct).ConfigureAwait(false);
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private string BuildUrl(string path, IReadOnlyDictionary<string, string?>? query = null)
    {
        var sb = new StringBuilder();
        sb.Append(_baseUrl);
        if (!path.StartsWith('/')) sb.Append('/');
        sb.Append(path);
        sb.Append("?apiKey=");
        sb.Append(Uri.EscapeDataString(_apiKey));
        if (query is not null)
        {
            foreach (var (k, v) in query)
            {
                if (v is null) continue;
                sb.Append('&');
                sb.Append(Uri.EscapeDataString(k));
                sb.Append('=');
                sb.Append(Uri.EscapeDataString(v));
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
