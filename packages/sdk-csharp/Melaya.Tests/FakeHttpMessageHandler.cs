using System.Net;
using System.Text;

namespace Melaya.Tests;

/// <summary>
/// Minimal mock transport for <see cref="MelayaHttpClient"/> unit tests.
/// Captures the last outgoing <see cref="HttpRequestMessage"/> (and, for convenience,
/// its body as text) and returns a caller-supplied canned response — no network I/O.
/// </summary>
internal sealed class FakeHttpMessageHandler : HttpMessageHandler
{
    private readonly Func<HttpRequestMessage, Task<HttpResponseMessage>> _responder;

    /// <summary>The most recent request the handler observed.</summary>
    public HttpRequestMessage? LastRequest { get; private set; }

    /// <summary>
    /// The most recent request body, read as UTF-8 text (works for JSON and multipart/form-data
    /// bodies alike). Read eagerly here, before the caller disposes its <see cref="HttpContent"/>.
    /// </summary>
    public string? LastRequestBodyText { get; private set; }

    /// <summary>All requests observed, in order (for tests that need more than the last one).</summary>
    public List<HttpRequestMessage> Requests { get; } = [];

    public FakeHttpMessageHandler(Func<HttpRequestMessage, Task<HttpResponseMessage>> responder)
        => _responder = responder;

    /// <summary>Convenience constructor: always return the same canned response.</summary>
    public FakeHttpMessageHandler(HttpStatusCode status, string jsonBody)
        : this(_ => Task.FromResult(JsonResponse(status, jsonBody))) { }

    public static HttpResponseMessage JsonResponse(HttpStatusCode status, string json)
        => new(status) { Content = new StringContent(json, Encoding.UTF8, "application/json") };

    protected override async Task<HttpResponseMessage> SendAsync(HttpRequestMessage request, CancellationToken cancellationToken)
    {
        LastRequest = request;
        Requests.Add(request);

        // Read eagerly (works for StringContent and MultipartFormDataContent alike) — the
        // caller disposes its HttpContent once PostAsync/SendAsync returns, so it must not
        // be read after the fact.
        LastRequestBodyText = request.Content is null
            ? null
            : await request.Content.ReadAsStringAsync(cancellationToken).ConfigureAwait(false);

        return await _responder(request).ConfigureAwait(false);
    }
}
