using System.Net;
using System.Text.Json;

namespace Melaya.Tests;

public class ConnectorToolsApiTests
{
    private static (ConnectorToolsApi api, FakeHttpMessageHandler handler) MakeApi(
        Func<HttpRequestMessage, Task<HttpResponseMessage>> responder)
    {
        var handler = new FakeHttpMessageHandler(responder);
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        return (new ConnectorToolsApi(http), handler);
    }

    [Fact]
    public async Task SearchAsync_EncodesQueryAndLimit_InTheQueryString()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"query":"unread email","services":["gmail"],"tools":[]}""")));

        var result = await api.SearchAsync("unread email", limit: 5);

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Get, req.Method);
        Assert.Equal("/api/v1/private/connector-tools/search", req.RequestUri!.AbsolutePath);
        // Spaces must be percent-encoded, not left raw or turned into '+'.
        Assert.Contains("q=unread%20email", req.RequestUri.Query);
        Assert.Contains("limit=5", req.RequestUri.Query);

        Assert.Equal("unread email", result.Query);
        Assert.Equal(["gmail"], result.Services);
        Assert.Empty(result.Tools!);
    }

    [Fact]
    public async Task SearchAsync_OmitsLimit_WhenNotProvided()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"query":"refund","services":[],"tools":[]}""")));

        await api.SearchAsync("refund");

        Assert.DoesNotContain("limit=", handler.LastRequest!.RequestUri!.Query);
    }

    [Fact]
    public async Task CallAsync_ReadTool_ReturnsDoneImmediately()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"status":"done","tool":"gmail_list_messages","readOnly":true,"result":"3 unread"}""")));

        var result = await api.CallAsync("gmail_list_messages");

        Assert.Equal("done", result.Status);
        Assert.True(result.ReadOnly);
        Assert.Equal("3 unread", result.Result);

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.Equal("/api/v1/private/connector-tools/call", req.RequestUri!.AbsolutePath);

        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.Equal("gmail_list_messages", doc.RootElement.GetProperty("tool").GetString());
        // Default approval is "required" even for a call the caller expects to read.
        Assert.Equal("required", doc.RootElement.GetProperty("approval").GetString());
    }

    [Fact]
    public async Task CallAsync_StagedWrite_Returns202Body_WithoutThrowing()
    {
        var (api, _) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.Accepted,
                """{"status":"pending_approval","tool":"gmail_send","requestId":"11111111-1111-1111-1111-111111111111","message":"Approve it in the app."}""")));

        // 202 is a success status — this must return the parsed body, not throw.
        var result = await api.CallAsync(
            "gmail_send", new Dictionary<string, object?> { ["to"] = "a@b.c" });

        Assert.Equal("pending_approval", result.Status);
        Assert.Equal("gmail_send", result.Tool);
        Assert.Equal("11111111-1111-1111-1111-111111111111", result.RequestId);
    }

    [Fact]
    public async Task CallAsync_MoneyMovingTool_ThrowsMelayaException_With403AndCode()
    {
        var (api, _) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.Forbidden,
                """{"error":"money_moving_requires_app_approval","message":"Tools that move money or trade run only from the Melaya app."}""")));

        var ex = await Assert.ThrowsAsync<MelayaException>(
            () => api.CallAsync("stripe_create_refund", approval: "none"));

        Assert.Equal(403, ex.Status);
        Assert.Equal("money_moving_requires_app_approval", ex.Code);
    }

    [Fact]
    public async Task CallStatusAsync_Done_ReturnsOkAndResult()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"requestId":"11111111-1111-1111-1111-111111111111","tool":"gmail_send","status":"done","ok":true,"result":"wrote:gmail_send"}""")));

        var result = await api.CallStatusAsync("11111111-1111-1111-1111-111111111111");

        Assert.Equal("done", result.Status);
        Assert.True(result.Ok);
        Assert.Equal("wrote:gmail_send", result.Result);
        Assert.Equal("/api/v1/private/connector-tools/calls/11111111-1111-1111-1111-111111111111",
            handler.LastRequest!.RequestUri!.AbsolutePath);
    }

    [Fact]
    public async Task CallAndWaitAsync_PollsUntilDone_WithATinyInterval()
    {
        const string requestId = "11111111-1111-1111-1111-111111111111";
        var statusCalls = 0;

        var (api, _) = MakeApi(req =>
        {
            if (req.Method == HttpMethod.Post && req.RequestUri!.AbsolutePath.EndsWith("/call"))
            {
                return Task.FromResult(FakeHttpMessageHandler.JsonResponse(HttpStatusCode.Accepted,
                    $$"""{"status":"pending_approval","tool":"gmail_send","requestId":"{{requestId}}","message":"pending"}"""));
            }

            // First two polls: still pending/running. Third poll: approved and done.
            statusCalls++;
            var body = statusCalls switch
            {
                1 => $$"""{"requestId":"{{requestId}}","tool":"gmail_send","status":"pending"}""",
                2 => $$"""{"requestId":"{{requestId}}","tool":"gmail_send","status":"running"}""",
                _ => $$"""{"requestId":"{{requestId}}","tool":"gmail_send","status":"done","ok":true,"result":"wrote:gmail_send"}""",
            };
            return Task.FromResult(FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK, body));
        });

        var outcome = await api.CallAndWaitAsync(
            "gmail_send",
            new Dictionary<string, object?> { ["to"] = "a@b.c" },
            pollIntervalMs: 5,
            timeoutMs: 5_000);

        Assert.Equal("done", outcome.Status);
        Assert.True(outcome.Ok);
        Assert.Equal("wrote:gmail_send", outcome.Result);
        Assert.Equal(3, statusCalls);
    }

    [Fact]
    public async Task CallAndWaitAsync_ReadTool_ReturnsImmediately_WithoutPolling()
    {
        var pollCount = 0;
        var (api, _) = MakeApi(req =>
        {
            if (req.RequestUri!.AbsolutePath.Contains("/calls/")) pollCount++;
            return Task.FromResult(FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"status":"done","tool":"gmail_list_messages","readOnly":true,"result":"3 unread"}"""));
        });

        var outcome = await api.CallAndWaitAsync("gmail_list_messages");

        Assert.Equal("done", outcome.Status);
        Assert.True(outcome.Ok);
        Assert.Equal("3 unread", outcome.Result);
        Assert.Equal(0, pollCount);
    }
}
