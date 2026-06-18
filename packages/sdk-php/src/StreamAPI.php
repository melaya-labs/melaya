<?php

declare(strict_types=1);

namespace Melaya;

/**
 * WebSocket streaming API.
 *
 * Public streams: open wss://wss.melaya.org/ws/<path>?apiKey=mk_...
 * Private streams: mint a wsTicket via POST /api/v1/private/private-ticket, then
 *                  open wss://wss.melaya.org/ws/<stream>?wsTicket=<ticket>.
 *
 * Returns a WsClient. Iterate frames with:
 *   while ($frame = $ws->readFrame()) { ... }
 * Close with: $ws->close();
 */
class StreamAPI
{
    public function __construct(
        private readonly string $apiKey,
        private readonly string $wsUrl,
        private readonly HttpClient $http,
    ) {}

    /** Live ticker frames. */
    public function ticker(string $exchange, string $symbol, ?string $market = null): WsClient
    {
        return $this->open('/ws/ticker', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'market' => $market],
            fn($v) => $v !== null,
        ));
    }

    /** Live order-book frames. */
    public function orderbook(string $exchange, string $symbol, ?int $limit = null, ?string $market = null): WsClient
    {
        return $this->open('/ws/orderbook', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'limit' => $limit, 'market' => $market],
            fn($v) => $v !== null,
        ));
    }

    /** Live OHLCV candle frames. */
    public function ohlcv(string $exchange, string $symbol, string $timeframe, ?string $market = null): WsClient
    {
        return $this->open('/ws/ohlcv', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'timeframe' => $timeframe, 'market' => $market],
            fn($v) => $v !== null,
        ));
    }

    /** Live public-trade frames. */
    public function trades(string $exchange, string $symbol, ?string $market = null): WsClient
    {
        return $this->open('/ws/public-trades', array_filter(
            ['exchange' => $exchange, 'symbol' => $symbol, 'market' => $market],
            fn($v) => $v !== null,
        ));
    }

    /** Cross-exchange liquidation firehose. Omit exchange for all venues. */
    public function liquidations(?string $exchange = null): WsClient
    {
        $params = $exchange !== null ? ['exchange' => $exchange] : [];
        return $this->open('/ws/liquidations', $params);
    }

    // ── Private feeds (authenticated; ticket-minted) ──────────────────────────

    /**
     * Live strategy events for your account (cycle markers, agent messages,
     * approval requests, executions, status). Mints a ticket, opens /ws/strategies.
     */
    public function strategies(): WsClient
    {
        return $this->openPrivate('/ws/strategies', 'strategies', []);
    }

    /**
     * Live private account feed for one connected exchange key (balance,
     * positions, your orders/fills). Pass the apiKeyId from account.keys().
     */
    public function private(
        string $exchange,
        ?string $market = null,
        ?string $apiKeyId = null,
        ?string $keyId = null,
        ?string $symbol = null,
    ): WsClient {
        return $this->openPrivate('/ws/private', 'private', array_filter([
            'exchange' => $exchange,
            'market'   => $market,
            'apiKeyId' => $apiKeyId,
            'keyId'    => $keyId,
            'symbol'   => $symbol,
        ], fn($v) => $v !== null));
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private function open(string $path, array $params): WsClient
    {
        $params['apiKey'] = $this->apiKey;
        $base = rtrim($this->wsUrl, '/');
        $url  = $base . $path . '?' . http_build_query($params);
        return new WsClient($url);
    }

    private function openPrivate(string $path, string $stream, array $body): WsClient
    {
        $ticketBody = array_merge(['stream' => $stream], $body);
        $resp   = $this->http->post('/api/v1/private/private-ticket', $ticketBody);
        $ticket = $resp['wsTicket'];
        $base   = rtrim($this->wsUrl, '/');
        $url    = $base . $path . '?wsTicket=' . urlencode($ticket);
        return new WsClient($url);
    }
}
