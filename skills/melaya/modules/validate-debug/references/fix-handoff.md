# Fix handoff: config re-save vs platform report

## Decide the class with evidence

| Question | Yes means |
|---|---|
| Does the same tool call, with correct arguments, fail or return wrong data when you replay it read-only with `melaya_connector_call`? | Platform defect |
| Does `melaya_pipeline_preview` drop or mangle a documented field? | Platform defect (or the field is undocumented: config) |
| Does a documented setting have no effect at run time (for example a per-agent approval gate ignored in a mode that should honour it)? | Platform defect, or the documented meaning is wrong: report it either way |
| Did the agent pick the wrong tool, wrong args, wrong order, skip a phase, loop, retype data, invent values? | Config/prompt |
| Is a connector, key or scope missing or expired? | Setup (user) |
| Is an external site blocking, rate-limiting or empty? | Source order (config) |
| Did the run stop early after a platform restart or runner disconnect? | Transient: re-run first; report only if it repeats |

When unsure, fix the prompt first. If the defect survives a clear, explicit
instruction on two different models, treat it as a platform defect.

## Path A: config / prompt fix (most fixes)

1. Change the generator script constants or the pipeline's instruction text
   (thesis, provenance rule, tool-reliability order, design rule, writing
   rules, mailer pattern). Never hand-edit the saved JSON as the fix.
2. Regenerate every affected config (a shared constant touches every pipeline
   that uses it).
3. `melaya_pipeline_preview` each changed config; confirm the new text/tools
   appear in the generated code.
4. `melaya_pipeline_save { "mode": "update", "pipeline": "<canonical name>", "project": "<project>", "config": <full document> }`.
5. `melaya_pipeline_get` read-back; diff against what you sent.
6. If `persistent_memory` is on and the old failure is remembered, clear it
   in the app or turn memory off before re-running.
7. Re-run with the same brief; compare with the ledger.

## Path B: platform defect (report it, do not work around it)

You cannot fix the platform from the MCP side, and you must not build a
workaround that hides the defect (for example writing data by hand, or
switching the pipeline to an unsafe approval mode).

1. Collect the evidence:
   - the run id (from `melaya_pipeline_run` or `melaya_run_status`);
   - the exact failing tool call, its arguments and its reply, copied from
     `melaya_run_inspect` with `include_tool_calls`;
   - your read-only replay with `melaya_connector_call` and its result;
   - what you expected, with the tool description that promises it.
2. Remove anything private before sharing: no credentials, no personal data
   of third parties, no client file contents beyond what the defect needs.
3. Ask the user to file it in the Melaya app: the **Report a bug** button (a
   floating bubble on every page once signed in). They pick a severity and an
   area, paste the title and description below, and can attach screenshots.
   The **My bugs** tab shows status and replies from the Melaya team.
4. Hold every config that depends on the fix. Mark the pipeline "blocked on
   platform" in the validation ledger and continue with other pipelines.
5. When Melaya confirms the fix is live, and the pipeline runs on the user's
   own runner, have the user restart the runner with the latest version (the
   start command comes from `melaya_setup_status`; confirm it is back with
   `melaya_runner_status`). Then save the dependent configs, then re-run.

### Bug report template (paste into Report a bug)

```
Title: <tool or feature>: <one-line symptom>

What happened: <what the run showed, in one or two sentences>
Expected: <what the tool description or docs say should happen>
Run id: <id>   Pipeline: <canonical name>   Project: <project>
Failing call: <tool name> with <arguments, secrets removed>
Reply: <the reply, trimmed>
Replay: <same call via melaya_connector_call and its result, or "write tool, not replayable">
Reproduces: <every time / sometimes>; tried on models: <model ids>
Impact: <which pipelines are blocked>
```

### Message to the user

```
I found a problem on the Melaya side, not in your pipeline: <one line>.
Please open Report a bug in the Melaya app and paste the text below.
Until Melaya fixes it, <pipeline> stays paused; the other pipelines are not
affected. Nothing has been changed on your data.
```

## After either path

- Update the validation ledger: defect, class, fix or report, run id that
  proved it.
- Re-run downstream pipelines that read what the fixed pipeline writes.
- If a lesson is general, add it to the generator's shared constants so every
  pipeline inherits it.
