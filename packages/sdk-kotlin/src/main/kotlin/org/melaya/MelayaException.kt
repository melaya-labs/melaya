package org.melaya

/**
 * Thrown for every non-2xx REST response and for `ok: false` envelope failures.
 *
 * @param message Human-readable description (includes HTTP status and error code).
 * @param status  HTTP status code.
 * @param code    The `error` field from the JSON envelope, or null.
 * @param body    The raw parsed response body, or null.
 */
class MelayaException(
    message: String,
    val status: Int,
    val code: String? = null,
    val body: Any? = null,
) : RuntimeException(message)
