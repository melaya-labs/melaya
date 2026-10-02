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

    // ── Several accounts per project connector ───────────────────────────────

    private const string AccountsJson =
        """[{"id":"acc_1","label":"Shop EU","isDefault":true,"createdAt":"2026-09-30T10:00:00.000Z"}]""";

    private static (ConnectorsApi api, FakeHttpMessageHandler handler) MakeAccountsApi()
        => MakeApi(_ => Task.FromResult(FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK, AccountsJson)));

    [Fact]
    public async Task AccountsAsync_GetsProjectAccounts_AndEscapesProjectWithSpace()
    {
        var (api, handler) = MakeAccountsApi();

        var list = await api.AccountsAsync("my project", "shopify");

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Get, req.Method);
        Assert.Equal("/api/v1/private/projects/my%20project/connectors/shopify/accounts", req.RequestUri!.AbsolutePath);
        var acc = Assert.Single(list);
        Assert.Equal("acc_1", acc.Id);
        Assert.Equal("Shop EU", acc.Label);
        Assert.True(acc.IsDefault);
        Assert.Equal("2026-09-30T10:00:00.000Z", acc.CreatedAt);
    }

    [Fact]
    public async Task AddAccountAsync_PostsFieldsAndOptions()
    {
        var (api, handler) = MakeAccountsApi();

        await api.AddAccountAsync(
            "acme", "shopify",
            new Dictionary<string, string> { ["SHOPIFY_TOKEN"] = "shpat_x" },
            label: "Shop EU",
            makeDefault: false);

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.Equal("/api/v1/private/projects/acme/connectors/shopify/accounts", req.RequestUri!.AbsolutePath);
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        var root = doc.RootElement;
        Assert.Equal("Shop EU", root.GetProperty("label").GetString());
        Assert.False(root.GetProperty("makeDefault").GetBoolean());
        Assert.False(root.TryGetProperty("currentLabel", out _));
        Assert.Equal("shpat_x", root.GetProperty("fields").GetProperty("SHOPIFY_TOKEN").GetString());
    }

    [Fact]
    public async Task SetDefaultAccountAsync_PutsAccountId()
    {
        var (api, handler) = MakeAccountsApi();

        await api.SetDefaultAccountAsync("acme", "shopify", "acc_1");

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Put, req.Method);
        Assert.Equal("/api/v1/private/projects/acme/connectors/shopify/accounts/default", req.RequestUri!.AbsolutePath);
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.Equal("acc_1", doc.RootElement.GetProperty("accountId").GetString());
    }

    [Fact]
    public async Task RenameAccountAsync_PutsLabel()
    {
        var (api, handler) = MakeAccountsApi();

        await api.RenameAccountAsync("acme", "shopify", "acc_1", "Shop Europe");

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Put, req.Method);
        Assert.Equal("/api/v1/private/projects/acme/connectors/shopify/accounts/acc_1", req.RequestUri!.AbsolutePath);
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.Equal("Shop Europe", doc.RootElement.GetProperty("label").GetString());
    }

    [Fact]
    public async Task RemoveAccountAsync_SendsDelete_WithoutBody()
    {
        var (api, handler) = MakeAccountsApi();

        await api.RemoveAccountAsync("acme", "shopify", "acc_1");

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Delete, req.Method);
        Assert.Equal("/api/v1/private/projects/acme/connectors/shopify/accounts/acc_1", req.RequestUri!.AbsolutePath);
        Assert.Null(handler.LastRequestBodyText);
    }
}
