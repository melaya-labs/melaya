using System.Net.WebSockets;
using System.Runtime.CompilerServices;
using System.Text;
using System.Text.Json;

namespace Melaya;

/// <summary>
/// WebSocket streaming API.
/// Each method returns an <see cref="IAsyncEnumerable{T}"/> of raw JSON frames.
/// <para>Public streams append <c>?apiKey=</c>. Private streams first mint a
/// short-lived ticket via <c>POST /api/v1/private/private-ticket</c>, then connect
/// to <c>wss://wss.melaya.org/ws/&lt;stream&gt;?wsTicket=…</c>.</para>
/// </summary>
public sealed class StreamApi
{
    private readonly string           _apiKey;
    private readonly string           _wsUrl;
    private readonly MelayaHttpClient _http;
    private readonly bool             _insecureTls;

    internal StreamApi(string apiKey, string wsUrl, MelayaHttpClient http, bool insecureTls)
    {
        _apiKey      = apiKey;
        _wsUrl       = wsUrl.TrimEnd('/');
        _http        = http;
        _insecureTls = insecureTls;
    }

    // ── Public feeds ──────────────────────────────────────────────────────────

    /// <summary>Live ticker frames for one symbol.</summary>
    public async IAsyncEnumerable<JsonElement> TickerAsync(
        string exchange, string symbol, string? market = null,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var url = BuildPublicUrl("/ws/ticker",
            ("exchange", exchange), ("symbol", symbol), ("market", market));
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    /// <summary>Live order-book frames.</summary>
    public async IAsyncEnumerable<JsonElement> OrderbookAsync(
        string exchange, string symbol, string? market = null, int? limit = null,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var url = BuildPublicUrl("/ws/orderbook",
            ("exchange", exchange), ("symbol", symbol), ("market", market), ("limit", limit?.ToString()));
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    /// <summary>Live OHLCV candle frames.</summary>
    public async IAsyncEnumerable<JsonElement> OhlcvAsync(
        string exchange, string symbol, string timeframe, string? market = null,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var url = BuildPublicUrl("/ws/ohlcv",
            ("exchange", exchange), ("symbol", symbol), ("timeframe", timeframe), ("market", market));
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    /// <summary>Live public-trade frames.</summary>
    public async IAsyncEnumerable<JsonElement> TradesAsync(
        string exchange, string symbol, string? market = null,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var url = BuildPublicUrl("/ws/public-trades",
            ("exchange", exchange), ("symbol", symbol), ("market", market));
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    /// <summary>Cross-exchange liquidation firehose. Omit <paramref name="exchange"/> for all venues.</summary>
    public async IAsyncEnumerable<JsonElement> LiquidationsAsync(
        string? exchange = null,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var url = BuildPublicUrl("/ws/liquidations", ("exchange", exchange));
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    // ── Private feeds ─────────────────────────────────────────────────────────

    /// <summary>
    /// Live strategy events for your account (cycle markers, agent messages,
    /// approval requests, executions, status changes).
    /// Mints a ticket, then opens <c>/ws/strategies</c>.
    /// </summary>
    public async IAsyncEnumerable<JsonElement> StrategiesAsync(
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var ticket = await MintTicketAsync("strategies", null, null, null, ct).ConfigureAwait(false);
        var url    = $"{_wsUrl}/ws/strategies?wsTicket={Uri.EscapeDataString(ticket)}";
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    /// <summary>
    /// Live private account feed for one connected exchange key (balance,
    /// positions, your orders/fills). Mints a ticket, then opens <c>/ws/private</c>.
    /// </summary>
    public async IAsyncEnumerable<JsonElement> PrivateAsync(
        string exchange,
        string? market   = null,
        string? apiKeyId = null,
        string? keyId    = null,
        string? symbol   = null,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        var ticket = await MintTicketAsync("private", exchange, market, apiKeyId ?? keyId, ct).ConfigureAwait(false);
        var url    = $"{_wsUrl}/ws/private?wsTicket={Uri.EscapeDataString(ticket)}";
        await foreach (var f in ConnectAsync(url, ct).ConfigureAwait(false))
            yield return f;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private string BuildPublicUrl(string path, params (string Key, string? Value)[] @params)
    {
        var sb = new StringBuilder();
        sb.Append(_wsUrl);
        sb.Append(path);
        sb.Append("?apiKey=");
        sb.Append(Uri.EscapeDataString(_apiKey));
        foreach (var (k, v) in @params)
        {
            if (v is null) continue;
            sb.Append('&');
            sb.Append(Uri.EscapeDataString(k));
            sb.Append('=');
            sb.Append(Uri.EscapeDataString(v));
        }
        return sb.ToString();
    }

    private async IAsyncEnumerable<JsonElement> ConnectAsync(
        string url,
        [EnumeratorCancellation] CancellationToken ct = default)
    {
        using var ws = CreateWebSocket();
        await ws.ConnectAsync(new Uri(url), ct).ConfigureAwait(false);

        var buffer = new byte[64 * 1024];
        var msgBuf = new MemoryStream(64 * 1024);

        while (ws.State == WebSocketState.Open && !ct.IsCancellationRequested)
        {
            msgBuf.SetLength(0);
            ValueWebSocketReceiveResult result;
            try
            {
                do
                {
                    result = await ws.ReceiveAsync(buffer.AsMemory(), ct).ConfigureAwait(false);
                    if (result.MessageType == WebSocketMessageType.Close)
                        yield break;
                    msgBuf.Write(buffer, 0, result.Count);
                } while (!result.EndOfMessage);
            }
            catch (OperationCanceledException)
            {
                yield break;
            }
            catch (WebSocketException)
            {
                yield break;
            }

            var text = Encoding.UTF8.GetString(msgBuf.GetBuffer(), 0, (int)msgBuf.Length);
            JsonElement frame;
            try
            {
                frame = JsonSerializer.Deserialize<JsonElement>(text);
            }
            catch
            {
                continue; // ignore non-JSON keep-alive frames
            }
            yield return frame;
        }
    }

    private ClientWebSocket CreateWebSocket()
    {
        var ws = new ClientWebSocket();
        if (_insecureTls)
        {
            ws.Options.RemoteCertificateValidationCallback = (_, _, _, _) => true;
        }
        return ws;
    }

    private async Task<string> MintTicketAsync(
        string stream,
        string? exchange,
        string? market,
        string? apiKeyId,
        CancellationToken ct)
    {
        var body = new Dictionary<string, object?> { ["stream"] = stream };
        if (exchange is not null) body["exchange"]  = exchange;
        if (market   is not null) body["market"]    = market;
        if (apiKeyId is not null) body["apiKeyId"]  = apiKeyId;

        var r = await _http.PostAsync<WsTicketEnvelope>("/api/v1/private/private-ticket", body, ct).ConfigureAwait(false);
        if (r.WsTicket is null)
            throw new MelayaException("Melaya: private-ticket response missing wsTicket", 0);
        return r.WsTicket;
    }
}
