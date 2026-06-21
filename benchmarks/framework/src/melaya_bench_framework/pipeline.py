"""Pipeline orchestration shim — reproduces the pipeline runner's
step-to-step transition cost.

What the production runner does between steps (see
the production multi-agent pipelines for variants):

    1. Step N's tool/model call completes → ToolResponse in hand.
    2. Variable binding: pull fields from ToolResponse into a context
       dict (`ctx["step_<n>_result"] = response`).
    3. Pick the next step from the pipeline graph (linear = step N+1,
       parallel = the unblocked frontier).
    4. Re-pack args for step N+1 from the context dict.
    5. Await step N+1.

The 4 lines between "step N done" and "step N+1 await" are pure
runner overhead. This shim's `LinearPipeline.run` and
`ParallelPipeline.run` reproduce that exact pattern. Each step's
tool callable returns in ~10 ns (zero-work mock) so any measured
latency is the runner's coordination cost.
"""

from __future__ import annotations

import asyncio
from dataclasses import dataclass
from typing import Any, Awaitable, Callable


# A pipeline step is a callable taking the previous context dict and
# returning a result that gets bound into the next context. Mirrors the
# `(ctx) -> ToolResponse | dict` shape that every production pipeline
# step uses, regardless of whether it's wrapping a tool call, a model
# call, or a sub-pipeline.
PipelineStep = Callable[[dict[str, Any]], Awaitable[Any]]


def _mock_step_factory(index: int) -> PipelineStep:
    """Build a zero-work step that records its index into the context.

    The body is exactly two operations:
        1. Read `ctx["last"]` (mirrors variable binding from prior step)
        2. Write `ctx["last"] = index` (mirrors result binding for next step)

    Total per-step work is ~50 ns of dict ops + the orchestrator's
    `await` cost. Anything else measured is the runner's overhead.
    """
    async def _step(ctx: dict[str, Any]) -> int:
        _ = ctx.get("last")
        ctx[f"step_{index}_result"] = index
        ctx["last"] = index
        return index
    return _step


@dataclass
class LinearPipeline:
    """A pipeline of N sequential steps. Each step awaits the previous
    one's completion before running. This is the most common shape in
    production — most runner workflows are "tool A → tool B → model →
    tool C" linear chains.

    The benched cost per step is `(await + dict-bind + invoke)`. With
    zero-work steps that's ~1-3 µs on modern hardware, which gives a
    floor for what step-to-step transitions can cost.
    """

    steps: list[PipelineStep]

    @classmethod
    def of_size(cls, n: int) -> "LinearPipeline":
        """Build a linear pipeline of `n` zero-work mock steps."""
        return cls(steps=[_mock_step_factory(i) for i in range(n)])

    async def run(self) -> dict[str, Any]:
        """Run every step in order. Returns the final context dict."""
        ctx: dict[str, Any] = {"last": None}
        for step in self.steps:
            await step(ctx)
        return ctx


@dataclass
class ParallelPipeline:
    """A pipeline of N parallel steps, all fanning out at once and
    joining at the end via `asyncio.gather`. This is the shape of the
    "research crew" workflows where multiple read-only tools fire in
    parallel before the reducer joins their outputs.

    Critical perf insight: `asyncio.gather` of N zero-work coroutines
    is NOT just `N × single-coro cost`. The event loop's task
    scheduling cost is amortised across all gathered awaitables, so
    per-step transition cost should be LOWER here than linear at
    sufficient N. The bench reports both so users can see the curve.
    """

    steps: list[PipelineStep]

    @classmethod
    def of_size(cls, n: int) -> "ParallelPipeline":
        """Build a parallel pipeline of `n` zero-work mock steps."""
        return cls(steps=[_mock_step_factory(i) for i in range(n)])

    async def run(self) -> dict[str, Any]:
        """Fire all steps in parallel via `asyncio.gather`. Each step
        sees the same initial context dict.

        In production, parallel steps each have their own scoped
        context that gets merged into the parent on join. The merge
        cost is dominated by `dict.update` (~20 ns per key) and is
        modelled inline below — fast enough to be in the noise floor.
        """
        ctx: dict[str, Any] = {"last": None}
        # Each step gets its own scoped ctx so the gather doesn't
        # serialize on dict contention. Mirrors production's per-step
        # context isolation.
        per_step_ctxs = [dict(ctx) for _ in self.steps]
        await asyncio.gather(
            *(step(c) for step, c in zip(self.steps, per_step_ctxs))
        )
        # Reducer: merge per-step results into the parent ctx.
        for c in per_step_ctxs:
            ctx.update(c)
        return ctx
