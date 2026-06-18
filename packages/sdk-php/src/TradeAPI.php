<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Live trading API — credentialed order placement, account state, and
 * position management on a CONNECTED exchange.
 *
 * Every method POSTs to https://api.melaya.org/api/v1/private/<op>; the server
 * resolves your connected exchange credential (referenced by $apiKeyId — see
 * AccountAPI::keys()) and forwards the call to the venue through Melaya's
 * in-house Rust engine. Responses share an envelope:
 * { ok, exchange, operation, orderId, clientOrderId, payload, data, ... }.
 *
 * WARNING: these hit the REAL venue with REAL funds. The write methods
 * (createOrder, cancelOrder, amendOrder, cancelAllOrders, cancelPlanOrders,
 * closePosition, setLeverage, setMarginMode, setPositionMode) move money or
 * change account state. For risk-free testing use SimAPI (paper) or a paper
 * strategy instead.
 */
class TradeAPI
{
    public function __construct(private readonly HttpClient $http) {}

    // ── Account state (reads) ─────────────────────────────────────────────────

    /** Live account balance on a connected venue. */
    public function balance(
        string  $exchange,
        ?string $apiKeyId    = null,
        ?string $keyId       = null,
        ?string $marketType  = null,
        ?array  $params      = null,
    ): array {
        return $this->op('balance', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'keyId'      => $keyId,
            'marketType' => $marketType,
            'params'     => $params,
        ]));
    }

    /** Live open positions. */
    public function positions(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
        ?array  $params     = null,
    ): array {
        return $this->op('positions', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
            'params'     => $params,
        ]));
    }

    /** Historical positions (venue-dependent). */
    public function positionsHistory(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('positions-history', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** Resting (open) orders. */
    public function openOrders(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('open-orders', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** All orders (open + recent). */
    public function orders(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('orders', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** Closed/filled orders. */
    public function closedOrders(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('closed-orders', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** Your trade (fill) history. */
    public function myTrades(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('my-trades', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** Extended trade history (venue-dependent). */
    public function myTradesHistory(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('my-trades-history', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** Resting conditional/plan (trigger) orders. */
    public function planOrders(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
        ?string $symbol     = null,
    ): array {
        return $this->op('plan-orders', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marketType' => $marketType,
            'symbol'     => $symbol,
        ]));
    }

    /** Current leverage for a symbol. */
    public function leverage(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $symbol     = null,
        ?string $marketType = null,
    ): array {
        return $this->op('leverage', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'symbol'     => $symbol,
            'marketType' => $marketType,
        ]));
    }

    /** Leverage tiers / brackets for a symbol. */
    public function leverageTiers(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $symbol     = null,
        ?string $marketType = null,
    ): array {
        return $this->op('leverage-tiers', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'symbol'     => $symbol,
            'marketType' => $marketType,
        ]));
    }

    // ── Order placement & management (LIVE writes — real funds) ──────────────

    /**
     * Place a live order on the venue. WARNING: real money.
     * $stopPrice, $takeProfitPrice, and $reduceOnly are folded into $params.
     */
    public function createOrder(
        string  $exchange,
        string  $symbol,
        string  $side,
        float   $amount,
        ?string $apiKeyId         = null,
        string  $type             = 'market',
        ?float  $price            = null,
        ?string $marketType       = null,
        ?float  $stopPrice        = null,
        ?float  $takeProfitPrice  = null,
        ?bool   $reduceOnly       = null,
        ?float  $leverage         = null,
        ?string $clientOrderId    = null,
        ?array  $params           = null,
    ): array {
        $p = $params ?? [];
        if ($stopPrice       !== null) $p['stopPrice']       = $stopPrice;
        if ($takeProfitPrice !== null) $p['takeProfitPrice'] = $takeProfitPrice;
        if ($reduceOnly      !== null) $p['reduceOnly']      = $reduceOnly;
        return $this->op('create-order', $this->clean([
            'exchange'      => $exchange,
            'apiKeyId'      => $apiKeyId,
            'symbol'        => $symbol,
            'side'          => $side,
            'amount'        => $amount,
            'type'          => $type,
            'price'         => $price,
            'marketType'    => $marketType,
            'leverage'      => $leverage,
            'clientOrderId' => $clientOrderId,
            'params'        => empty($p) ? null : $p,
        ]));
    }

    /** Cancel a live order by id. WARNING. */
    public function cancelOrder(
        string  $exchange,
        ?string $apiKeyId       = null,
        ?string $orderId        = null,
        ?string $clientOrderId  = null,
        ?string $symbol         = null,
        ?string $marketType     = null,
    ): array {
        return $this->op('cancel-order', $this->clean([
            'exchange'      => $exchange,
            'apiKeyId'      => $apiKeyId,
            'orderId'       => $orderId,
            'clientOrderId' => $clientOrderId,
            'symbol'        => $symbol,
            'marketType'    => $marketType,
        ]));
    }

    /** Amend (modify) a live order. WARNING. */
    public function amendOrder(
        string  $exchange,
        ?string $apiKeyId  = null,
        ?string $orderId   = null,
        ?string $symbol    = null,
        ?float  $amount    = null,
        ?float  $price     = null,
    ): array {
        return $this->op('amend-order', $this->clean([
            'exchange' => $exchange,
            'apiKeyId' => $apiKeyId,
            'orderId'  => $orderId,
            'symbol'   => $symbol,
            'amount'   => $amount,
            'price'    => $price,
        ]));
    }

    /** Cancel every open order (optionally scoped to a symbol). WARNING. */
    public function cancelAllOrders(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $symbol     = null,
        ?string $marketType = null,
    ): array {
        return $this->op('cancel-all-orders', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'symbol'     => $symbol,
            'marketType' => $marketType,
        ]));
    }

    /** Cancel resting plan/trigger orders. WARNING. */
    public function cancelPlanOrders(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?string $symbol     = null,
        ?string $marketType = null,
    ): array {
        return $this->op('cancel-plan-orders', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'symbol'     => $symbol,
            'marketType' => $marketType,
        ]));
    }

    /** Close an open position (market reduce-only). WARNING. */
    public function closePosition(
        string  $exchange,
        string  $symbol,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
    ): array {
        return $this->op('close-position', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'symbol'     => $symbol,
            'marketType' => $marketType,
        ]));
    }

    /** Set leverage for a symbol. WARNING. */
    public function setLeverage(
        string  $exchange,
        string  $symbol,
        float   $leverage,
        ?string $apiKeyId   = null,
        ?string $marketType = null,
    ): array {
        return $this->op('set-leverage', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'symbol'     => $symbol,
            'leverage'   => $leverage,
            'marketType' => $marketType,
        ]));
    }

    /** Set margin mode (cross/isolated). WARNING. */
    public function setMarginMode(
        string  $exchange,
        string  $marginMode,
        ?string $apiKeyId   = null,
        ?string $symbol     = null,
        ?string $marketType = null,
    ): array {
        return $this->op('set-margin-mode', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'marginMode' => $marginMode,
            'symbol'     => $symbol,
            'marketType' => $marketType,
        ]));
    }

    /** Set position mode (one-way / hedge). WARNING. */
    public function setPositionMode(
        string  $exchange,
        ?string $apiKeyId   = null,
        ?bool   $hedged     = null,
        ?string $mode       = null,
        ?string $marketType = null,
    ): array {
        return $this->op('set-position-mode', $this->clean([
            'exchange'   => $exchange,
            'apiKeyId'   => $apiKeyId,
            'hedged'     => $hedged,
            'mode'       => $mode,
            'marketType' => $marketType,
        ]));
    }

    // ── Internal ─────────────────────────────────────────────────────────────

    private function op(string $pathOp, array $body): array
    {
        return $this->http->post("/api/v1/private/{$pathOp}", $body);
    }

    /** Strip null values from the body before sending (omit nulls per contract). */
    private function clean(array $body): array
    {
        return array_filter($body, fn($v) => $v !== null);
    }
}
