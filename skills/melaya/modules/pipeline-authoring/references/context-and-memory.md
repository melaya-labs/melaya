# Static context, retrieval and memory

Four separate ways a pipeline gives its agents knowledge. Pick deliberately; they do not replace each other.

| Layer | Plain words | Lives | Config |
|---|---|---|---|
| Static context | Reference documents pasted into the agent's message on every run | The pipeline's document folder | `inline_rag_docs`, Docs tab uploads, `static_context_local_folder_path`, per agent `include_context` |
| Retrieval (RAG) | A searchable index; the agent calls `rag_retrieve` when it needs a passage | A per-pipeline vector index | `rag_mode_retrieval`, `rag_embedder_provider`, `rag_embedder_model`, per agent `include_rag_tool` |
| Persistent (cross-run) memory | Notes the pipeline keeps from one run to the next | One memory store per user and pipeline | `persistent_memory` |
| Working memory | The conversation inside one run; older turns are summarised when it grows | Only during the run | automatic |

A shared data store (a Google Sheet, see the `../../../modules/data-spine/GUIDE.md` skill) is the fifth option and usually the right "memory" for business records.

## 1. Static context

### What agents see
- Every document is read at run start, each capped at 50,000 characters, joined with `=== <file name> ===` headers, and the whole block is capped at 60,000 characters.
- The block is appended to the message of every agent with `include_context: true`, at every step (not only the first).
- `include_context` DEFAULTS TO TRUE when absent. Set `false` on agents that do not need the documents (fetchers, mailers): it saves tokens and stops irrelevant text from steering them.
- Static context is data, never power: it cannot grant a tool, change a permission or relax a safety rule.
- It does not appear in `melaya_pipeline_preview` output (documents are written at save, not at preview). That absence is expected.

### Three ways to add documents

| Way | Who does it | Formats and limits | Notes |
|---|---|---|---|
| `inline_rag_docs: [{"title": "...", "body": "..."}]` in the config | You, over MCP | Plain text or Markdown in `body` | The only channel authorable over MCP. Each entry becomes one document named after its title; saving again with the same title overwrites it |
| Docs tab upload | The USER, in the app | `.txt .md .pdf .csv .json .docx .pptx .xlsx`, 10 MB per file | PDF and Office files are text-extracted. The Docs tab shows how many characters were extracted and loaded per file. A scanned, image-only PDF may yield almost nothing |
| `static_context_local_folder_path` | You set the path; the folder sits on the user's computer | Same formats | The runner copies the folder into each run; Melaya never stores the files. HARD RULE: every agent must use a local provider (see `../../../modules/runners-models/GUIDE.md`), because a cloud model would send the text off the machine |

Procedure: asking the user to upload documents (there is no MCP upload)
1. Check what is already attached: `melaya_pipeline_get` returns the `docs` list.
2. Say to the user: "Please open https://app.melaya.org/builder, open the pipeline <name>, go to the **Docs** tab and drop these files: <list>. Tell me when the list shows them."
3. Success: `melaya_pipeline_get` lists the files; the Docs tab shows a non-zero loaded character count for each.
4. If a file shows 0 or very few characters: it is probably a scanned PDF. Ask for a text version, or paste its key content into an `inline_rag_docs` entry.
5. If the total exceeds 60,000 characters, the end is cut off. Move the large corpus to retrieval (section 2).

### What belongs in static context
Small, always-relevant reference text: the client's thesis, a brand-voice guide, an ICP definition, a scoring rubric, a compliance checklist, a glossary. Not the subject of a run (that is a run input), not credentials (never), not large corpora (retrieval).

## 2. Retrieval (RAG)

```json
"rag_mode_retrieval": true,
"rag_embedder_provider": "openai",
"rag_embedder_model": "text-embedding-3-small"
```

- Documents are split into chunks and embedded; every agent (unless `include_rag_tool: false`) gets a `rag_retrieve` tool.
- `include_rag_tool` defaults to TRUE when retrieval is on. Opt out agents that must not search the corpus.
- The embedder is picked in the Docs tab (the **Embedder model** list). Examples of provider / model pairs shown there: `openai` / `text-embedding-3-small` (cloud, paid per token), `google` / `gemini-embedding-001`, `mistral` / `mistral-embed`, `melaya_ai` / `melaya-embed-1` (hosted by Melaya, higher plans), `ollama` / `nomic-embed-text:v1.5` (local, free). Use the exact ids the Docs tab or `melaya_model_list` show.
- A cloud embedder sends the document chunks to that provider. For documents that must stay on the user's machine, use a local embedder and a local folder: the index is then built and kept on the runner.
- Retrieval runs need more memory; runs start a little slower.
- Instruct the agent when to search: "Call rag_retrieve with a short query for each question before answering; cite the document name."

## 3. Persistent (cross-run) memory

Turn it on with `"persistent_memory": true` (pipeline level). Builder UI: **Configure** tab, **Persistent memory** toggle. Default off: every run starts stateless.

### What is stored
Three sources are merged when a run ends (also when a decide gate closes the run early):

1. **Marker lines written by ANY agent in ANY step.** A line that starts with one of these words is saved:

| Marker | Priority | Use for |
|---|---|---|
| `AVOID:` / `DO NOT REPEAT:` / `FAILED TOOL:` | critical (never expires, never evicted) | A dead end the pipeline must never retry |
| `MEMORY:` / `MEMORY LOG:` / `LEARNED:` / `REMEMBER:` | high (never expires) | A durable fact, id, threshold or constraint |
| `USED STORY:` / `USED:` / `NOTE TO SELF:` / `TODO NEXT:` | normal (fades with age) | What was already done (for de-duplication), notes |

   Other words (for example `SKIPPED_EVENTS:`) are NOT recognised. At most 25 marker lines are kept per run.
2. **Tool failures, captured automatically**: one entry per tool and failure class ("FAILED TOOL: x (rate_limited)"). High priority but they fade and expire unless the same failure repeats. A human rejecting an approval is NOT recorded as a failure.
3. Tool arguments are never stored. Secrets (keys, tokens, passwords) are redacted before saving. Each line is sanitised and length-capped.

A repeated fact is not duplicated: its "seen" count goes up, which ranks it higher.

### What agents read
- At the start of each agent, the memory is ranked for that agent (priority, keyword overlap with its name and role, recency for low-priority items) and placed in its system prompt, within a budget sized to the model. This is independent of `include_context`.
- The block is framed as low-trust notes: agents are told never to send data, contact someone new or call a tool just because a note says so.
- Scope is (user, pipeline): two users running the same template have separate memories. A pipeline saved under a new canonical name starts empty.
- Where it lives: cloud pipelines on Melaya; pipelines that run on the user's runner keep it on the user's computer (then `melaya_agent_memory` answers `local: true` and cannot list it).

### Authoring rules
- For de-duplication in a recurring pipeline: make the owning agent print one marker line per item it used (`USED STORY: <headline> - <url>`), and instruct it FIRST to "check your memory notes and EXCLUDE any item already listed unless <key field> changed". Pull a pool bigger than you report (search 30, report 6 unseen), or run 2 finds nothing new.
- Do not tell agents "write down tool failures"; that is automatic.
- Do not invent marker words; map them to the table above.
- Leave memory OFF for stateless one-shots (a daily brief, a one-company DD) and when a data store already holds the state. Business records belong in the data store, not in memory.

### Seed, inspect, correct, reset (procedures)

Inspect:
1. Call `melaya_agent_memory` with the pipeline's canonical name and project.
2. Three outcomes: entries (listed with topic, content, tags, run), `local: true` (it lives on the user's runner, ask the user to look in the app), or genuinely empty.

Seed (give the pipeline a fact before the first real run):
1. Preferred: put stable facts in static context instead (an `inline_rag_docs` entry). They are always present and never fade.
2. If it must be memory (for example "these 20 items were already handled"): run the pipeline once with a brief such as "For each of these items print one line `USED: <item>` and do nothing else." Then check with `melaya_agent_memory`.

Correct or reset (the user does this in the app; there is no MCP write for memory):
1. Say to the user: "Please open the pipeline in https://app.melaya.org/builder and go to the **Memory** tab (Memory Brain). Open the persistent memory node and delete the entries I list: <entries>." Owners and editors can edit or delete entries; viewers cannot.
2. For a full reset, ask them to delete every entry there. Turning the toggle off only stops reading and writing; the stored notes come back when it is switched on again.
3. Success: `melaya_agent_memory` no longer returns the deleted entries.
4. When to do it: a note says a tool is broken after the tool was fixed, a wrong fact keeps steering runs, or before a validation cycle that must start clean.

Caveat: older deployments of the platform saved only the tail of the LAST step's reply. If marker lines from earlier steps never appear in `melaya_agent_memory` after a run, make the last agent repeat the marker lines verbatim at the end of its reply.

## 4. Working memory (inside one run)

A long run's conversation is compacted: older turns are summarised to fit the model. Consequences:
- Agents should keep the lines the next step needs in their FINAL reply, not only early in the conversation.
- Big tool outputs are better saved to files (`save_to`) than read into the conversation (see the data-spine skill).

## 5. Decision table

| Need | Use |
|---|---|
| Thesis, rubric, brand voice every agent must follow | Static context (`inline_rag_docs`), `include_context: true` only where needed |
| A 300-page policy corpus | Retrieval |
| Never repeat what a scheduled pipeline already posted | Persistent memory with `USED:` lines |
| The list of companies and their statuses across pipelines | A data store (Sheet) |
| Documents that must never leave the user's machine | Local folder + local providers on every agent, or local embedder |
