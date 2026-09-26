# Speed tuning

Target: a research/sourcing pipeline finishes in under ~10 minutes with under
~40 tool calls on a cheap hosted model. Typical result: a "use every source"
sourcing prompt makes one agent do over a hundred sequential calls in nearly
half an hour; rewritten as bounded phases, the same job delivers its target
number of real records in well under ten minutes.

## Measure first

1. `melaya_run_diagnosis { "run_id": "<id>" }` - duration per phase, token and
   dollar cost, `stall` / `coverage` forensics.
2. `melaya_run_inspect { "run_id": "<id>", "include_tool_calls": true }` -
   count calls per agent and per tool; spot sequences of one call per turn.
3. Classify the time sink:

| Pattern in the trace | Cause | Fix |
|---|---|---|
| Long chain of single calls, one per turn | No parallel batching | "Call tools in parallel batches of up to N" |
| Many near-identical searches | No budget, no stop rule | Hard budget + "stop at TARGET" |
| One LLM judgement per candidate | Triage done by the agent model | ONE `decide_batch` / `decide_batch_file` call for all candidates |
| Repeated 404 path guesses | Guessing URLs | `scrape_links` on the home page |
| Huge replies re-read every turn | Big inline data | `save_to` files, pass paths |
| Long waits between steps | Pending approval | `melaya_approval_list` |
| Minutes per turn | Local/CPU model | Cheap hosted model for validation |

## The bounded-phase instruction pattern

Replace "use every source you can" with:

```
BUDGET: at most 40 tool calls in total. Call tools in parallel batches.
TARGET: 15 new records. Stop as soon as TARGET is reached.

PHASE 1 - Feeds (one parallel batch): call the registry/data tools and the
RSS feeds listed below together. Collect candidates. No confirmation yet.
PHASE 2 - Triage (one call): write all candidates to a file and call
decide_batch_file once (or decide_batch with the list) to score them. Keep the
top TARGET.
PHASE 3 - Confirm (one parallel batch): for each kept candidate, one
scrape_page on its known URL. Drop any you cannot confirm.
OUTPUT: RECORDS: ... SUMMARY: ... CITE: ...
```

Why it works:
- Phase 1 and 3 run as parallel batches: wall time is one slow call, not the sum.
- Phase 2 replaces N model turns with one call to a free Melaya scorer
  (`decide_batch` / `decide_batch_file`; `jev_*` is the paid hosted twin with
  the same schema).
- The budget and TARGET give the model a stop rule, which removes `stall`.

## Other levers

- Source order in every research prompt: registry/data tools, then a known URL
  with `scrape_page`, then RSS/GDELT, then `web_search` only to discover URLs.
  Blocked or empty sources cost full round trips.
- Use a `decide` step (System One gate) between steps to stop a run early when
  there is nothing worth processing, instead of letting later steps idle.
- Keep `include_context` off for agents that do not need static context; it is
  prepended to every turn.
- Use the validation `brief` to shrink the run ("at most 5 records") while
  iterating on logic, then do one full-size run.

## Verify the gain

Re-run with the same brief and compare in your ledger: calls, minutes, cost,
records produced, artifact check result. A faster run that produces fewer
verified records is not a fix.
