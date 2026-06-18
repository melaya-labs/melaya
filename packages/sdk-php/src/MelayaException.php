<?php

declare(strict_types=1);

namespace Melaya;

use RuntimeException;

/**
 * Thrown for non-2xx HTTP responses or when the API returns `ok: false`.
 *
 * NOTE: We do NOT redeclare $code or $message here — Exception already owns those.
 * Use $errorCode for the API error code string and $status for HTTP status.
 */
class MelayaException extends RuntimeException
{
    /** HTTP status code (0 for transport/cURL errors). */
    public readonly int $status;

    /** The `error` field from the API response envelope (e.g. "COLD_CACHE"). */
    public readonly ?string $errorCode;

    /** The raw parsed response body, if available. */
    public readonly mixed $body;

    public function __construct(
        string $message,
        int $status = 0,
        ?string $errorCode = null,
        mixed $body = null,
    ) {
        parent::__construct($message);
        $this->status    = $status;
        $this->errorCode = $errorCode;
        $this->body      = $body;
    }
}
