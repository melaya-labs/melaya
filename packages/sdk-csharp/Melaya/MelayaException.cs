namespace Melaya;

/// <summary>
/// Thrown for non-2xx HTTP responses or when the API envelope contains
/// <c>ok: false</c>. Mirrors the TS/Python MelayaError contract.
/// </summary>
public sealed class MelayaException : Exception
{
    /// <summary>HTTP status code (or 0 for envelope-level failures with a 2xx status).</summary>
    public int Status { get; }

    /// <summary>Machine-readable error code from <c>body.error</c>, if present.</summary>
    public string? Code { get; }

    /// <summary>Raw deserialized response body, if available.</summary>
    public object? Body { get; }

    public MelayaException(string message, int status, string? code = null, object? body = null)
        : base(message)
    {
        Status = status;
        Code   = code;
        Body   = body;
    }
}
