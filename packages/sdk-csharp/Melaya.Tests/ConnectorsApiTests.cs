using System.Net;
using System.Text.Json;

namespace Melaya.Tests;

public class ConnectorsApiTests
{
    private static (ConnectorsApi api, FakeHttpMessageHandler handler) MakeApi(
        Func<HttpRequestMessage, Task<HttpResponseMessage>> responder)
    {
        var handler = new FakeHttpMessageHandler(responder);
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        return (new ConnectorsApi(http), handler);
    }

    [Fact]
    public async Task ApplyPersonalAsync_IsABridgedPost_WithPathParamAndBody()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK, """{"ok":true}""")));

        var result = await api.ApplyPersonalAsync("acme", "google", new[] { "gmail", "drive" });

        Assert.True(result.Ok);

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Post, req.Method);
        // Both path params (project, service) are in the URL; the non-path field (googleCapabilities) is in the JSON body.
        Assert.Equal("/api/v1/private/projects/acme/connectors/google/apply-personal", req.RequestUri!.AbsolutePath);

        Assert.NotNull(handler.LastRequestBodyText);
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        var caps = doc.RootElement.GetProperty("googleCapabilities");
        Assert.Equal(JsonValueKind.Array, caps.ValueKind);
        Assert.Equal(2, caps.GetArrayLength());
        Assert.Equal("gmail", caps[0].GetString());
        Assert.Equal("drive", caps[1].GetString());
    }

    [Fact]
    public async Task GoogleDisconnectAsync_SendsDelete_WithJsonBody()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK, """{"ok":true}""")));

        var result = await api.GoogleDisconnectAsync("acme", "5f8d0d557b6c4b001c2a3e4f", "gmail");

        Assert.True(result.Ok);

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Delete, req.Method);
        Assert.Equal("/api/v1/private/projects/acme/connectors/google/access", req.RequestUri!.AbsolutePath);

        // DELETE with a JSON body — the target (accountId/capability) travels in the body, not the query string.
        Assert.Empty(req.RequestUri.Query);
        Assert.NotNull(req.Content);
        Assert.NotNull(handler.LastRequestBodyText);

        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.Equal("5f8d0d557b6c4b001c2a3e4f", doc.RootElement.GetProperty("accountId").GetString());
        Assert.Equal("gmail", doc.RootElement.GetProperty("capability").GetString());
    }
}
