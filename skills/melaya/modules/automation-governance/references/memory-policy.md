# Crew memory policy reference

## 1. What `persistent_memory: true` does

Pipeline-level switch. When on, every run of that pipeline:

1. LOADS prior memory into EVERY agent's system prompt at start (ranked by relevance and recency, capped at about 8000 characters per agent, low-priority entries older than about 120 days dropped).
2. CAPTURES during the run, automatically, real tool failures (permission, rate limit, timeout, bad arguments, server errors) as memory entries. No narration needed; soft results such as "no matches" are filtered out.
3. SAVES at the end: lines any agent wrote on their own line starting with a marker (`MEMORY:`, `AVOID:`, `LEARNED:`, `USED:`, `FAILED TOOL:`, `REMEMBER:`, `DO NOT REPEAT:`, `TODO NEXT:`) become entries. With no markers in the run, the tail of the run transcript is saved instead. Markers per run are capped.

Storage: one memory file per (user, pipeline). Cloud runs keep it in the tenant's persistent area; local-runner runs keep it on the user's machine (then `melaya_agent_memory` reports that it cannot list it from here).

Read it with `melaya_agent_memory` (`pipeline`, optional `project`). Entries show topic, content, tags and the run that wrote them. Deleting individual entries is done by the user in the pipeline's Memory panel in the Agent Builder (owner or editor).

## 2. Why it is off by default in a data system

| Failure seen | Cause | Rule |
|---|---|---|
| Agents skip a tool that now works | an auto-captured "tool X failed" note from before the fix is replayed into every prompt | after any tool fix, inspect memory and have the user delete stale failure entries before re-running |
| A run re-reports last week's items | the transcript tail (a list of records) was saved and replayed as if current | keep records in the data store, not in memory |
| Prompts grow and slow down | memory injected into every agent, including workers that do not need it | enable memory only on pipelines that benefit |
| Poisoned instructions | text from a scraped page ended up in a marker line | never ask agents to write markers that quote external content |

## 3. Policy

1. The data store is the memory. Dedupe keys, status, `last_seen`, scores and provenance live in the Sheet (see `../../../modules/data-spine/GUIDE.md`). Each run reads it at the start. This is inspectable, editable by the client, and never stale in a hidden way.
2. Default `persistent_memory: false` for sourcing, screening, due diligence, memo, forms and portfolio pipelines.
3. Turn it on only when what the run learns is prose worth carrying over, for example a copilot that should remember the user's stated preferences or recurring corrections. Then:
   - instruct the last agent to end with 1-5 explicit `MEMORY:` lines (facts about preferences or process, never records, never quotes from external pages);
   - accept that tool-failure notes will also be captured; review them after each fix.
4. Never use memory to carry secrets, personal data about third parties, or client documents.
5. At handover, list per pipeline whether memory is on and why, and how the client clears an entry.

## 4. Checks

- [ ] `melaya_pipeline_get`: `persistent_memory` value is intentional for each pipeline.
- [ ] Where on: `melaya_agent_memory` shows only useful entries; stale failure notes removed after fixes.
- [ ] Where off: the pipeline reads its state from the data store at the start of each run.
