using System.Net;
using System.Text.Json;

namespace Melaya.Tests;

public class PipelinesApiTests
{
    private static (PipelinesApi api, FakeHttpMessageHandler handler) MakeApi(
        Func<HttpRequestMessage, Task<HttpResponseMessage>> responder)
    {
        var handler = new FakeHttpMessageHandler(responder);
        var http = new MelayaHttpClient("mk_test", "https://unit-test.invalid", handler);
        return (new PipelinesApi(http), handler);
    }

    [Fact]
    public async Task RunAsync_SendsRunInputsInBody_AndEchoesThemBack()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"run_id":"r_abc123","queued":true,"run_inputs":{"brief":"hello"}}""")));

        var result = await api.RunAsync("my-pipeline", new PipelineRunRequest
        {
            Project = "acme",
            RunInputs = new PipelineRunInputs
            {
                Brief = "Summarize the attached screenshot.",
                Values = new Dictionary<string, object>
                {
                    ["screenshot"] = new { file_id = "f_123" },
                },
            },
        });

        // Request shape: path + JSON body carrying run_inputs.
        Assert.NotNull(handler.LastRequest);
        Assert.Equal(HttpMethod.Post, handler.LastRequest!.Method);
        Assert.Equal("/api/v1/private/pipelines/my-pipeline/run", handler.LastRequest.RequestUri!.AbsolutePath);

        Assert.NotNull(handler.LastRequestBodyText);
        using var doc = JsonDocument.Parse(handler.LastRequestBodyText!);
        var root = doc.RootElement;
        Assert.Equal("acme", root.GetProperty("project").GetString());

        var runInputs = root.GetProperty("run_inputs");
        Assert.Equal("Summarize the attached screenshot.", runInputs.GetProperty("brief").GetString());
        Assert.Equal("f_123", runInputs.GetProperty("values").GetProperty("screenshot").GetProperty("file_id").GetString());

        // Response: run_id/queued deserialize normally, run_inputs echo comes back as JsonElement.
        Assert.Equal("r_abc123", result.RunId);
        Assert.True(result.Queued);
        Assert.NotNull(result.RunInputs);
        Assert.Equal("hello", result.RunInputs!.Value.GetProperty("brief").GetString());
    }

    [Fact]
    public async Task UploadRunFileAsync_SendsMultipartFormData_WithFilenameAndBoundary()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK, """{"file_id":"f_upload_1"}""")));

        var bytes = new byte[] { 1, 2, 3, 4, 5 };
        var result = await api.UploadRunFileAsync("my-pipeline", "screenshot", bytes, "shot.png", contentType: "image/png");

        Assert.Equal("f_upload_1", result.FileId);

        var req = handler.LastRequest!;
        Assert.Equal(HttpMethod.Post, req.Method);
        Assert.Equal("/api/v1/private/pipelines/my-pipeline/run-files", req.RequestUri!.AbsolutePath);
        Assert.Contains("key=screenshot", req.RequestUri.Query);

        // Content-Type must be multipart/form-data with a boundary parameter.
        Assert.NotNull(req.Content);
        var contentType = req.Content!.Headers.ContentType;
        Assert.NotNull(contentType);
        Assert.Equal("multipart/form-data", contentType!.MediaType);
        var boundary = contentType.Parameters.FirstOrDefault(p => p.Name == "boundary");
        Assert.NotNull(boundary);
        Assert.False(string.IsNullOrEmpty(boundary!.Value));

        // The body must contain a single file part named "file" with the given filename.
        Assert.NotNull(handler.LastRequestBodyText);
        var bodyText = handler.LastRequestBodyText!.Replace("\"", "");
        Assert.Contains("name=file", bodyText);
        Assert.Contains("filename=shot.png", bodyText);
    }

    [Fact]
    public async Task RunInputFileAsync_ReturnsRawBytes_NotJsonParsed()
    {
        var expectedBytes = new byte[] { 0x00, 0xFF, 0x10, 0x20, 0x7B, 0x7D }; // includes stray { }

        var (api, _) = MakeApi(_ => Task.FromResult(new HttpResponseMessage(HttpStatusCode.OK)
        {
            Content = new ByteArrayContent(expectedBytes)
            {
                Headers = { ContentType = new System.Net.Http.Headers.MediaTypeHeaderValue("application/octet-stream") },
            },
        }));

        var bytes = await api.RunInputFileAsync("my-pipeline", "abc1234567890def", 0);

        Assert.Equal(expectedBytes, bytes);
    }

    [Fact]
    public async Task RunInputFileAsync_NonSuccessStatus_ThrowsMelayaExceptionWithErrorCode()
    {
        var (api, _) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.NotFound, """{"ok":false,"error":"not_found"}""")));

        var ex = await Assert.ThrowsAsync<MelayaException>(
            () => api.RunInputFileAsync("my-pipeline", "abc1234567890def", 0));

        Assert.Equal(404, ex.Status);
        Assert.Equal("not_found", ex.Code);
    }

    [Fact]
    public async Task ProjectToolCallsAsync_EncodesOnlyProvidedFilters()
    {
        var (api, handler) = MakeApi(_ => Task.FromResult(
            FakeHttpMessageHandler.JsonResponse(HttpStatusCode.OK,
                """{"items":[],"nextCursor":null,"capped":false}""")));

        var result = await api.ProjectToolCallsAsync(
            "acme",
            limit: 10,
            status: "error",
            sort: "recent");

        var req = handler.LastRequest!;
        Assert.Equal("/api/v1/private/projects/acme/tool-calls", req.RequestUri!.AbsolutePath);

        var query = req.RequestUri.Query;
        Assert.Contains("limit=10", query);
        Assert.Contains("status=error", query);
        Assert.Contains("sort=recent", query);
        // Filters that were not passed must be omitted entirely, not sent empty.
        Assert.DoesNotContain("tool=", query);
        Assert.DoesNotContain("agent=", query);
        Assert.DoesNotContain("beforeCreatedAt=", query);

        Assert.NotNull(result.Items);
        Assert.Empty(result.Items!);
        Assert.False(result.Capped);
    }
}
