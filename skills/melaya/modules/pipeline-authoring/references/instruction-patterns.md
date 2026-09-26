# Instruction-writing patterns

Written for cheap, fast cloud models (qwen-plus class). If a pattern works there, it works on stronger models.
Every agent runs headless: nobody answers its questions.

## 1. Skeleton of a good instruction

```
<CONTEXT: thesis / target / what the previous message holds>

Goal: <one sentence>. If this run has a brief, it overrides <theme, region, count>. Default TARGET: <n>.

HARD BUDGET: at most <N> tool calls in total. Always call tools in parallel batches (all calls of a
phase in ONE turn). Stop the moment you have TARGET items.

Phase 1, <name> (ONE parallel batch): invoke <tool> with <arg> = <value> on each of: <list>.
Phase 2, triage (ONE call, free): invoke decide_batch with items = <shape>, questions = <json>,
  top_by = "<question>" and top_k = <n>.
Phase 3, confirm (ONE parallel batch): for the best TARGET candidates, invoke scrape_page on <url>.
Only if Phase 1 gave fewer than TARGET: <one fallback batch>.
Do not use any other source in this run.

<RELIABLE tool order>
<PROVENANCE rule>

Final reply, ALWAYS in exactly this format, even when you found nothing (then RECORDS: [] and say why):
RECORDS: <JSON array of objects on ONE line, keys = ...>
SUMMARY: <n> items from <sources>; <which sources failed>
CITE: <item> - <source URL>   (one per item)
Never answer with a narrative report instead of RECORDS.
```

## 2. Patterns and the failure each one prevents

| Pattern | Wording | Prevents |
|---|---|---|
| Bounded phases | "Phase 1 ... Phase 2 ... Phase 3" | Wandering, 100+ sequential calls |
| Hard budget | "HARD BUDGET: at most 40 tool calls in total." | Runaway runs (28 minutes observed) |
| Parallel batches | "all calls of a phase in ONE turn" | One call per model turn, slow runs |
| Stop at target | "Stop searching the moment you have TARGET companies." | Over-collection |
| Free triage | "ONE decide_batch call over every candidate" | An LLM reading 200 headlines one by one |
| Closed source list | "Do not use any other source in this run." | Drifting into blocked search engines |
| Fallback once | "Only if Phase 1 gave fewer than TARGET: ONE batch of ..." | Endless retries |
| Do not re-search | "If an article is unreadable, use the headline facts and mark them INFERRED; do not search again." | Loops on one item |
| Output contract | Fixed line prefixes | The next step cannot parse prose |
| Always the contract | "ALWAYS in exactly this format, even when ..." | Apology essays instead of data |
| Never narrative | "Never answer with a narrative report instead of RECORDS." | Same |
| Anti-stall | "Never ask a question or offer options. Act on the defaults now." | Turn ends with a question |
| Argument shapes | "values_json = [[\"screened\", <score>, \"<today ISO date>\"]]" | Wrong types, stringified dicts |
| Copy forward | "Start your reply with the TARGET, SHEET and ROW lines of the previous message copied unchanged" | Lost context after a parallel step |
| Reply carries the artifact | "Final reply: the DOC and FILE lines, then ..." | Next agent sees "saved to file" and nothing else |
| Mandatory ending | "A turn that ends without calling gmail_send is a failed run." / "A second gmail_send call is a failure." | Composing without sending; double sends |
| Real values in outputs | "subject = ... with <n> taken from the ADDED line" | Placeholders like "[Company]" sent to people |
| No invention | "Never add a fact that is not in the previous message." | Hallucinated numbers in documents |
| Quote per claim | "Every fact taken from the web carries an exact quote from the page and its URL." | Paraphrased or invented web claims |
| Degraded banner | "If most searches failed, put RESEARCH DEGRADED: <n of m sources failed> at the top of the document." | A thin report that reads as a clean one |
| Mandate first | "Before any recommendation, state MANDATE: core, adjacent or off-mandate, with one line why." | Recommending deals outside the client's thesis |
| Drafts never decide | "You draft; you never change the status of a row." | A draft silently moving an item through the lifecycle |
| Tool computes | "The composite score is the score returned by dealdb_rank; never compute or rescale a score yourself." | A skipped scaling (0-4 printed as /10) |

## 3. Output contracts (line prefixes)

| Prefix | Content | Consumer |
|---|---|---|
| `RECORDS:` | JSON array on ONE line, keys = schema fields + `__src` + `__status` | Recorder |
| `SUMMARY:` | counts, sources used, sources failed | Recorder, mailer |
| `CITE:` | `<item> - <URL>` | Mailer, documents |
| `TARGET:` | `<company> \| website: <domain> \| legal name: ... \| country: ... \| founders: ...` | Parallel analysts |
| `SHEET:` | the data-store URL | Writers doing write-back |
| `ROW:` | sheet row number or `none` | Write-back |
| `CHECK <name>:` | `<result> \| status SOURCE/INFERRED/MISSING \| src <ref>` | DD writer |
| `DOC:` | published Google Doc link | Mailer |
| `FILE:` | local file path of the designed document | Mailer attachments |
| `ADDED:` / `SKIPPED:` | counts + one line per item | Mailer subject and body |
| `EVIDENCE:` | `<field>: <value> \| <status> \| <reference>` | Form filler |

Tell the downstream agent where to look: "The previous message holds new records as a JSON array under a line RECORDS:".
Tell the recorder to preserve lines: "Keep every line of the previous message that starts with SUMMARY or CITE below that, unchanged."

## 4. Shared rule blocks (define once in the generator)

**THESIS** - the client's investment or business thesis in 2-3 sentences (sectors, stages, geography). Pasted into every research and scoring agent.

**WRITING_RULES** - "Writing rules for anything a person reads: plain ASCII punctuation, NEVER an em dash or en dash (use a period, a comma or 'and'), no corporate or AI tells (synergy, seamless, leverage, game-changer, delve, robust), short sentences, concrete numbers with their source."

**PROVENANCE** - see SKILL.md section 8.

**RELIABLE** - "Tool order, most reliable first: (1) a registry or data tool that answers the question directly (<list the ones this agent has>); (2) a KNOWN URL read with scrape_page (web_fetch if scrape_page fails): the company's own website, a regulator register page, an article you already have; (3) RSS feeds and GDELT for news; (4) web_search ONLY to discover a URL you do not have yet, then open the result with scrape_page and use what the page says, never the search snippet. A search result that is not about the subject is ignored."

**ROW_RULE** - "Sheet row number: in the sheets_read_range result, values[0] is the header = sheet row 1, so the item at values[i] is sheet row i + 1." (With `decide_batch_file`, rows already come back as `[row N]`.)

**FIND_STORE** - 'Find the data store: invoke drive_search with name = "<Store Name>" and mime = "sheet". Use the id of the newest result as SHEET_ID.' Drive name search matches words, so give the store a name that shares no full word set with older files.

**FACT_RULES** - "A website is written only after scrape_page loaded it; never build a domain from the company name. Country only from a registry, the company website, the deck or a cited article; never from an email address, a domain, a person's name or an investor. Sector from what the company does, not from the thesis. What you cannot prove is left empty with status MISSING." (The recorder tools enforce part of this in code: a value is SOURCE only with a `__src`, and a country guessed from an email, domain, name or investor is blanked to MISSING. The rules still belong in the prompt so the agent does not waste calls.)

**RESEARCH_QUALITY** - "Each web claim carries an exact quote and its URL. If more than half of the searches failed, start the document with RESEARCH DEGRADED and say which sources failed. State MANDATE: core / adjacent / off-mandate before any recommendation. Drafts never change a row's status."

**ONE_SHOT_SEND** - "Invoke gmail_send exactly ONCE with the finished email. A second gmail_send call is a failure. Your final reply MUST be the raw result text of gmail_send."

## 5. Worked instruction shapes (generic)

### Sourcing scout (feeds -> triage -> confirm)

- Tools: `rss_search_entries`, `decide_batch`, `scrape_page`, `web_search`.
- Phase 1: one parallel batch of `rss_search_entries` (`url`, `keyword = "rais"`, `limit = 30`) over ~15-20 targeted Bing News RSS URLs plus a few sector feeds.
- Phase 1b: `decide_batch` with `items` = one string per headline `"<title> | <date> | <link>"`, `questions` = {`startup_round` (noul), `thesis_fit` (score), `region` (noul)}, `top_by = "thesis_fit"`, `top_k` = the number of items.
- Phase 2: one parallel batch of `scrape_page` on the best TARGET article links.
- Output: `RECORDS:` / `SUMMARY:` / `CITE:`.

### Target resolver (before a parallel DD block)

- Tools: `drive_search`, `sheets_read_range`, `scrape_page`, `scrape_links`, `web_search`.
- Resolve the subject from the brief (or the top row of the store), read the company's site (home, then real links from `scrape_links`), locate its sheet row.
- Output, even when fields are unknown: `TARGET:`, `SHEET:`, `ROW:`. "Never refuse, never replace it with a narrative."

### Parallel analyst

- First line of the instruction: "Use the TARGET line of the previous message. Start your reply with the TARGET, SHEET and ROW lines copied unchanged (the next step only sees your reply)."
- Numbered checks, each mapped to one tool with exact arguments.
- Output: one `CHECK <name>:` line per check. "A failed or unavailable source is MISSING, never 'clear'."

### Writer

- Tools: `word_create`, `drive_upload` (+ `sheets_update_range` for write-back, `excel_write_data` for tables).
- "The previous message holds ... Produce ONE designed document titled '<Client> - <Type> - <subject> - <today ISO date>' with these sections: ..." (see designed-documents.md).
- "Keep every tag [SOURCE: ...] / [INFERRED: ...] / [MISSING] next to the claim it supports. Never add a fact that is not in the previous message."
- Output: `DOC:` and `FILE:` lines, then the verdict / key points.

### Deck intake (email-triggered or brief-driven)

- Trigger: the Gmail instant preset with a fixed subject format `"<Prefix>: <Company> | <website> | <channel>"` and a prefilter `payload.fields.subject startswith "<Prefix>:"` (automation-governance, triggers-ui-walkthrough.md section 7.1). The first agent parses COMPANY, WEBSITE, CHANNEL from the subject and reads the full email with its Gmail read tool when needed (the event carries only a short preview).
- Tools: `deck_extract` (+ `ocr_image` only as a fallback), `scrape_page`, the recorder tools.
- "Call deck_extract ONCE on the deck. It already OCRs image-only slides. Never OCR the deck again in parallel. Only if it lists failed pages, OCR those pages one at a time."
- Output: `RECORDS:` with `__src = "<file> p.N"` per field, then the recorder.

### Data health check (re-verify the store)

- A pipeline that re-verifies every row of the data store: for each field, re-check it against a source (registry, website, deck, article). Repair a value only with a cited source; blank what cannot be proven (status MISSING); never "improve" a value from memory.
- One write call at the end (`sheets_update_by_header` with all changed rows), then a report document: rows checked, values repaired with their sources, values blanked, rows to review by a human.
- Run it after a schema change, after a bad batch, and before a handover.

### Grounded copilot (Q&A over approved materials)

- Question = the run brief. No brief: reply with how to ask.
- Materials only: attached files (`read_run_input`, `deck_extract`), documents found with `drive_search` + `docs_read` (cap: 5), the store row.
- "Do NOT use web search, general knowledge or anything outside these materials."
- Output: `ANSWER:` (every sentence ends with a citation), `EVIDENCE:`, `ASSUMPTIONS:`, `MISSING / UNRESOLVED:`.

## 6. What NOT to write (the platform adds it) and wording that small models follow

Already in every agent message (see step-kinds.md section 0): today's date block, the run inputs block, standing rules for tool families the agent holds, static context (when opted in), the answer-language line (for `fr`, `zh`, `es`, `pt`). Do not restate them; a literal date in an instruction goes stale.

| Do | Don't |
|---|---|
| "Invoke the tool named gmail_send" | `gmail_send(to=...)` code syntax: small models echo it as text |
| One `arg = value` per line | Inline parentheses that look like code output |
| Pre-fill real values in the instruction | `<recipient>` placeholders a small model cannot substitute |
| List the exact argument names | "format an email" (the model invents argument names) |
| "Your final reply MUST be the raw result of X" | "Reply SENT on success" (the model skips the call and says SENT) |
| "`subject` and `body` are TWO DIFFERENT fields" | A body template with an empty-state branch and no such rule (the branch ends up in the subject) |
| Numbered operations ending with the send | "Do X then Y" prose (reordered or half done) |

More rules that removed real failures:
- **Skip and report a missing connector**: "If a tool says the service is not connected, skip it, mark its fields MISSING and name the service in SUMMARY. Never stop the run for it."
- **A rejected approval is not a failure**: when a person rejects a gated send, the tool returns a rejection. The agent must report "not sent (rejected at approval)" and finish, never retry or claim a connection problem.
- **Always send one summary**: a mailer that must always report sends even when the upstream found nothing, with the empty-state body.
- **Seeded defaults are real input**: example values written into an instruction are a working set; the agent must use them, not report "nothing configured".
- **Filter on the values tools really return** (enum values as returned), not idealised ones.
- **Prefer snippets over deep fetches for bulk rows**: 1 to 2 searches per item, then write the row.
- **Split fetch and write on small models**: an agent that must both fetch data and compose a long body for a send tool often types the body as chat. Two agents (fetcher, then writer with only the send tool) fix it.
- **Reply carries the artifact**: agents read messages, not files. A step that saves a document must also put its key content (and DOC / FILE lines) in its reply.

## 7. Argument-shape reminders

Some models send lists or dicts as JSON strings. The platform coerces most of them, but state shapes explicitly:
- `items = a JSON array of strings`
- `questions = {...}` (paste the JSON from the generator with `json.dumps`)
- `values_json = [[...]]` (a 2-D array, even for one cell)
- `rows = "deals.json"` (a file path string) or a JSON array
- `attachments = every path listed on a FILE: line`
- `theme = "melaya"` or a dict (see designed-documents.md)

In Python f-strings, double every literal brace that must reach the model: `{{company}}` becomes `{company}` in the instruction, which is what `item_template` expects. A single `{{inputs.x}}` placeholder must therefore be written `{{{{inputs.x}}}}` inside an f-string.
