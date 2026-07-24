<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Internal cURL-based HTTP client.
 *
 * The credential (mk_* API key or session JWT) is sent ONLY via the
 * Authorization: Bearer header — never in the URL query string, so it cannot
 * leak into access logs, proxies, or referrer headers.
 *
 * Error mapping:
 *   - { "error": "tier_insufficient", "tier": "..." }  → 403  → MelayaException (code "tier_insufficient")
 *   - { "error": "...", "message": "...", "code": "..." } → 4xx/5xx → MelayaException
 *   - { "ok": false, ... } → MelayaException (envelope-level failure)
 *
 * Retry: bounded exponential backoff with jitter on GET requests only
 *   (attempt count configurable via the client's `retries` option, default 3)
 *   for network errors, 429, and 5xx. Retry-After header is honoured on 429.
 *   POST/PUT/PATCH/DELETE are never retried (not idempotent).
 * Timeout: per-request timeout (default 30 s, configurable). Passed as CURLOPT_TIMEOUT.
 * TLS: certificate and hostname verification are always enabled.
 * Security: the apiKey/JWT is NEVER logged or included in exception messages.
 */
class HttpClient
{
    /** Maximum backoff cap in seconds. */
    private const BACKOFF_CAP_SEC = 30;

    private readonly int  $maxRetries;
    private readonly int  $timeoutSec;

    public function __construct(
        /** @var string Bearer token — either mk_* API key or JWT; never logged. */
        private readonly string $bearer,
        private readonly string $baseUrl,
        /**
         * @deprecated No longer used. The API key is sent only via the
         * Authorization header; it is never injected into the query string.
         * Retained for backward-compatible construction.
         */
        private readonly bool   $injectApiKeyParam = true,
        int                     $timeoutSec        = 30,
        int                     $maxRetries        = 3,
    ) {
        $this->maxRetries = $maxRetries;
        $this->timeoutSec = $timeoutSec;
    }

    /** @return mixed */
    public function get(string $path, array $query = []): mixed
    {
        return $this->request('GET', $path, $query, null);
    }

    /** @return mixed */
    public function post(string $path, mixed $body = null): mixed
    {
        return $this->request('POST', $path, [], $body);
    }

    /** @return mixed */
    public function put(string $path, mixed $body = null): mixed
    {
        return $this->request('PUT', $path, [], $body);
    }

    /** @return mixed */
    public function patch(string $path, mixed $body = null): mixed
    {
        return $this->request('PATCH', $path, [], $body);
    }

    /** @return mixed */
    public function delete(string $path, array $query = []): mixed
    {
        return $this->request('DELETE', $path, $query, null);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private function buildUrl(string $path, array $query): string
    {
        $base = rtrim($this->baseUrl, '/');
        $p    = '/' . ltrim($path, '/');
        // SECURITY: the credential is sent ONLY via the Authorization: Bearer
        // header (see request()). It must NEVER be added to the query string,
        // where it would leak into access logs, proxies, and referrers.
        $filtered = array_filter($query, static fn($v) => $v !== null && $v !== '');
        return $base . $p . (count($filtered) > 0 ? '?' . http_build_query($filtered) : '');
    }

    /** @return mixed */
    private function request(string $method, string $path, array $query, mixed $body): mixed
    {
        $url     = $this->buildUrl($path, $query);
        $headers = [
            'Authorization: Bearer ' . $this->bearer,
            'Accept: application/json',
            'User-Agent: melaya-php-sdk/0.2.0',
        ];

        $jsonBody = null;
        if ($body !== null) {
            $jsonBody  = json_encode($body, JSON_THROW_ON_ERROR | JSON_UNESCAPED_UNICODE);
            $headers[] = 'Content-Type: application/json';
            $headers[] = 'Content-Length: ' . strlen($jsonBody);
        }

        // Only GET requests are retried (idempotent). POST/PUT/PATCH/DELETE are
        // executed exactly once — retrying non-idempotent requests risks duplicates.
        $isIdempotent = ($method === 'GET');
        $maxAttempts  = $isIdempotent ? (max(0, $this->maxRetries) + 1) : 1;

        $attempt  = 0;
        $delaySec = 1.0;

        while (true) {
            $attempt++;
            [$status, $raw, $curlErr, $retryAfter] = $this->curlExec($method, $url, $headers, $jsonBody);

            $isNetworkErr  = ($curlErr !== '');
            $isRetryStatus = ($status === 429 || $status >= 500);

            if ($isIdempotent && ($isNetworkErr || $isRetryStatus) && $attempt < $maxAttempts) {
                // On 429 honour Retry-After if present; otherwise use exponential backoff + jitter.
                if ($status === 429 && $retryAfter > 0) {
                    $waitSec = min($retryAfter, self::BACKOFF_CAP_SEC);
                } else {
                    // jitter: uniform random in [delaySec/2 .. delaySec]
                    $waitSec = $delaySec * 0.5 + lcg_value() * $delaySec * 0.5;
                    $waitSec = min($waitSec, self::BACKOFF_CAP_SEC);
                }
                // sleep() takes integer seconds; usleep() for sub-second precision
                $waitUs = (int) round($waitSec * 1_000_000);
                usleep($waitUs);
                $delaySec = min($delaySec * 2, self::BACKOFF_CAP_SEC);
                continue;
            }

            if ($isNetworkErr) {
                throw new MelayaException('Melaya: network error (cURL)', 0);
            }

            return $this->parse($status, $raw);
        }
    }

    /**
     * Execute one cURL request.
     * @return array{int, string, string, int} [statusCode, rawBody, curlError, retryAfterSec]
     */
    private function curlExec(string $method, string $url, array $headers, ?string $jsonBody): array
    {
        $ch = curl_init($url);

        // CURLOPT_TIMEOUT is per-request wall-clock timeout in seconds.
        curl_setopt_array($ch, [
            CURLOPT_CUSTOMREQUEST  => $method,
            CURLOPT_RETURNTRANSFER => true,
            CURLOPT_TIMEOUT        => $this->timeoutSec,
            CURLOPT_SSL_VERIFYPEER => true,
            CURLOPT_SSL_VERIFYHOST => 2,
            CURLOPT_HTTPHEADER     => $headers,
            // Capture response headers so we can read Retry-After on 429
            CURLOPT_HEADERFUNCTION => static function ($ch, string $header) use (&$retryAfterRaw): int {
                if (str_starts_with(strtolower($header), 'retry-after:')) {
                    $retryAfterRaw = trim(substr($header, strlen('retry-after:')));
                }
                return strlen($header);
            },
        ]);

        if ($jsonBody !== null) {
            curl_setopt($ch, CURLOPT_POSTFIELDS, $jsonBody);
        }

        $retryAfterRaw = null;
        $raw    = (string) curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err    = curl_error($ch);
        curl_close($ch);

        // Parse Retry-After: accept integer seconds or HTTP-date
        $retryAfterSec = 0;
        if ($retryAfterRaw !== null) {
            if (is_numeric($retryAfterRaw)) {
                $retryAfterSec = (int) $retryAfterRaw;
            } elseif (($ts = strtotime($retryAfterRaw)) !== false) {
                $retryAfterSec = max(0, $ts - time());
            }
        }

        if ($raw === '' && $err !== '') {
            return [0, '', $err, 0];
        }

        return [$status, $raw, '', $retryAfterSec];
    }

    /** @return mixed */
    private function parse(int $status, string $raw): mixed
    {
        $data = null;
        if ($raw !== '') {
            try {
                $data = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
            } catch (\JsonException) {
                $data = $raw;
            }
        }

        if ($status >= 400) {
            $errorCode = is_array($data) ? ($data['error'] ?? null) : null;
            $message   = is_array($data) ? ($data['message'] ?? null) : null;
            // { error: "tier_insufficient", tier: "..." } 403
            // { error: "...", message: "...", code: "..." } 4xx/5xx
            throw new MelayaException(
                'Melaya API error: HTTP ' . $status . ($message ? " — {$message}" : ''),
                $status,
                is_string($errorCode) ? $errorCode : null,
                $data,
            );
        }

        // Envelope-level failure: ok === false
        if (is_array($data) && isset($data['ok']) && $data['ok'] === false) {
            $errorCode = $data['error'] ?? null;
            $message   = $data['message'] ?? null;
            throw new MelayaException(
                'Melaya API request failed' . ($message ? ": {$message}" : ''),
                $status,
                is_string($errorCode) ? $errorCode : null,
                $data,
            );
        }

        return $data;
    }
}
