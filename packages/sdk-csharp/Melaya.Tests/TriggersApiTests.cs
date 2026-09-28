using System.Net;
using System.Text.Json;

namespace Melaya.Tests;

public class TriggersApiTests
{
    private static (TriggersApi api, FakeHttpMessageHandler handler) MakeApi(string json, HttpStatusCode status = HttpStatusCode.OK)
    {
        var handler = new FakeHttpMessageHandler(status, json);
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        return (new TriggersApi(http), handler);
    }

    private static void AssertRequest(FakeHttpMessageHandler handler, HttpMethod method, string path)
    {
        var req = handler.LastRequest!;
        Assert.Equal(method, req.Method);
        Assert.Equal(path, req.RequestUri!.AbsolutePath);
    }

    [Fact]
    public async Task ListAsync_SendsFilters_AndDecodesRecords()
    {
        var (api, handler) = MakeApi(
            """[{"id":"t1","name":"Refunds","kind":"webhook","project":"support","pipelineName":"refunds","enabled":true,"config":{"decide":{}},"maxEventsPerMin":60,"webhookUrl":"https://api.melaya.org/hooks/abc","projectAccess":true}]""");

        var list = await api.ListAsync(project: "support", pipelineName: "refunds");

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers");
        var query = handler.LastRequest!.RequestUri!.Query;
        Assert.Contains("project=support", query);
        Assert.Contains("pipelineName=refunds", query);

        var t = Assert.Single(list);
        Assert.Equal("t1", t.Id);
        Assert.Equal("webhook", t.Kind);
        Assert.True(t.Enabled);
        Assert.Equal(60, t.MaxEventsPerMin);
        Assert.Equal(JsonValueKind.Object, t.Config!.Value.ValueKind);
        Assert.Equal("https://api.melaya.org/hooks/abc", t.WebhookUrl);
    }

    [Fact]
    public async Task ListAsync_OmitsQuery_WhenNoFilters()
    {
        var (api, handler) = MakeApi("[]");

        var list = await api.ListAsync();

        Assert.Empty(list);
        Assert.Equal("", handler.LastRequest!.RequestUri!.Query);
    }

    [Fact]
    public async Task GetAsync_EscapesId()
    {
        var (api, handler) = MakeApi("""{"id":"a/b","kind":"poll"}""");

        var t = await api.GetAsync("a/b");

        Assert.Equal(HttpMethod.Get, handler.LastRequest!.Method);
        Assert.EndsWith("/api/v1/private/triggers/a%2Fb", handler.LastRequest.RequestUri!.AbsoluteUri);
        Assert.Equal("poll", t.Kind);
    }

    [Fact]
    public async Task DeliveriesAsync_SendsLimit()
    {
        var (api, handler) = MakeApi("""[{"id":"d1","verdict":"dispatched"}]""");

        var rows = await api.DeliveriesAsync("t1", limit: 20);

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/t1/deliveries");
        Assert.Equal("?limit=20", handler.LastRequest!.RequestUri!.Query);
        Assert.Equal("dispatched", rows[0].GetProperty("verdict").GetString());
    }

    [Fact]
    public async Task StatsAsync_SendsHours_AndDecodesVerdicts()
    {
        var (api, handler) = MakeApi(
            """{"hours":48,"byVerdict":{"dispatched":{"n":12,"p50":120.5,"p95":900},"failed":{"n":1,"p50":null,"p95":null}},"filtered":3,"sampled":false}""");

        var stats = await api.StatsAsync("t1", hours: 48);

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/t1/stats");
        Assert.Equal("?hours=48", handler.LastRequest!.RequestUri!.Query);
        Assert.Equal(48, stats.Hours);
        Assert.Equal(12, stats.ByVerdict!["dispatched"].N);
        Assert.Equal(120.5, stats.ByVerdict["dispatched"].P50);
        Assert.Null(stats.ByVerdict["failed"].P95);
        Assert.Equal(3, stats.Filtered);
        Assert.False(stats.Sampled);
    }

    [Fact]
    public async Task PendingApprovalsAsync_HitsApprovalsPath()
    {
        var (api, handler) = MakeApi("""[{"requestId":"r1"}]""");

        var rows = await api.PendingApprovalsAsync("t1");

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/t1/approvals");
        Assert.Equal("r1", rows[0].GetProperty("requestId").GetString());
    }

    [Fact]
    public async Task TestAsync_PostsPayload_AndDecodesResult()
    {
        var (api, handler) = MakeApi("""{"accepted":false,"eventId":"test-1","reason":"rate_limited"}""");

        var res = await api.TestAsync("t1", new { type = "refund.created", amount = 12 });

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/triggers/t1/test");
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        var payload = doc.RootElement.GetProperty("payload");
        Assert.Equal("refund.created", payload.GetProperty("type").GetString());
        Assert.Equal(12, payload.GetProperty("amount").GetInt32());

        Assert.False(res.Accepted);
        Assert.Equal("test-1", res.EventId);
        Assert.Equal("rate_limited", res.Reason);
    }

    [Fact]
    public async Task TestAsync_WithoutPayload_SendsEmptyObject()
    {
        var (api, handler) = MakeApi("""{"accepted":true,"eventId":"test-2"}""");

        var res = await api.TestAsync("t1");

        Assert.Equal("{}", handler.LastRequestBodyText);
        Assert.True(res.Accepted);
        Assert.Null(res.Reason);
    }

    [Fact]
    public async Task EventsAsync_SendsAllFilters_AsCommaList()
    {
        var (api, handler) = MakeApi(
            """{"events":[{"triggerId":"t1","deliveryId":null,"eventId":"e1","source":"webhook","verdict":"rejected","detail":"bad signature","at":1790000000000}],"scanned":40,"retention":{"maxEvents":500,"ttlSec":86400}}""");

        var res = await api.EventsAsync(triggerId: "t1", since: 1789999999000, verdicts: new[] { "rejected", "failed" }, limit: 50);

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/events");
        var query = handler.LastRequest!.RequestUri!.Query;
        Assert.Contains("triggerId=t1", query);
        Assert.Contains("since=1789999999000", query);
        Assert.Contains("verdicts=rejected%2Cfailed", query);
        Assert.Contains("limit=50", query);

        var ev = Assert.Single(res.Events!);
        Assert.Null(ev.DeliveryId);
        Assert.Equal("rejected", ev.Verdict);
        Assert.Equal(1790000000000L, ev.At);
        Assert.Equal(40, res.Scanned);
        Assert.Equal(500, res.Retention!.MaxEvents);
        Assert.Equal(86400, res.Retention.TtlSec);
    }

    [Fact]
    public async Task EventsAsync_OmitsEmptyFilters()
    {
        var (api, handler) = MakeApi("""{"events":[],"scanned":0,"retention":{"maxEvents":500,"ttlSec":86400}}""");

        await api.EventsAsync(verdicts: Array.Empty<string>());

        Assert.Equal("", handler.LastRequest!.RequestUri!.Query);
    }

    [Fact]
    public async Task PollStatusAsync_DecodesState()
    {
        var (api, handler) = MakeApi(
            """{"synced":true,"status":"ok","lastError":null,"lastPolledAt":"2026-09-28T10:00:00.000Z","nextPollAt":"2026-09-28T10:05:00.000Z","armed":true,"baselinePending":false,"seenCount":17,"itemsPublished":4,"consecutiveErrors":0,"requestedIntervalSec":60,"effectiveIntervalSec":300,"tierFloorSec":300}""");

        var st = await api.PollStatusAsync("t1");

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/t1/poll");
        Assert.True(st.Synced);
        Assert.Equal("ok", st.Status);
        Assert.Null(st.LastError);
        Assert.Equal("2026-09-28T10:05:00.000Z", st.NextPollAt);
        Assert.True(st.Armed);
        Assert.False(st.BaselinePending);
        Assert.Equal(17, st.SeenCount);
        Assert.Equal(4, st.ItemsPublished);
        Assert.Equal(60, st.RequestedIntervalSec);
        Assert.Equal(300, st.EffectiveIntervalSec);
        Assert.Equal(300, st.TierFloorSec);
    }

    [Fact]
    public async Task PollTestAsync_PostsDryTrue_AndDecodesItems()
    {
        var (api, handler) = MakeApi(
            """{"dry":true,"ok":true,"found":3,"baseline":false,"wouldPublish":2,"items":[{"id":"i1","preview":"{\"title\":\"a\"}"}],"samplePayload":{"title":"a"}}""");

        var res = await api.PollTestAsync("t1");

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/triggers/t1/poll/test");
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.True(doc.RootElement.GetProperty("dry").GetBoolean());

        Assert.True(res.Dry);
        Assert.True(res.Ok);
        Assert.Equal(3, res.Found);
        Assert.False(res.Baseline);
        Assert.Equal(2, res.WouldPublish);
        Assert.Equal("i1", Assert.Single(res.Items!).Id);
        Assert.Equal("a", res.SamplePayload!.Value.GetProperty("title").GetString());
    }

    [Fact]
    public async Task PollTestAsync_FailedPoll_ReturnsResult_WithoutThrowing()
    {
        var (api, _) = MakeApi("""{"dry":true,"ok":false,"error":"tool_failed"}""");

        var res = await api.PollTestAsync("t1");

        Assert.True(res.Dry);
        Assert.False(res.Ok);
        Assert.Equal("tool_failed", res.Error);
    }

    [Fact]
    public async Task PollTestAsync_Throttled_ThrowsMelayaException()
    {
        var (api, _) = MakeApi("""{"error":"poll_dry_run_throttled"}""", HttpStatusCode.TooManyRequests);

        var ex = await Assert.ThrowsAsync<MelayaException>(() => api.PollTestAsync("t1"));

        Assert.Equal(429, ex.Status);
        Assert.Equal("poll_dry_run_throttled", ex.Code);
    }

    [Fact]
    public async Task PollNowAsync_PostsDryFalse()
    {
        var (api, handler) = MakeApi("""{"dry":false,"queued":true}""");

        var res = await api.PollNowAsync("t1");

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/triggers/t1/poll/test");
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.False(doc.RootElement.GetProperty("dry").GetBoolean());
        Assert.False(res.Dry);
        Assert.True(res.Queued);
    }

    [Fact]
    public async Task PollSyncAsync_PostsToSyncPath()
    {
        var (api, handler) = MakeApi("""{"result":"armed"}""");

        var res = await api.PollSyncAsync("t1");

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/triggers/t1/poll/sync");
        Assert.Equal("{}", handler.LastRequestBodyText);
        Assert.Equal("armed", res.GetProperty("result").GetString());
    }

    [Fact]
    public async Task PresetsAsync_DecodesTierAndPresets()
    {
        var (api, handler) = MakeApi(
            """{"tier":"pro","tierFloorSec":60,"presets":[{"id":"gmail_new_mail"}],"beta":{"allowed":true}}""");

        var res = await api.PresetsAsync();

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/presets");
        Assert.Equal("pro", res.Tier);
        Assert.Equal(60, res.TierFloorSec);
        Assert.Equal("gmail_new_mail", Assert.Single(res.Presets!).GetProperty("id").GetString());
        Assert.True(res.Beta!.Value.GetProperty("allowed").GetBoolean());
    }

    [Fact]
    public async Task LimitsAsync_HitsLimitsPath()
    {
        var (api, handler) = MakeApi("""{"tierClass":"pro","triggers":{"used":2,"cap":20},"pollIntervalFloorSec":60}""");

        var res = await api.LimitsAsync();

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/limits");
        Assert.Equal(20, res.GetProperty("triggers").GetProperty("cap").GetInt32());
    }

    [Fact]
    public async Task SourcesAsync_HitsSourcesPath()
    {
        var (api, handler) = MakeApi("""[{"id":"s1","name":"Binance trades"}]""");

        var res = await api.SourcesAsync();

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/triggers/sources");
        Assert.Equal("s1", res[0].GetProperty("id").GetString());
    }
}
