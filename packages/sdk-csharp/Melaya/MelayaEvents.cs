using System.Net.WebSockets;
using System.Text;
using System.Text.Json;

namespace Melaya;

// ── Real-time event payload shapes ────────────────────────────────────────────

/// <summary>
/// Emitted as <c>pushEvent</c> on rooms <c>run:&lt;runId&gt;</c> and <c>project:&lt;project&gt;</c>.
/// </summary>
public sealed class RunPushEvent
{
    [System.Text.Json.Serialization.JsonPropertyName("event_type")]  public string? EventType   { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("runId")]       public string? RunId        { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("project")]     public string? Project      { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("pipelineName")]public string? PipelineName { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("agentId")]     public string? AgentId      { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("message")]     public string? Message      { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("status")]      public string? Status       { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("timestamp")]   public string? Timestamp    { get; set; }
    [System.Text.Json.Serialization.JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Emitted as <c>pushInitPhase</c> on run and project rooms.</summary>
public sealed class RunInitPhaseEvent
{
    [System.Text.Json.Serialization.JsonPropertyName("runId")]   public string? RunId   { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("step")]    public int?    Step    { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("total")]   public int?    Total   { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("label")]   public string? Label   { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("status")]  public string? Status  { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("project")] public string? Project { get; set; }
    [System.Text.Json.Serialization.JsonExtensionData]           public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Emitted as <c>pushHitlApprovals</c> on room <c>hitl:user:&lt;userId&gt;</c>.</summary>
public sealed class HitlApprovalEvent
{
    [System.Text.Json.Serialization.JsonPropertyName("type")]      public string? Type      { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("runId")]     public string? RunId     { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("requestId")] public string? RequestId { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("count")]     public int?    Count     { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("at")]        public string? At        { get; set; }
    [System.Text.Json.Serialization.JsonExtensionData]             public Dictionary<string, JsonElement>? Extra { get; set; }
}

/// <summary>Emitted as <c>pipelineCreated</c>, <c>pipelineUpdated</c>, or <c>pipelineDeleted</c> on project rooms.</summary>
public sealed class PipelineCrudEvent
{
    [System.Text.Json.Serialization.JsonPropertyName("name")]        public string? Name      { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("project")]     public string? Project   { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("description")] public string? Desc      { get; set; }
    [System.Text.Json.Serialization.JsonPropertyName("updatedBy")]   public string? UpdatedBy { get; set; }
    [System.Text.Json.Serialization.JsonExtensionData]               public Dictionary<string, JsonElement>? Extra { get; set; }
}

// ── MelayaEvents ──────────────────────────────────────────────────────────────

/// <summary>
/// Platform real-time events via Engine.IO v4 / Socket.IO v4 at <c>/api/v1/events</c>.
/// <para>
/// Uses the standard Engine.IO protocol: HTTP long-poll handshake → upgrade to
/// <see cref="ClientWebSocket"/>. Authentication is passed in Authorization
/// headers and the standard Socket.IO connect auth payload.
/// </para>
/// <para>
/// The connection is opened lazily: constructing the client does not open a
/// socket. The background connection loop starts on the first <c>On*</c>
/// subscription call. Connection errors are surfaced through
/// <see cref="OnError"/> when set (otherwise traced via
/// <see cref="System.Diagnostics.Debug"/>), and the reconnect backoff resets
/// after every successful connection.
/// </para>
/// <para>
/// Room semantics:
/// <list type="bullet">
///   <item><c>run:&lt;runId&gt;</c> — events for a specific pipeline run</item>
///   <item><c>project:&lt;project&gt;</c> — all events in a project</item>
///   <item><c>hitl:user:&lt;userId&gt;</c> — HITL approval events (auto-joined by server on connect)</item>
/// </list>
/// </para>
/// </summary>
/// <example>
/// <code>
/// m.Events.OnRunUpdate("run-123", e => Console.WriteLine(e.EventType));
/// m.Events.OnHitlApproval(e => Console.WriteLine($"HITL: {e.Type}"));
/// // later:
/// m.Events.LeaveRun("run-123");
/// m.Events.Dispose();
/// </code>
/// </example>
public sealed class MelayaEvents : IDisposable
{
    // Engine.IO v4 packet type characters
    private const string EioOpen    = "0";
    private const string EioPing    = "2";
    private const string EioPong    = "3";
    private const string EioMessage = "4";

    // Socket.IO v4 packet types (after EIO "4" prefix)
    private const int SioConnect = 0;
    private const int SioEvent   = 2;

    private readonly string _apiKey;
    private readonly string _baseUrl;    // https://api.melaya.org

    private string? _sid;
    private ClientWebSocket? _ws;
    private CancellationTokenSource _cts = new();
    private Task? _loopTask;
    private bool _started;
    private bool _disposed;
    private volatile bool _sessionUp;

    // event name → list of untyped handlers
    private readonly Dictionary<string, List<Action<JsonElement>>> _listeners = new();
    // room → handlers registered for that room (removed on LeaveRun / LeaveProject)
    private readonly Dictionary<string, List<(string EventName, Action<JsonElement> Handler)>> _roomListeners = new();
    private readonly HashSet<string> _joinedRooms = new();
    private readonly object _lock = new();

    /// <summary>
    /// Optional callback invoked when the background connection loop hits an
    /// error (handshake failure, dropped socket, …). When unset, errors are
    /// written to <see cref="System.Diagnostics.Debug"/>. The loop keeps
    /// reconnecting with bounded exponential backoff either way.
    /// </summary>
    public Action<Exception>? OnError { get; set; }

    internal MelayaEvents(string apiKey, string baseUrl)
    {
        _apiKey  = apiKey;
        _baseUrl = baseUrl.TrimEnd('/');
        // Lazy: the connection loop starts on the first subscription call.
    }

    /// <summary>Starts the background connection loop once (thread-safe, idempotent).</summary>
    private void EnsureStarted()
    {
        lock (_lock)
        {
            if (_started || _disposed) return;
            _started  = true;
            var token = _cts.Token;
            _loopTask = Task.Run(() => ConnectLoopAsync(token));
        }
    }

    // ── Public subscription API ───────────────────────────────────────────────

    /// <summary>
    /// Subscribe to run events for a specific pipeline run.
    /// Automatically joins the <c>run:&lt;runId&gt;</c> Socket.IO room.
    /// Events carrying a different <c>runId</c> (from other joined rooms) are filtered out.
    /// Returns an unsubscribe action.
    /// </summary>
    public Action OnRunUpdate(string runId, Action<RunPushEvent> handler)
    {
        EnsureStarted();
        JoinRoom($"run:{runId}");
        return OnRoomScoped($"run:{runId}", "pushEvent", el =>
        {
            var e = Deserialize<RunPushEvent>(el);
            if (e.RunId is not null && e.RunId != runId) return;
            handler(e);
        });
    }

    /// <summary>
    /// Subscribe to init-phase progress events for a run.
    /// Automatically joins the <c>run:&lt;runId&gt;</c> room.
    /// Events carrying a different <c>runId</c> are filtered out.
    /// Returns an unsubscribe action.
    /// </summary>
    public Action OnInitPhase(string runId, Action<RunInitPhaseEvent> handler)
    {
        EnsureStarted();
        JoinRoom($"run:{runId}");
        return OnRoomScoped($"run:{runId}", "pushInitPhase", el =>
        {
            var e = Deserialize<RunInitPhaseEvent>(el);
            if (e.RunId is not null && e.RunId != runId) return;
            handler(e);
        });
    }

    /// <summary>
    /// Subscribe to all events in a project.
    /// Automatically joins the <c>project:&lt;project&gt;</c> room.
    /// Events carrying a different <c>project</c> (from other joined rooms) are filtered out.
    /// Returns an unsubscribe action.
    /// </summary>
    public Action OnProjectEvent(string project, Action<RunPushEvent> handler)
    {
        EnsureStarted();
        JoinRoom($"project:{project}");
        return OnRoomScoped($"project:{project}", "pushEvent", el =>
        {
            var e = Deserialize<RunPushEvent>(el);
            if (e.Project is not null && e.Project != project) return;
            handler(e);
        });
    }

    /// <summary>
    /// Subscribe to HITL approval events.
    /// The server auto-joins authenticated sockets to the user's HITL room on connect.
    /// Returns an unsubscribe action.
    /// </summary>
    public Action OnHitlApproval(Action<HitlApprovalEvent> handler)
    {
        EnsureStarted();
        return OnRaw("pushHitlApprovals", el => handler(Deserialize<HitlApprovalEvent>(el)));
    }

    /// <summary>Subscribe to pipeline created events in a project room.</summary>
    public Action OnPipelineCreated(string project, Action<PipelineCrudEvent> handler)
        => OnPipelineCrud(project, "pipelineCreated", handler);

    /// <summary>Subscribe to pipeline updated events in a project room.</summary>
    public Action OnPipelineUpdated(string project, Action<PipelineCrudEvent> handler)
        => OnPipelineCrud(project, "pipelineUpdated", handler);

    /// <summary>Subscribe to pipeline deleted events in a project room.</summary>
    public Action OnPipelineDeleted(string project, Action<PipelineCrudEvent> handler)
        => OnPipelineCrud(project, "pipelineDeleted", handler);

    private Action OnPipelineCrud(string project, string eventName, Action<PipelineCrudEvent> handler)
    {
        EnsureStarted();
        JoinRoom($"project:{project}");
        return OnRoomScoped($"project:{project}", eventName, el =>
        {
            var e = Deserialize<PipelineCrudEvent>(el);
            if (e.Project is not null && e.Project != project) return;
            handler(e);
        });
    }

    /// <summary>
    /// Leave a run room and stop receiving events for it.
    /// Removes the listeners registered for that run and drops the room from
    /// the rejoin-on-reconnect list. Other rooms are unaffected.
    /// </summary>
    public void LeaveRun(string runId)     => LeaveRoom($"run:{runId}");

    /// <summary>
    /// Leave a project room.
    /// Removes the listeners registered for that project and drops the room
    /// from the rejoin-on-reconnect list. Other rooms are unaffected.
    /// </summary>
    public void LeaveProject(string project) => LeaveRoom($"project:{project}");

    // ── Internal ─────────────────────────────────────────────────────────────

    private Action OnRaw(string eventName, Action<JsonElement> cb)
    {
        lock (_lock)
        {
            if (!_listeners.TryGetValue(eventName, out var list))
            {
                list = new();
                _listeners[eventName] = list;
            }
            list.Add(cb);
        }
        return () =>
        {
            lock (_lock)
            {
                _listeners.TryGetValue(eventName, out var l);
                l?.Remove(cb);
            }
        };
    }

    /// <summary>
    /// Like <see cref="OnRaw"/>, but also tracks the handler under its room so
    /// <see cref="LeaveRun"/> / <see cref="LeaveProject"/> can remove it.
    /// </summary>
    private Action OnRoomScoped(string room, string eventName, Action<JsonElement> cb)
    {
        lock (_lock)
        {
            if (!_listeners.TryGetValue(eventName, out var list))
            {
                list = new();
                _listeners[eventName] = list;
            }
            list.Add(cb);

            if (!_roomListeners.TryGetValue(room, out var roomList))
            {
                roomList = new();
                _roomListeners[room] = roomList;
            }
            roomList.Add((eventName, cb));
        }
        return () =>
        {
            lock (_lock)
            {
                if (_listeners.TryGetValue(eventName, out var l)) l.Remove(cb);
                if (_roomListeners.TryGetValue(room, out var rl))
                    rl.RemoveAll(t => t.EventName == eventName && ReferenceEquals(t.Handler, cb));
            }
        };
    }

    private void Emit(string eventName, JsonElement payload)
    {
        List<Action<JsonElement>>? handlers;
        lock (_lock)
        {
            _listeners.TryGetValue(eventName, out handlers);
            handlers = handlers is null ? null : new List<Action<JsonElement>>(handlers);
        }
        if (handlers is null) return;
        foreach (var h in handlers)
        {
            try { h(payload); }
            catch { /* listener errors must not crash the event loop */ }
        }
    }

    private void HandleSioEvent(JsonElement[] args)
    {
        if (args.Length < 2) return;
        var eventName = args[0].GetString();
        if (eventName is null) return;
        Emit(eventName, args[1]);
    }

    private void HandlePacket(string raw)
    {
        if (raw.Length == 0) return;
        var type = raw[0].ToString();

        if (type == EioPing)
        {
            _ = WsSendAsync(EioPong, CancellationToken.None);
            return;
        }
        if (type != EioMessage) return;

        var sioRaw = raw.Substring(1);
        if (sioRaw.Length == 0) return;
        if (!int.TryParse(sioRaw[0].ToString(), out var sioType)) return;
        if (sioType != SioEvent) return;

        try
        {
            var argsJson = sioRaw.Substring(1);
            var args = JsonSerializer.Deserialize<JsonElement[]>(argsJson);
            if (args is not null) HandleSioEvent(args);
        }
        catch { /* ignore malformed packets */ }
    }

    private void JoinRoom(string room)
    {
        lock (_lock)
        {
            if (!_joinedRooms.Add(room)) return;
        }
        if (_sid is not null)
            _ = WsSendAsync(SioJoinPacket(room), CancellationToken.None);
    }

    private void LeaveRoom(string room)
    {
        lock (_lock)
        {
            _joinedRooms.Remove(room);
            if (_roomListeners.Remove(room, out var roomList))
            {
                foreach (var (eventName, cb) in roomList)
                {
                    if (_listeners.TryGetValue(eventName, out var l)) l.Remove(cb);
                }
            }
        }
        if (_sid is not null)
            _ = WsSendAsync(SioEmitPacket("leaveRoom", room), CancellationToken.None);
    }

    private void ReplayJoins()
    {
        HashSet<string> rooms;
        lock (_lock) { rooms = new HashSet<string>(_joinedRooms); }
        foreach (var r in rooms)
            _ = WsSendAsync(SioJoinPacket(r), CancellationToken.None);
    }

    /// <summary>
    /// Maps a logical room string to the correct server-side emit event.
    /// "run:&lt;id&gt;"     → joinRunRoom(&lt;id&gt;)
    /// "project:&lt;name&gt;" → joinProjectRoom(&lt;name&gt;)
    /// other             → joinRunRoom(&lt;room&gt;) as safe fallback
    /// </summary>
    private static string SioJoinPacket(string room)
    {
        if (room.StartsWith("run:", StringComparison.Ordinal))
            return SioEmitPacket("joinRunRoom", room.Substring(4));
        if (room.StartsWith("project:", StringComparison.Ordinal))
            return SioEmitPacket("joinProjectRoom", room.Substring(8));
        // safe fallback: treat as a bare run ID
        return SioEmitPacket("joinRunRoom", room);
    }

    // ── Connection loop ───────────────────────────────────────────────────────

    private async Task ConnectLoopAsync(CancellationToken ct)
    {
        int backoffMs = 1000;
        const int MaxBackoffMs = 30_000;
        var rng = new Random();
        while (!ct.IsCancellationRequested)
        {
            _sessionUp = false;
            try
            {
                await ConnectOnceAsync(ct).ConfigureAwait(false);
            }
            catch (OperationCanceledException) { return; }
            catch (Exception ex) { ReportError(ex); }

            // Reset backoff after any successful connection, even if the
            // session later dropped with an error.
            if (_sessionUp) backoffMs = 1000;

            if (ct.IsCancellationRequested) return;
            // bounded exponential backoff with jitter
            int jitter = rng.Next(0, Math.Min(backoffMs / 2, 1000));
            try { await Task.Delay(backoffMs + jitter, ct).ConfigureAwait(false); }
            catch (OperationCanceledException) { return; }
            backoffMs = Math.Min(backoffMs * 2, MaxBackoffMs);
        }
    }

    private void ReportError(Exception ex)
    {
        if (_disposed) return; // expected teardown noise
        var cb = OnError;
        if (cb is not null)
        {
            try { cb(ex); }
            catch { /* the error callback must not crash the loop */ }
        }
        else
        {
            System.Diagnostics.Debug.WriteLine($"MelayaEvents: {ex.GetType().Name}: {ex.Message}");
        }
    }

    private async Task ConnectOnceAsync(CancellationToken ct)
    {
        // Step 1: Engine.IO handshake via HTTP GET polling
        using var http = BuildHttpClient();
        var pollGetUrl = PollUrl(null);
        var handshakeText = await http.GetStringAsync(pollGetUrl, ct).ConfigureAwait(false);

        var jsonStart = handshakeText.IndexOf('{');
        if (jsonStart < 0) throw new InvalidOperationException("MelayaEvents: unexpected EIO handshake");
        var openData = JsonSerializer.Deserialize<EioOpenPacket>(handshakeText.Substring(jsonStart))!;
        _sid = openData.Sid;

        // Step 2: Send Socket.IO connect packet with auth via polling POST
        var connectPacket = $"{EioMessage}{SioConnect}{JsonSerializer.Serialize(new { token = _apiKey })}";
        var content = new StringContent(connectPacket, Encoding.UTF8, "text/plain");
        await http.PostAsync(PollUrl(null), content, ct).ConfigureAwait(false);

        // Step 3: Upgrade to WebSocket
        _ws?.Dispose();
        _ws = new ClientWebSocket();
        _ws.Options.SetRequestHeader("Authorization", $"Bearer {_apiKey}");
        await _ws.ConnectAsync(new Uri(WsUrl()), ct).ConfigureAwait(false);
        _sessionUp = true; // successful connection — backoff resets in the loop

        // Send Engine.IO upgrade probe
        await WsSendAsync("2probe", ct).ConfigureAwait(false);
        await WsSendAsync("5", ct).ConfigureAwait(false);

        // Replay room joins
        ReplayJoins();

        // Read loop
        var buf = new byte[64 * 1024];
        while (_ws.State == WebSocketState.Open && !ct.IsCancellationRequested)
        {
            var sb = new StringBuilder();
            WebSocketReceiveResult result;
            do
            {
                result = await _ws.ReceiveAsync(buf, ct).ConfigureAwait(false);
                if (result.MessageType == WebSocketMessageType.Close)
                {
                    await _ws.CloseOutputAsync(WebSocketCloseStatus.NormalClosure, "", ct).ConfigureAwait(false);
                    return;
                }
                sb.Append(Encoding.UTF8.GetString(buf, 0, result.Count));
            }
            while (!result.EndOfMessage);

            HandlePacket(sb.ToString());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private string PollUrl(string? extra)
    {
        var u = new UriBuilder(_baseUrl)
        {
            Path  = "/api/v1/events/",
            Query = "EIO=4&transport=polling"
                  + (_sid is not null ? $"&sid={Uri.EscapeDataString(_sid)}" : "")
                  + (extra ?? ""),
        };
        return u.Uri.ToString();
    }

    private string WsUrl()
    {
        var u = new UriBuilder(_baseUrl)
        {
            Path   = "/api/v1/events/",
            Query  = "EIO=4&transport=websocket"
                   + (_sid is not null ? $"&sid={Uri.EscapeDataString(_sid)}" : ""),
        };
        // Switch protocol: https→wss, http→ws
        u.Scheme = u.Scheme switch
        {
            "https" => "wss",
            "http"  => "ws",
            _       => u.Scheme,
        };
        return u.Uri.ToString();
    }

    private static string SioEmitPacket(string eventName, string arg)
        => $"{EioMessage}{SioEvent}{JsonSerializer.Serialize(new object[] { eventName, arg })}";

    private async Task WsSendAsync(string text, CancellationToken ct)
    {
        try
        {
            if (_ws is null || _ws.State != WebSocketState.Open) return;
            var bytes = Encoding.UTF8.GetBytes(text);
            await _ws.SendAsync(bytes, WebSocketMessageType.Text, true, ct).ConfigureAwait(false);
        }
        catch { /* ignore transient send errors */ }
    }

    private HttpClient BuildHttpClient()
    {
        HttpMessageHandler handler = new HttpClientHandler();
        var client = new HttpClient(handler);
        client.DefaultRequestHeaders.Authorization =
            new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", _apiKey);
        return client;
    }

    private static T Deserialize<T>(JsonElement el)
    {
        var json = el.GetRawText();
        return JsonSerializer.Deserialize<T>(json)
            ?? throw new InvalidOperationException($"Could not deserialize {typeof(T).Name}");
    }

    /// <inheritdoc/>
    public void Dispose()
    {
        if (_disposed) return;
        _disposed = true;
        _cts.Cancel();
        _cts.Dispose();
        _ws?.Dispose();
        _ws = null;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private sealed class EioOpenPacket
    {
        [System.Text.Json.Serialization.JsonPropertyName("sid")]
        public string? Sid { get; set; }

        [System.Text.Json.Serialization.JsonPropertyName("pingInterval")]
        public int? PingInterval { get; set; }

        [System.Text.Json.Serialization.JsonPropertyName("pingTimeout")]
        public int? PingTimeout { get; set; }
    }
}
