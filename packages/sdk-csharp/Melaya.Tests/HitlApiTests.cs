using System.Net;

namespace Melaya.Tests;

public class HitlApiTests
{
    [Fact]
    public async Task RunMessagesAsync_HitsRunsMessagesPath_WithPaging()
    {
        var handler = new FakeHttpMessageHandler(HttpStatusCode.OK,
            """[{"id":"m1","runId":"run 1","role":"assistant","content":"hi"}]""");
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        var api = new HitlApi(http);

        var rows = await api.RunMessagesAsync("run 1", limit: 25, cursor: "c1");

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Get, req.Method);
        // Not under /hitl/: the messages route lives at /api/v1/private/runs/{runId}/messages.
        Assert.Equal("/api/v1/private/runs/run%201/messages", req.RequestUri!.AbsolutePath);
        Assert.Contains("limit=25", req.RequestUri.Query);
        Assert.Contains("cursor=c1", req.RequestUri.Query);
        var msg = Assert.Single(rows);
        Assert.Equal("m1", msg.Id);
        Assert.Equal("hi", msg.Content);
    }
}
