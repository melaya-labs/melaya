<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Internal cURL-based HTTP client.
 *
 * Injects the API key as both `?apiKey=` query param AND `Authorization: Bearer` header.
 * Throws MelayaException on HTTP >= 400 or on `ok: false` in the JSON envelope.
 * TLS verification is disabled when MELAYA_INSECURE_TLS=1 (dev/CI only).
 */
class HttpClient
{
    private bool $insecure;

    public function __construct(
        private readonly string $apiKey,
        private readonly string $baseUrl,
    ) {
        $this->insecure = (getenv('MELAYA_INSECURE_TLS') === '1');
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
    public function delete(string $path, array $query = []): mixed
    {
        return $this->request('DELETE', $path, $query, null);
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private function buildUrl(string $path, array $query): string
    {
        $base = rtrim($this->baseUrl, '/');
        $p    = '/' . ltrim($path, '/');
        $query['apiKey'] = $this->apiKey;
        return $base . $p . '?' . http_build_query(
            array_filter($query, fn($v) => $v !== null && $v !== ''),
        );
    }

    /** @return mixed */
    private function request(string $method, string $path, array $query, mixed $body): mixed
    {
        $url  = $this->buildUrl($path, $query);
        $ch   = curl_init($url);

        $headers = [
            'Authorization: Bearer ' . $this->apiKey,
            'Accept: application/json',
        ];

        curl_setopt($ch, CURLOPT_CUSTOMREQUEST, $method);
        curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
        curl_setopt($ch, CURLOPT_TIMEOUT, 30);
        curl_setopt($ch, CURLOPT_SSL_VERIFYPEER, !$this->insecure);
        curl_setopt($ch, CURLOPT_SSL_VERIFYHOST, $this->insecure ? 0 : 2);

        if ($body !== null) {
            $json = json_encode($body, JSON_THROW_ON_ERROR);
            curl_setopt($ch, CURLOPT_POSTFIELDS, $json);
            $headers[] = 'Content-Type: application/json';
            $headers[] = 'Content-Length: ' . strlen($json);
        }

        curl_setopt($ch, CURLOPT_HTTPHEADER, $headers);

        $raw    = curl_exec($ch);
        $status = (int) curl_getinfo($ch, CURLINFO_HTTP_CODE);
        $err    = curl_error($ch);
        curl_close($ch);

        if ($raw === false) {
            throw new MelayaException("Melaya: cURL error: {$err}", 0);
        }

        // Parse JSON
        $data = null;
        if ($raw !== '') {
            try {
                $data = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
            } catch (\JsonException) {
                $data = $raw;
            }
        }

        // HTTP-level failure
        if ($status >= 400) {
            $errorCode = is_array($data) ? ($data['error'] ?? null) : null;
            throw new MelayaException(
                "Melaya API {$status}" . ($errorCode ? " ({$errorCode})" : ''),
                $status,
                $errorCode,
                $data,
            );
        }

        // Envelope-level failure: ok === false
        if (is_array($data) && isset($data['ok']) && $data['ok'] === false) {
            $errorCode = $data['error'] ?? null;
            throw new MelayaException(
                'Melaya API request failed' . ($errorCode ? ": {$errorCode}" : ''),
                $status,
                $errorCode,
                $data,
            );
        }

        return $data;
    }
}
