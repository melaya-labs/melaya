using System.Net;
using System.Text.Json;

namespace Melaya.Tests;

public class CredentialsApiTests
{
    private const string AccountsJson =
        """[{"id":"acc_1","label":"Shop EU","isDefault":true,"createdAt":"2026-09-30T10:00:00.000Z"},{"id":"current","label":"Main","isDefault":false,"createdAt":null}]""";

    private static (CredentialsApi api, FakeHttpMessageHandler handler) MakeApi(string json, HttpStatusCode status = HttpStatusCode.OK)
    {
        var handler = new FakeHttpMessageHandler(status, json);
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        return (new CredentialsApi(http), handler);
    }

    private static void AssertRequest(FakeHttpMessageHandler handler, HttpMethod method, string path)
    {
        var req = handler.LastRequest!;
        Assert.Equal(method, req.Method);
        Assert.Equal(path, req.RequestUri!.AbsolutePath);
    }

    [Fact]
    public async Task AccountsAsync_GetsAccounts_AndDecodesThem()
    {
        var (api, handler) = MakeApi(AccountsJson);

        var list = await api.AccountsAsync("shopify");

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/credentials/shopify/accounts");
        Assert.Equal(2, list.Count);
        Assert.Equal("acc_1", list[0].Id);
        Assert.Equal("Shop EU", list[0].Label);
        Assert.True(list[0].IsDefault);
        Assert.Equal("2026-09-30T10:00:00.000Z", list[0].CreatedAt);
        Assert.Equal("current", list[1].Id);
        Assert.False(list[1].IsDefault);
        Assert.Null(list[1].CreatedAt);
    }

    [Fact]
    public async Task AccountsAsync_EscapesServiceWithSpace()
    {
        var (api, handler) = MakeApi("[]");

        await api.AccountsAsync("my service");

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/credentials/my%20service/accounts");
    }

    [Fact]
    public async Task AddAccountAsync_PostsFieldsAndOptions()
    {
        var (api, handler) = MakeApi(AccountsJson);

        var list = await api.AddAccountAsync(
            "shopify",
            new Dictionary<string, string> { ["SHOPIFY_TOKEN"] = "shpat_x", ["SHOPIFY_DOMAIN"] = "eu.myshopify.com" },
            label: "Shop EU",
            currentLabel: "Main",
            makeDefault: true);

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/credentials/shopify/accounts");
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        var root = doc.RootElement;
        Assert.Equal("Shop EU", root.GetProperty("label").GetString());
        Assert.Equal("Main", root.GetProperty("currentLabel").GetString());
        Assert.True(root.GetProperty("makeDefault").GetBoolean());
        var fields = root.GetProperty("fields");
        Assert.Equal("shpat_x", fields.GetProperty("SHOPIFY_TOKEN").GetString());
        Assert.Equal("eu.myshopify.com", fields.GetProperty("SHOPIFY_DOMAIN").GetString());
        Assert.Equal(2, list.Count);
    }

    [Fact]
    public async Task AddAccountAsync_OmitsNullOptions()
    {
        var (api, handler) = MakeApi(AccountsJson);

        await api.AddAccountAsync("shopify", new Dictionary<string, string> { ["SHOPIFY_TOKEN"] = "shpat_x" });

        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        var root = doc.RootElement;
        Assert.True(root.TryGetProperty("fields", out _));
        Assert.False(root.TryGetProperty("label", out _));
        Assert.False(root.TryGetProperty("currentLabel", out _));
        Assert.False(root.TryGetProperty("makeDefault", out _));
    }

    [Fact]
    public async Task SetDefaultAccountAsync_PutsAccountId()
    {
        var (api, handler) = MakeApi(AccountsJson);

        var list = await api.SetDefaultAccountAsync("shopify", "acc_1");

        AssertRequest(handler, HttpMethod.Put, "/api/v1/private/credentials/shopify/accounts/default");
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.Equal("acc_1", doc.RootElement.GetProperty("accountId").GetString());
        Assert.True(list[0].IsDefault);
    }

    [Fact]
    public async Task IdentifyAccountAsync_PostsEmptyObject()
    {
        var (api, handler) = MakeApi(AccountsJson);

        await api.IdentifyAccountAsync("shopify", "acc 1");

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/credentials/shopify/accounts/acc%201/identify");
        Assert.Equal("{}", handler.LastRequestBodyText);
    }

    [Fact]
    public async Task RenameAccountAsync_PutsLabel()
    {
        var (api, handler) = MakeApi(AccountsJson);

        await api.RenameAccountAsync("shopify", "acc_1", "Shop Europe");

        AssertRequest(handler, HttpMethod.Put, "/api/v1/private/credentials/shopify/accounts/acc_1");
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        Assert.Equal("Shop Europe", doc.RootElement.GetProperty("label").GetString());
    }

    [Fact]
    public async Task RemoveAccountAsync_SendsDelete_WithoutBody()
    {
        var (api, handler) = MakeApi("""[{"id":"current","label":"Main","isDefault":true,"createdAt":null}]""");

        var list = await api.RemoveAccountAsync("shopify", "acc_1");

        AssertRequest(handler, HttpMethod.Delete, "/api/v1/private/credentials/shopify/accounts/acc_1");
        Assert.Null(handler.LastRequestBodyText);
        Assert.Equal("current", Assert.Single(list).Id);
    }
}
