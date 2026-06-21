"""Static-context assembly shim.

Models building the per-turn *static* context block the model sees:
system prompt + granted knowledge docs + tool schemas. Static because it
is fixed for a run, distinct from the rolling message history (model
wrapper) and from RAG retrieval (rag). Pure string/dict assembly.
"""
from __future__ import annotations
from typing import Any


class ContextAssembler:
    def __init__(self, system_prompt: str, knowledge_docs: list[str],
                 tool_specs: list[dict[str, Any]]) -> None:
        self.system_prompt = system_prompt
        self.knowledge_docs = knowledge_docs
        self.tool_specs = tool_specs

    def assemble(self) -> dict[str, Any]:
        """Pack the system prompt + knowledge docs into one context string
        and serialize the granted tools to their schema view."""
        parts = [self.system_prompt]
        parts.extend(self.knowledge_docs)
        ctx = "\n\n".join(parts)
        tools = [{"name": t["name"], "schema": t.get("parameters", {})}
                 for t in self.tool_specs]
        return {"system": ctx, "tools": tools, "chars": len(ctx)}
