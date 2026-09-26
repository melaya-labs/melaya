# Limits, costs and languages

Numbers an author needs before promising anything to a client. Plan-dependent values can change; when a run hits one, the error or `melaya_setup_status` names the limit.

## 1. Time

| Limit | Value | What to do |
|---|---|---|
| Run wall clock | About 30 minutes of the run's own work. Time spent waiting for a human approval does not count; after an approval the run gets about 10 more minutes to finish | Bounded phases, tool budgets, parallel batches (instruction-patterns.md). Split very long work into two pipelines |
| Per-tool timeout | `web_search` 15 s, `web_fetch` 20 s, `scrape_page` 25 s, most tools 30 s, `pdf_from_html` and shell tools 120 s | A timeout comes back to the agent as an error result, not a crash |
| Automatic retries | Network errors, timeouts and HTTP 429 are retried up to 3 times (0.5 s, 1.5 s, 3 s). 400, 401, 403, 404 are never retried | Do not tell agents to "retry until it works" |
| Circuit breaker | After 5 consecutive failures a tool is switched off for about 60 s within that run and returns a "temporarily disabled" error | Instruct a fallback source or "mark MISSING and continue" |
| Approval expiry (triggered runs) | Default 1 hour (60 s to 24 h, set on the trigger) | Keep the gated send as the LAST step |

## 2. Agent size

- The number of think-and-act turns per agent comes from its persona and the model profile, not from the config. You cannot raise it in a pipeline config.
- Small local models are clamped hard (a 4B-class model: about 5 turns and short outputs). Design at most about 5 tool calls per agent for those, and split fetch and write into two agents.
- Keep each agent's tool list short (2 to 6 tools). Models with small context windows ignore tools when given dozens.
- Static context: 50,000 characters per document, 60,000 in total.
- Persistent memory: at most 25 marker lines captured per run; the store keeps about 200 entries, evicting low-priority old ones first.
- Decide step: judges only the first 4,000 characters of the previous output; 1 to 8 questions.
- `decide_batch` / `decide_batch_file`: up to 5,000 items per call.

## 3. Run inputs

| Item | Limit |
|---|---|
| Brief | 8 KB of text |
| All text values together | 32 KB |
| Files per run | 10 |
| File size | 25 MB per file and 100 MB per run on paid plans; 10 MB and 25 MB on the entry plans. Over MCP: base64 up to 7 MB, a URL up to 25 MB |
| Declared inputs | 30 per pipeline |
| Files on a runner-hosted pipeline | Refused: files reach cloud runs only |

Details: run-inputs.md.

## 4. Schedules and triggers (summary; depth in `../../../modules/automation-governance/GUIDE.md`)

- A cron in the config is not active until armed with `melaya_pipeline_schedule` action `set`.
- The entry plan is manual only. Minimum gap between runs rises with the plan (roughly 12 h, then 1 h, then 5 min on the top plans).
- A schedule that needs the user's runner is skipped while the runner is offline; after 5 consecutive skips or failed dispatches it pauses itself.
- Scheduled runs cannot carry run inputs yet: a scheduled pipeline must work with no brief (write the "no brief" default path).
- Runs started by event triggers are always forced to safe approvals.

## 5. Costs

What costs money on a run:

| Item | Who pays | How to keep it low |
|---|---|---|
| Model tokens: every agent turn, every tool result read back into the conversation, every parallel agent, every enforce retry | The model provider account in use (the user's key, or the plan's hosted models) | Cheap fast model for most agents; stronger model only on the one synthesis agent; small tool results (`save_to` files) |
| Embeddings for retrieval | The embedder's provider (free for local and some hosted embedders) | Retrieval only for large corpora |
| `decide`, `decide_check`, `decide_batch`, `decide_batch_file`, the `decide` step | Free (run by Melaya), with a per-minute decision budget per plan; over it the call is rate-limited, retry after a minute | Use `cascade_by` for big batches; one call for all items, not one per item |
| `jev_*` tools and `engine: "jev"` | The user's own Jev key, billed per input token | Use only when hosted speed matters |
| Connector tools that call paid APIs (search, SEO, data vendors) | The user's own key for that service | Check the tool description before adding it |
| `web_search` | Tries the calling agent's own provider search first, so it may bill that provider | Prefer registry and data tools, then known URLs |

Where to read the real cost: `melaya_eval_report` (token and dollar cost, cost per accepted run, per project and time window) and the run record (`melaya_run_status`, `melaya_run_inspect`). CPU speed of the free decision engine is roughly 0.2 to 0.5 s per decision, so 500 rows x 3 questions takes minutes; the cascade option cuts that.

Run history is kept for a limited time that depends on the plan (from about a week on the entry plan to unlimited on the top plan). Export anything the client must keep (documents go to Drive, records to the data store).

## 6. Languages

- `user_lang` on the pipeline: `en` (default), `fr`, `zh`, `es` or `pt` adds an "always answer in <language>" line to every agent. Any other code adds nothing.
- For any other language (German, Arabic, Japanese ...), say it in the instruction of every agent that writes for people: "Write every output in German." Do the same for the documents and the email.
- Catalog personas (factory agents) are translated into the user's interface language. A `system_prompt_override` replaces the persona in every language, so the behaviour stays identical whatever the UI language. Use it when a persona fights your intent.
- Write the instructions themselves in English: cheap models follow English instructions most reliably, and the output language is set separately.
- Tool arguments that are search queries should be in the language of the sources (a French registry wants French terms).
- Writing rules for any language: plain punctuation, no em or en dashes, no AI tells, concrete numbers with their source.
