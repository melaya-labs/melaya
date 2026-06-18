<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Paper-trading (sim broker) API.
 *
 * The sim broker synthesises fills from Melaya's live ticker tape and keeps a
 * virtual wallet per strategy — no venue-side state ever changes, no exchange
 * credentials are needed. Every call is scoped to a strategyId.
 */
class SimAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /** Paper accounts (one virtual wallet per paper strategy). */
    public function listAccounts(): array
    {
        $r = $this->http->get('/api/v1/private/sim/list-accounts');
        if (is_array($r) && array_is_list($r)) {
            return $r;
        }
        return $r['accounts'] ?? [];
    }

    /** Virtual balance for a paper strategy (equity, realized/unrealized PnL, free/used). */
    public function balance(string $strategyId, ?string $asset = null): array
    {
        $q = array_filter(['strategy_id' => $strategyId, 'asset' => $asset], fn($v) => $v !== null);
        return $this->http->get('/api/v1/private/sim/balance', $q);
    }

    /** Open paper positions for a strategy. */
    public function positions(string $strategyId): array
    {
        $r = $this->http->get('/api/v1/private/sim/positions', ['strategy_id' => $strategyId]);
        if (is_array($r) && array_is_list($r)) {
            return $r;
        }
        return $r['positions'] ?? [];
    }

    /** Resting paper orders for a strategy. */
    public function openOrders(string $strategyId): array
    {
        $r = $this->http->get('/api/v1/private/sim/open-orders', ['strategy_id' => $strategyId]);
        if (is_array($r) && array_is_list($r)) {
            return $r;
        }
        return $r['orders'] ?? [];
    }

    /** Filled paper trades for a strategy. */
    public function myTrades(string $strategyId): array
    {
        $r = $this->http->get('/api/v1/private/sim/my-trades', ['strategy_id' => $strategyId]);
        if (is_array($r) && array_is_list($r)) {
            return $r;
        }
        return $r['trades'] ?? [];
    }

    /**
     * Place a paper order. Fills synthesise from the live ticker; nothing hits the venue.
     *
     * @param string $type         'market' or 'limit'
     * @param float|null $price    Required for limit orders
     */
    public function createOrder(
        string $strategyId,
        string $exchange,
        string $symbol,
        string $side,
        float $amount,
        string $type = 'market',
        ?float $price = null,
        ?string $market = null,
        ?float $leverage = null,
        ?bool $reduceOnly = null,
        ?float $slPrice = null,
        ?float $tpPrice = null,
        ?string $clientOrderId = null,
        ?array $params = null,
    ): array {
        $body = [
            'strategy_id'      => $strategyId,
            'exchange'         => $exchange,
            'symbol'           => $symbol,
            'side'             => $side,
            'amount'           => $amount,
            'order_type'       => $type,
            'orderType'        => $type,
            'price'            => $price,
            'market'           => $market,
            'market_type'      => $market,
            'leverage'         => $leverage,
            'reduceOnly'       => $reduceOnly,
            'slPrice'          => $slPrice,
            'tpPrice'          => $tpPrice,
            'client_order_id'  => $clientOrderId,
            'clientOrderId'    => $clientOrderId,
            'params'           => $params,
        ];
        return $this->http->post('/api/v1/private/sim/create-order',
            array_filter($body, fn($v) => $v !== null));
    }

    /** Cancel a resting paper order. */
    public function cancelOrder(
        string $strategyId,
        string $orderId,
        ?string $symbol = null,
        ?string $exchange = null,
    ): array {
        $body = array_filter([
            'strategy_id' => $strategyId,
            'order_id'    => $orderId,
            'orderId'     => $orderId,
            'symbol'      => $symbol,
            'exchange'    => $exchange,
        ], fn($v) => $v !== null);
        return $this->http->post('/api/v1/private/sim/cancel-order', $body);
    }
}
