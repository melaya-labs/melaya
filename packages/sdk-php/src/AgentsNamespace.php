<?php

declare(strict_types=1);

namespace Melaya;

/**
 * Grouped accessor for all agent-plane modules.
 *
 * Access via `$melaya->agents->*`.
 *
 * @example
 * ```php
 * $m = new Melaya\Melaya(apiKey: getenv('MK'));
 *
 * // Pipeline runs
 * $runs = $m->agents->pipelines->list(['project' => 'my-project']);
 *
 * // HITL approvals
 * $pending = $m->agents->hitl->pending();
 * $m->agents->hitl->approve($pending[0]['requestId']);
 *
 * // Assistant profile
 * $m->agents->assistant->setProfile(['name' => 'Antoine']);
 *
 * // Phone control
 * $tree = $m->agents->phone->screenTree();
 *
 * // Evals
 * $summary = $m->agents->evals->summary();
 * ```
 */
final class AgentsNamespace
{
    /** Pipeline run overview, traces, and cron schedules. */
    public readonly PipelinesAPI $pipelines;

    /** Human-in-the-loop approval queue: list pending, approve, reject. */
    public readonly HitlAPI $hitl;

    /** Assistant onboarding profile (get + set). */
    public readonly AssistantAPI $assistant;

    /** Phone device control: pair, list, screen-tree, apps. */
    public readonly PhoneAPI $phone;

    /** Eval run results, summaries, memory graphs, and benchmarks. */
    public readonly EvalsAPI $evals;

    /** @internal Constructed by {@see Melaya}. */
    public function __construct(
        PipelinesAPI $pipelines,
        HitlAPI $hitl,
        AssistantAPI $assistant,
        PhoneAPI $phone,
        EvalsAPI $evals,
    ) {
        $this->pipelines = $pipelines;
        $this->hitl      = $hitl;
        $this->assistant = $assistant;
        $this->phone     = $phone;
        $this->evals     = $evals;
    }
}
