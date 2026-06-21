"""Registry boot shim — reproduces `the production registry builder`.

What the production registry does at cold boot (the production registry):

    1. Import ~200 `the production tool modules` modules (each is ~3-30 tools).
    2. For each module, `inspect.iscoroutinefunction` every public
       `async def` and pull out its signature + docstring.
    3. For each tool, build a JSON schema (param type, required,
       default).
    4. Register on the Toolkit with a per-tool postprocess func +
       a per-tool group name.

Production reports this takes single-digit seconds (~3-7 s typical on
modern hardware). This shim lets you measure that cost on YOUR
hardware with a synthetic tool surface of equivalent size, so you can
compare your machine's tier to the production reference.

The shim doesn't import the real tool modules — they're not
in this OSS package and they'd add a 5-minute install. Instead, we
synthesize N tool modules in memory with the same shape (async def,
typed args, docstring) and feed them through the same
introspect+register pipeline. The per-tool cost should match the real
runtime to within 10-20 %; the absolute number scales linearly with N.
"""

from __future__ import annotations

import inspect
import textwrap
import types
from typing import Any, Callable

from .toolkit import Toolkit


# ── Synthetic tool factory ──────────────────────────────────────────────


_TOOL_TEMPLATE = '''
async def tool_{idx}(
    arg_a: str,
    arg_b: int = 0,
    arg_c: float = 0.0,
    arg_d: bool = False,
    arg_e: dict | None = None,
) -> dict:
    """Synthetic tool #{idx} for the registry-boot bench.

    Mirrors the docstring shape used by every `the production tool modules`
    tool: one-line summary, then Args / Returns sections. The
    registry's docstring parser walks until the first blank line, so
    keeping a realistic body length is what makes the bench's per-tool
    cost match the production cost.

    Args:
        arg_a: First positional, required.
        arg_b: Second positional, defaults to 0.
        arg_c: Third positional, defaults to 0.0.
        arg_d: Fourth positional, defaults to False.
        arg_e: Optional dict payload.

    Returns:
        A dict with the tool's index for round-trip verification.
    """
    return {{"tool_index": {idx}}}
'''


def synthesize_tool_modules(
    n_modules: int = 50,
    tools_per_module: int = 5,
) -> list[types.ModuleType]:
    """Build `n_modules` synthetic Python modules each containing
    `tools_per_module` async tool functions. Same shape as the real
    `the production tool modules` files: typed args, Google-style docstrings,
    one-line summaries.

    Default size (50 × 5 = 250 tools) is in the same ballpark as
    production (its full production tool catalog across many modules). Bump for stress
    testing.

    Returns the list of synthesized modules. Each module has the
    correct `__name__` so `registry.boot`'s `getattr(obj, "__module__")
    == module.__name__` predicate accepts the tools.
    """
    modules: list[types.ModuleType] = []
    for mod_idx in range(n_modules):
        mod_name = f"_synthetic_tools_mod_{mod_idx}"
        module = types.ModuleType(mod_name)
        source = "\n".join(
            _TOOL_TEMPLATE.format(idx=mod_idx * tools_per_module + i)
            for i in range(tools_per_module)
        )
        # `exec` in the module's namespace so each `async def` is
        # bound with __module__ == mod_name (required by `_is_tool`).
        exec(compile(source, f"<{mod_name}>", "exec"), module.__dict__)
        modules.append(module)
    return modules


# ── Registry boot ───────────────────────────────────────────────────────


class Registry:
    """Walks a list of tool modules, introspects every public async
    tool, and registers them on a fresh Toolkit. Mirrors the
    `the production registry builder` hot path.
    """

    @staticmethod
    def _is_tool(obj: Any, module: types.ModuleType) -> bool:
        """Predicate mirrors `the registry's tool predicate`."""
        return (
            inspect.iscoroutinefunction(obj)
            and getattr(obj, "__module__", "") == module.__name__
            and not obj.__name__.startswith("_")
        )

    @classmethod
    def boot(cls, modules: list[types.ModuleType]) -> Toolkit:
        """Build a fresh Toolkit with every tool from every module.

        Steady-state shape of the work:
            1. For each module, `dir(module)` + getattr each name.
            2. Predicate-check it's a public coroutine.
            3. Introspect signature + docstring.
            4. Build a params dict (type, required, default).
            5. Register on the Toolkit.

        This is the cold-start cost. The IMPORT cost of the modules
        themselves (`import a production tool module`) is measured separately
        in the bench — for the synthetic shim, modules are built
        in-memory so the import cost is negligible. The production
        registry's import cost dominates its boot time (most modules
        are pure-Python with a handful of stdlib imports each).
        """
        toolkit = Toolkit()
        for module in modules:
            for name in dir(module):
                obj = getattr(module, name)
                if not cls._is_tool(obj, module):
                    continue
                # Introspect: same calls the real registry makes
                sig = inspect.signature(obj)
                doc = inspect.getdoc(obj) or ""
                # Build the params dict identically to the registry builder
                params = {
                    pname: {
                        "type": (
                            str(pval.annotation.__name__)
                            if hasattr(pval.annotation, "__name__")
                            else str(pval.annotation)
                        ).replace("inspect._empty", "any"),
                        "required":
                            pval.default is inspect.Parameter.empty,
                        "default": (
                            None if pval.default is inspect.Parameter.empty
                            else pval.default
                        ),
                    }
                    for pname, pval in sig.parameters.items()
                }
                # First-line summary, same logic as the registry builder
                summary_lines: list[str] = []
                for ln in doc.splitlines():
                    s = ln.strip()
                    if not s:
                        if summary_lines:
                            break
                        continue
                    summary_lines.append(s)
                description = " ".join(summary_lines)
                _ = (params, description)  # noqa: F841 — modelled, not used
                toolkit.register_tool_function(obj, func_name=name)
        return toolkit
