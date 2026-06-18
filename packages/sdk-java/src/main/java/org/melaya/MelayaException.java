package org.melaya;

/**
 * Thrown for non-2xx HTTP responses or when the API envelope returns {@code ok: false}.
 */
public class MelayaException extends RuntimeException {

    private final int status;
    private final String code;
    private final Object body;

    public MelayaException(String message, int status, String code, Object body) {
        super(message);
        this.status = status;
        this.code = code;
        this.body = body;
    }

    /** HTTP status code (e.g. 400, 401, 429). */
    public int getStatus() {
        return status;
    }

    /** Machine-readable error code from the API envelope (may be {@code null}). */
    public String getCode() {
        return code;
    }

    /** Raw parsed response body (may be {@code null}). */
    public Object getBody() {
        return body;
    }

    @Override
    public String toString() {
        return "MelayaException{status=" + status + ", code=" + code + ", message=" + getMessage() + "}";
    }
}
