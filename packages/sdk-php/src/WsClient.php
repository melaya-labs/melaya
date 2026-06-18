<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Minimal synchronous RFC 6455 WebSocket client over a TLS stream socket.
 *
 * No external dependencies — uses stream_socket_client() with an ssl:// context.
 * TLS verification is disabled when MELAYA_INSECURE_TLS=1.
 *
 * Usage:
 *   $ws = new WsClient('wss://wss.melaya.org/ws/ticker?apiKey=mk_...');
 *   while ($frame = $ws->readFrame()) {
 *       // $frame is the decoded JSON (array) or null on close/timeout
 *   }
 *   $ws->close();
 */
class WsClient
{
    /** @var resource|null */
    private mixed $socket = null;

    private string $host;
    private int $port;
    private string $path;

    public function __construct(private readonly string $url, private readonly int $timeoutMs = 10000)
    {
        $this->parse($url);
        $this->connect();
    }

    // ── Public ────────────────────────────────────────────────────────────────

    /**
     * Read one JSON frame from the WebSocket.
     * Returns the decoded array, or null on close/timeout/non-JSON frames.
     */
    public function readFrame(): ?array
    {
        $raw = $this->readWsFrame();
        if ($raw === null) {
            return null;
        }
        try {
            $data = json_decode($raw, true, 512, JSON_THROW_ON_ERROR);
            return is_array($data) ? $data : null;
        } catch (\JsonException) {
            return null;
        }
    }

    /** Close the underlying socket. */
    public function close(): void
    {
        if ($this->socket !== null) {
            // Send close frame (opcode 8)
            @fwrite($this->socket, $this->encodeFrame('', 0x8));
            @fclose($this->socket);
            $this->socket = null;
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private function parse(string $url): void
    {
        $parts = parse_url($url);
        if ($parts === false) {
            throw new MelayaException("WsClient: invalid URL: {$url}");
        }
        $this->host = $parts['host'] ?? '';
        $this->port = $parts['port'] ?? 443;
        $path = $parts['path'] ?? '/';
        $query = isset($parts['query']) ? '?' . $parts['query'] : '';
        $this->path = $path . $query;
    }

    private function connect(): void
    {
        $insecure = (getenv('MELAYA_INSECURE_TLS') === '1');

        $ctx = stream_context_create([
            'ssl' => [
                'verify_peer'      => !$insecure,
                'verify_peer_name' => !$insecure,
            ],
        ]);

        $remote  = "ssl://{$this->host}:{$this->port}";
        $timeoutSec = $this->timeoutMs / 1000;

        $socket = stream_socket_client(
            $remote,
            $errno,
            $errstr,
            $timeoutSec,
            STREAM_CLIENT_CONNECT,
            $ctx,
        );

        if ($socket === false) {
            throw new MelayaException("WsClient: connection failed to {$remote}: [{$errno}] {$errstr}");
        }

        // Set read timeout
        stream_set_timeout($socket, (int) floor($timeoutSec), (int) (($this->timeoutMs % 1000) * 1000));

        $this->socket = $socket;
        $this->handshake();
    }

    private function handshake(): void
    {
        $key    = base64_encode(random_bytes(16));
        $host   = $this->host . ($this->port !== 443 ? ":{$this->port}" : '');
        $request = implode("\r\n", [
            "GET {$this->path} HTTP/1.1",
            "Host: {$host}",
            "Upgrade: websocket",
            "Connection: Upgrade",
            "Sec-WebSocket-Key: {$key}",
            "Sec-WebSocket-Version: 13",
            "Origin: https://{$this->host}",
            '',
            '',
        ]);

        fwrite($this->socket, $request);

        // Read HTTP response headers
        $response = '';
        while (!feof($this->socket)) {
            $line = fgets($this->socket, 4096);
            if ($line === false) {
                break;
            }
            $response .= $line;
            if (str_ends_with($response, "\r\n\r\n")) {
                break;
            }
        }

        if (!str_contains($response, '101')) {
            throw new MelayaException("WsClient: handshake failed. Response:\n" . substr($response, 0, 500));
        }

        // Validate Sec-WebSocket-Accept
        $expectedAccept = base64_encode(sha1($key . '258EAFA5-E914-47DA-95CA-C5AB0DC85B11', true));
        if (!str_contains($response, $expectedAccept)) {
            throw new MelayaException("WsClient: invalid Sec-WebSocket-Accept header");
        }
    }

    /** Read one raw WebSocket frame payload (text frames only). Returns null on close/timeout. */
    private function readWsFrame(): ?string
    {
        if ($this->socket === null) {
            return null;
        }

        // Read first 2 bytes (FIN+opcode, mask+payload-len)
        $header = $this->readBytes(2);
        if ($header === null) {
            return null;
        }

        $b0     = ord($header[0]);
        $b1     = ord($header[1]);
        // $fin  = ($b0 & 0x80) !== 0;
        $opcode = $b0 & 0x0F;
        $masked = ($b1 & 0x80) !== 0;
        $len    = $b1 & 0x7F;

        if ($opcode === 0x8) {
            // Close frame
            $this->close();
            return null;
        }
        if ($opcode === 0x9) {
            // Ping — respond with pong
            $pingPayload = $len > 0 ? $this->readBytes($len) : '';
            fwrite($this->socket, $this->encodeFrame($pingPayload ?? '', 0xA));
            return $this->readWsFrame(); // recurse to read next frame
        }

        if ($len === 126) {
            $ext = $this->readBytes(2);
            if ($ext === null) {
                return null;
            }
            $len = unpack('n', $ext)[1];
        } elseif ($len === 127) {
            $ext = $this->readBytes(8);
            if ($ext === null) {
                return null;
            }
            // 64-bit big-endian; take lower 32 bits for practical purposes
            $hi  = unpack('N', substr($ext, 0, 4))[1];
            $lo  = unpack('N', substr($ext, 4, 4))[1];
            $len = ($hi << 32) | $lo;
        }

        $maskKey = null;
        if ($masked) {
            $maskKey = $this->readBytes(4);
            if ($maskKey === null) {
                return null;
            }
        }

        $payload = $len > 0 ? $this->readBytes($len) : '';
        if ($payload === null) {
            return null;
        }

        if ($masked && $maskKey !== null) {
            for ($i = 0; $i < strlen($payload); $i++) {
                $payload[$i] = chr(ord($payload[$i]) ^ ord($maskKey[$i % 4]));
            }
        }

        // Only handle text and binary frames (opcodes 1, 2)
        if ($opcode !== 0x1 && $opcode !== 0x2 && $opcode !== 0x0) {
            return $this->readWsFrame();
        }

        return $payload;
    }

    /** Read exactly $n bytes from the socket, return null on EOF/timeout. */
    private function readBytes(int $n): ?string
    {
        $buf = '';
        $remaining = $n;
        while ($remaining > 0) {
            $chunk = fread($this->socket, $remaining);
            if ($chunk === false || $chunk === '') {
                $meta = stream_get_meta_data($this->socket);
                if ($meta['timed_out'] || feof($this->socket)) {
                    return null;
                }
                continue;
            }
            $buf .= $chunk;
            $remaining -= strlen($chunk);
        }
        return $buf;
    }

    /** Encode a client-to-server WebSocket frame (always masked per RFC 6455). */
    private function encodeFrame(string $payload, int $opcode = 0x1): string
    {
        $len    = strlen($payload);
        $header = chr(0x80 | $opcode); // FIN + opcode

        if ($len <= 125) {
            $header .= chr(0x80 | $len);
        } elseif ($len <= 65535) {
            $header .= chr(0x80 | 126) . pack('n', $len);
        } else {
            $header .= chr(0x80 | 127) . pack('NN', 0, $len);
        }

        $maskKey  = random_bytes(4);
        $masked   = '';
        for ($i = 0; $i < $len; $i++) {
            $masked .= chr(ord($payload[$i]) ^ ord($maskKey[$i % 4]));
        }

        return $header . $maskKey . $masked;
    }
}
