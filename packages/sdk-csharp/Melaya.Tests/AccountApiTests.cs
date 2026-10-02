using System.Net;

namespace Melaya.Tests;

public class AccountApiTests
{
    private static (AccountApi api, FakeHttpMessageHandler handler) MakeApi(string json, HttpStatusCode status = HttpStatusCode.OK)
    {
        var handler = new FakeHttpMessageHandler(status, json);
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        return (new AccountApi(http), handler);
    }

    private static void AssertRequest(FakeHttpMessageHandler handler, HttpMethod method, string path)
    {
        var req = handler.LastRequest!;
        Assert.Equal(method, req.Method);
        Assert.Equal(path, req.RequestUri!.AbsolutePath);
    }

    [Fact]
    public async Task RotateApiKeyAsync_PostsEmptyObject_AndReturnsNewKey()
    {
        var (api, handler) = MakeApi("""{"apiKey":"mk_new_123"}""");

        var res = await api.RotateApiKeyAsync();

        AssertRequest(handler, HttpMethod.Post, "/api/v1/private/api-key");
        Assert.Equal("{}", handler.LastRequestBodyText);
        Assert.Equal("mk_new_123", res.ApiKey);
    }

    [Fact]
    public async Task RevokeApiKeyAsync_SendsDelete()
    {
        var (api, handler) = MakeApi("""{"ok":true}""");

        var res = await api.RevokeApiKeyAsync();

        AssertRequest(handler, HttpMethod.Delete, "/api/v1/private/api-key");
        Assert.Null(handler.LastRequestBodyText);
        Assert.True(res.Ok);
    }

    [Fact]
    public async Task ApiKeyUsageAsync_GetsUsagePath()
    {
        var (api, handler) = MakeApi("""{"requests":42}""");

        var res = await api.ApiKeyUsageAsync();

        AssertRequest(handler, HttpMethod.Get, "/api/v1/private/api-key/usage");
        Assert.Equal(42, res.GetProperty("requests").GetInt32());
    }
}
