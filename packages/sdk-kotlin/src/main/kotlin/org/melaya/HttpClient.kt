package org.melaya

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

/**
 * Internal HTTP client.  Injects the API key as both a query parameter
 * (`?apiKey=mk_...`) and an `Authorization: Bearer` header on every call.
 */
internal class HttpClient(
    private val apiKey: String,
    private val baseUrl: String,
    internal val okHttp: OkHttpClient,
) {

    // ── URL helpers ──────────────────────────────────────────────────────────

    private fun buildUrl(path: String, query: Map<String, Any?> = emptyMap()): String {
        val base = baseUrl.trimEnd('/')
        val sb = StringBuilder("$base/${ path.trimStart('/') }?apiKey=${encode(apiKey)}")
        for ((k, v) in query) {
            if (v != null) sb.append("&${encode(k)}=${encode(v.toString())}")
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

    fun post(path: String, body: Any? = null): Any? {
        val jsonBody = when (body) {
            null -> "{}".toRequestBody(JSON_MEDIA_TYPE)
            is JSONObject -> body.toString().toRequestBody(JSON_MEDIA_TYPE)
            is Map<*, *> -> JSONObject(body).toString().toRequestBody(JSON_MEDIA_TYPE)
            else -> body.toString().toRequestBody(JSON_MEDIA_TYPE)
        }
        val req = Request.Builder()
            .url(buildUrl(path))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody)
            .build()
        return execute(req)
    }

    fun delete(path: String, query: Map<String, Any?> = emptyMap()): Any? {
        val req = Request.Builder()
            .url(buildUrl(path, query))
            .header("Authorization", "Bearer $apiKey")
            .delete()
            .build()
        return execute(req)
    }

    // ── Response parsing + envelope unwrap ──────────────────────────────────

    private fun execute(req: Request): Any? {
        val resp = okHttp.newCall(req).execute()
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
