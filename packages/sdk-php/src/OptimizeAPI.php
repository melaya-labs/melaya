<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Optimize API — parameter sweep optimizations (genetic/grid) over strategy configs.
 *
 * Maps to /api/v1/private/backtest/optimize/*.
 *
 * @example
 * ```php
 * $sdk = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * $run = $sdk->optimize->start([
 *     'strategyId' => 'strat_abc',
 *     'method'     => 'genetic',
 *     'paramBounds' => ['qty' => [0.001, 0.1]],
 * ]);
 * $optRunId = $run['optRunId'];
 *
 * // Poll until done
 * do {
 *     sleep(5);
 *     $runs = $sdk->optimize->list();
 * } while (true);
 *
 * $sdk->optimize->apply($optRunId, ['strategyId' => 'strat_abc']);
 * ```
 */
class OptimizeAPI
{
    public function __construct(private readonly HttpClient $http) {}

    /**
     * Start a parameter sweep optimization (genetic/grid) over a strategy config.
     *
     * @param array $body Strategy config + param bounds + sweep method
     */
    public function start(array $body): array
    {
        return $this->http->post('/api/v1/private/backtest/optimize', $body);
    }

    /** List optimization sweep runs. */
    public function list(): array
    {
        return $this->http->get('/api/v1/private/backtest/optimize');
    }

    /** Get the status/progress of an optimization sweep run. */
    public function status(string $optRunId): array
    {
        return $this->http->get(
            '/api/v1/private/backtest/optimize/' . rawurlencode($optRunId) . '/status'
        );
    }

    /** Cancel an in-progress optimization sweep. */
    public function cancel(string $optRunId): array
    {
        return $this->http->post(
            '/api/v1/private/backtest/optimize/' . rawurlencode($optRunId) . '/cancel'
        );
    }

    /**
     * Apply the best parameters from a completed optimization sweep to a strategy.
     *
     * @param string $optRunId The optimization run ID
     * @param array  $body     e.g. ['strategyId' => 'strat_abc']
     */
    public function apply(string $optRunId, array $body = []): array
    {
        return $this->http->post(
            '/api/v1/private/backtest/optimize/' . rawurlencode($optRunId) . '/apply',
            $body
        );
    }
}
