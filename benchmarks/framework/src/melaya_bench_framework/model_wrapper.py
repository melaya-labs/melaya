"""Model wrapper shim — reproduces `the production model wrapper`.

The production wrapper does this on every LLM turn:

    1. Pack the conversation history into the provider's expected
       message format (Anthropic vs OpenAI vs Gemini all want
       slightly different shapes).
    2. Add the system prompt + tools spec.
    3. Forward to the provider's `__call__` over HTTP (Anthropic SDK,
       OpenAI SDK, Ollama HTTP, etc.).
    4. Unpack the response into a `ChatResponse` with normalised
       `content_blocks` + `usage` (token counts).
    5. Record token usage in the shared `CostTracker` singleton.

What this bench measures: steps 1, 2, 4, 5. Step 3 — the provider
HTTP boundary — is replaced with a `MockProvider` that returns a
canned response in ~10 ns. That isolates RUNNER overhead from
network, which is what we want: network latency is provider-dependent
and lives on /benchmarks/agent-providers, not here.
"""

from __future__ import annotations

import time
from dataclasses import dataclass, field
from typing import Any


# ── Provider-shape stand-ins ────────────────────────────────────────────


@dataclass
class _TokenUsage:
    """Mirrors `the provider usage object`."""
    input_tokens: int = 0
    output_tokens: int = 0
    cached_input_tokens: int = 0


@dataclass
class _ChatResponse:
    """Mirrors `the provider response object`."""
    content_blocks: list[dict[str, Any]] = field(default_factory=list)
    usage: _TokenUsage = field(default_factory=_TokenUsage)


# ── Mock provider — the 0-ms HTTP boundary stand-in ─────────────────────


class MockProvider:
    """Drop-in for any runtime model — returns a canned
    `_ChatResponse` immediately, modelling a provider HTTP boundary
    with zero network latency.

    This is the lever that isolates RUNNER overhead from PROVIDER
    network. Real providers see 200 ms - 5 s of HTTP RTT to the LLM
    inference cluster; this returns in <1 µs so any latency the bench
    reports is wrapper-side.
    """

    def __init__(self, canned_text: str = "ok") -> None:
        self._canned = _ChatResponse(
            content_blocks=[{"type": "text", "text": canned_text}],
            usage=_TokenUsage(input_tokens=10, output_tokens=2),
        )

    async def __call__(self, messages: list[dict[str, Any]], **kwargs: Any) -> _ChatResponse:
        # Tiny copy so the wrapper's downstream mutations don't
        # cross-contaminate iterations (the production provider always
        # builds a fresh response object per call).
        return _ChatResponse(
            content_blocks=list(self._canned.content_blocks),
            usage=_TokenUsage(
                input_tokens=self._canned.usage.input_tokens,
                output_tokens=self._canned.usage.output_tokens,
                cached_input_tokens=self._canned.usage.cached_input_tokens,
            ),
        )


# ── Message history packing — the prompt assembly cost ──────────────────


def build_message_history(
    n_turns: int = 6,
    avg_msg_chars: int = 400,
) -> list[dict[str, Any]]:
    """Synthesize a realistic conversation history.

    n_turns=6 mirrors a typical agentic turn after a few tool calls.
    avg_msg_chars=400 is the median observed message length in
    production traces (system prompt is longer, tool results are
    shorter, model responses sit in this range).

    Returned shape matches what the production model wrapper
    receives from the agent: alternating user/assistant messages
    with the system prompt as message [0].
    """
    out: list[dict[str, Any]] = [
        {"role": "system", "content": "You are a helpful assistant." * 20},
    ]
    for i in range(n_turns):
        out.append({
            "role": "user" if i % 2 == 0 else "assistant",
            "content": (f"msg{i}-" * (avg_msg_chars // 6))[:avg_msg_chars],
        })
    return out


# ── The wrapper itself — the thing being measured ──────────────────────


class ModelWrapper:
    """Mirrors the production model wrapper for the steady-state
    case. The four steps are:

        1. Pack history into provider format    (`_pack`)
        2. Add system prompt + tools            (in `_pack`)
        3. Await provider                       (the mock)
        4. Unpack response + record cost        (`_unpack`)

    The hot path is ~10 ns of dict ops + the mock provider's ~10 ns
    response + ~50 ns of cost accounting. On modern HW this lands
    around 300-800 ns p50.
    """

    __slots__ = ("provider", "model_name", "_total_input", "_total_output")

    def __init__(
        self,
        provider: MockProvider,
        model_name: str = "mock-model-v1",
    ) -> None:
        self.provider = provider
        self.model_name = model_name
        self._total_input = 0
        self._total_output = 0

    def _pack(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None,
    ) -> list[dict[str, Any]]:
        """Provider-shape transformation. For OpenAI/Anthropic the
        production code remaps a few keys + injects the tools spec
        as a top-level field; that's a dict-copy with one or two
        renames. Modelled inline to capture the per-call alloc cost.
        """
        # In reality each provider has its own formatter (see
        # the provider formatters); they all do approximately this
        # shape transformation, so the bench measures the average
        # rather than tying itself to one provider's quirks.
        packed = [{"role": m["role"], "content": m["content"]} for m in messages]
        if tools:
            # Tools spec lives as a top-level field in the wire
            # payload — modelled as a no-op append since we're not
            # actually serialising.
            packed.append({"role": "_tools", "content": tools})
        return packed

    def _unpack(self, response: _ChatResponse) -> dict[str, Any]:
        """Normalise the provider response + record cost. The
        production version routes the response through a per-provider
        adapter; the steady-state cost is dominated by the cost
        accounting (two adds + a dict put)."""
        self._total_input += response.usage.input_tokens
        self._total_output += response.usage.output_tokens
        return {
            "content": response.content_blocks,
            "usage": {
                "input_tokens": response.usage.input_tokens,
                "output_tokens": response.usage.output_tokens,
            },
        }

    async def call(
        self,
        messages: list[dict[str, Any]],
        tools: list[dict[str, Any]] | None = None,
    ) -> dict[str, Any]:
        """One LLM turn through the wrapper. The hot path the bench
        measures."""
        packed = self._pack(messages, tools)
        raw = await self.provider(packed)
        return self._unpack(raw)
