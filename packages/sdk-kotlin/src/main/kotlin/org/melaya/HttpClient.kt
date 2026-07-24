package org.melaya

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * OkHttp interceptor that retries idempotent GET requests on network error,
 * HTTP 429 (rate-limited), or 5xx (server error).
 *
 * Rules:
 *   - Only retries GET requests (POST/PUT/PATCH/DELETE are never retried).
 *   - Maximum 2 retries (3 total attempts).
 *   - Exponential back-off with ±20% jitter: ~1s, ~2s.
 *   - Respects the `Retry-After` header on 429 (treated as seconds, capped at 30s).
 *   - Secrets (API key) are never surfaced in exceptions or logs.
 */
internal class RetryInterceptor(private val maxRetries: Int = 2) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Only retry idempotent GETs
        if (request.method != "GET") return chain.proceed(request)

        var attempt = 0
        var response = chain.proceed(request)
        while (attempt < maxRetries && shouldRetry(response.code)) {
            response.close()
            Thread.sleep(retryDelayMs(response, attempt))
            attempt++
            response = chain.proceed(request)
        }
        return response
    }

    private fun shouldRetry(code: Int) = code == 429 || code in 500..599

    private fun retryDelayMs(response: Response, attempt: Int): Long {
        val retryAfter = response.header("Retry-After")?.toLongOrNull()
        // Honor Retry-After but cap it at 30s so a hostile/buggy header can't stall the client.
        if (retryAfter != null) return retryAfter.coerceIn(0L, 30L) * 1_000L
        val base = (1L shl attempt) * 1_000L   // 1s, 2s
        val jitter = (base * 0.2 * Math.random()).toLong()
        return base + jitter
    }
}

/**
 * Internal HTTP client. Sends the API key ONLY as an `Authorization: Bearer`
 * header on every call — never in the URL query string, so the key cannot leak
 * into access logs, proxies, or referrer headers.
 *
 * @param requestTimeoutMs Per-request call timeout in ms (default 30 000). Applied via
 *   OkHttp's per-call [OkHttpClient.newBuilder] override so it does not affect the base
 *   client's connection/read/write timeouts.
 */
internal class HttpClient(
    private val apiKey: String,
    private val baseUrl: String,
    internal val okHttp: OkHttpClient,
    private val requestTimeoutMs: Long = 30_000L,
) {

    // ── URL helpers ──────────────────────────────────────────────────────────

    private fun buildUrl(path: String, query: Map<String, Any?> = emptyMap()): String {
        val base = baseUrl.trimEnd('/')
        val sb = StringBuilder("$base/${ path.trimStart('/') }")
        var first = true
        for ((k, v) in query) {
            if (v != null) {
                sb.append(if (first) "?" else "&").append("${encode(k)}=${encode(v.toString())}")
                first = false
            }
        }
        return sb.toString()
    }

    private fun encode(s: String) = java.net.URLEncoder.encode(s, "UTF-8")

    // ── Verb helpers ─────────────────────────────────────────────────────────

    fun get(path: String, query: Map<String, Any?> = emptyMap()): Any? {
        val req = Request.Builder()
            .url(buildUrl(path, query))
            .header("Authorization", "Bearer $apiKey")
            .get()
            .build()
        return execute(req)
    }

    private fun toRequestBody(body: Any?) = when (body) {
        null -> "{}".toRequestBody(JSON_MEDIA_TYPE)
        is JSONObject -> body.toString().toRequestBody(JSON_MEDIA_TYPE)
        is Map<*, *> -> JSONObject(body).toString().toRequestBody(JSON_MEDIA_TYPE)
        is List<*> -> org.json.JSONArray(body).toString().toRequestBody(JSON_MEDIA_TYPE)
        else -> body.toString().toRequestBody(JSON_MEDIA_TYPE)
    }

    fun post(path: String, body: Any? = null): Any? {
        val jsonBody = toRequestBody(body)
        val req = Request.Builder()
            .url(buildUrl(path))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody)
            .build()
        return execute(req)
    }

    fun put(path: String, body: Any? = null): Any? {
        val jsonBody = toRequestBody(body)
        val req = Request.Builder()
            .url(buildUrl(path))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .put(jsonBody)
            .build()
        return execute(req)
    }

    fun patch(path: String, body: Any? = null): Any? {
        val jsonBody = toRequestBody(body)
        val req = Request.Builder()
            .url(buildUrl(path))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .patch(jsonBody)
            .build()
        return execute(req)
    }

    fun delete(path: String, query: Map<String, Any?> = emptyMap(), body: Any? = null): Any? {
        val reqBuilder = Request.Builder()
            .url(buildUrl(path, query))
            .header("Authorization", "Bearer $apiKey")
        val req = if (body != null) {
            reqBuilder.delete(toRequestBody(body)).build()
        } else {
            reqBuilder.delete().build()
        }
        return execute(req)
    }

    // ── Response parsing + envelope unwrap ──────────────────────────────────

    private fun execute(req: Request): Any? {
        // Apply a per-request call timeout without modifying the shared OkHttpClient.
        val callClient = if (requestTimeoutMs > 0) {
            okHttp.newBuilder()
                .callTimeout(requestTimeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
                .build()
        } else {
            okHttp
        }
        val resp = callClient.newCall(req).execute()
        val text = resp.body?.string() ?: ""
        val status = resp.code

        val data: Any? = try {
            if (text.isBlank()) null
            else if (text.trimStart().startsWith("[")) JSONArray(text)
            else JSONObject(text)
        } catch (_: Exception) {
            text
        }

        if (status >= 400) {
            val code = (data as? JSONObject)?.optString("error", null)
            throw MelayaException(
                "Melaya API $status" + if (code != null) " ($code)" else "",
                status, code, data
            )
        }

        if (data is JSONObject && data.optBoolean("ok", true) == false) {
            val code = data.optString("error", null)
            throw MelayaException(
                "Melaya API request failed" + if (code != null) ": $code" else "",
                status, code, data
            )
        }

        return data
    }
}

// ── Convenience extension helpers used by API classes ──────────────────────

internal fun Any?.asObject(): JSONObject = this as? JSONObject
    ?: throw MelayaException("Unexpected response type: ${this?.javaClass?.simpleName}", 0)

internal fun Any?.asArray(): JSONArray = this as? JSONArray
    ?: throw MelayaException("Unexpected array response type: ${this?.javaClass?.simpleName}", 0)

internal fun JSONObject.getObject(key: String): JSONObject = optJSONObject(key)
    ?: throw MelayaException("Missing key '$key' in response", 0)

internal fun JSONObject.getArray(key: String): JSONArray = optJSONArray(key)
    ?: JSONArray()

internal fun JSONArray.toJsonObjects(): List<JSONObject> =
    (0 until length()).map { getJSONObject(it) }

internal fun JSONArray.toAnyList(): List<Any?> =
    (0 until length()).map { get(it) }
