# Template authoring standards (integrator summary)

The bar: a user loads the template, edits only the `[START EDIT ME]` block, runs it, and gets a
correct, useful artifact on the first try. A template is "done" only when its delivered artifact
(not the run status) has been validated on a real run with real data. Never ship empty or fake
agents.

## 1. Card: name and description

| Field | Standard |
|---|---|
| Template name (what users see on the card) | Title Case, never snake_case. Acronyms upper (AI, SEO, CRM, KPI, PDF, RSS, API, KYC, AML). Brands proper-cased (GitHub, Gmail, LinkedIn, Slack, Notion). |
| Pipeline `display_name` | Same human title. The pipeline `name` stays the canonical snake_case handle. |
| Description | At least 200 characters (200-480 is healthy). Shape: what it actually does -> the benefit -> the one-time setup ("Connect the required service once in Connectors, set your defaults at the top of each step, and run"). |
| Trigger hint | If designed for an event trigger, name the preset in the description as text only. |

## 2. Editable block (FORMAT v2)

```
[START EDIT ME]
- FIELD = <real default> (what it is)
[END EDIT ME]

<directive that uses the configured values above>
```

- Exactly one pair per agent instruction, markers on their own lines, at the top.
- Only where the prompt needs a value no tool can resolve: categories, keywords/ICP, thresholds,
  watchlists, named recipients, URLs the API cannot list.
- No block when the tool resolves the value itself (own address, own events, own account).
- Defaults are real and runnable: validation runs them verbatim. No `you@example.com`, no dummy
  URNs that fail, no `<token>`.
- Shared (community) templates: every default and every line of user-facing copy is brand-neutral.
  For an id the user must fill, use a real-shaped dummy plus a hint in parentheses, for example
  `CHANNEL_ID = 000000000000000000 (enable Developer Mode, right-click the channel, Copy ID)`.
- Never write the marker tokens in prose; refer to "the configured values above".
- The per-run subject is a run input, never an editable-block value.

## 3. Config standards

| Area | Rule |
|---|---|
| Topology | Agents embedded in `steps[]`. Step kinds: `agent`, `parallel` (`agents[]` + `joinStrategy` concat / last / vote), `loop` (singular `agent`, bounded iterations, stop keyword), `condition` (`rules` of `{keyword, agent}` + `defaultAgent`), `decide` (System One gate, never first unless it judges a trigger payload). Use `loop` only for genuine iteration. |
| Tools | Every agent has its own explicit minimal `agent_tools`. An agent with an empty list inherits the pipeline-level `tools` list (can silently gain a send tool and double-send). Leave top-level `tools` empty. |
| Evals | Every agent: `loop_policy {"mode":"observe_only","evaluator":"default"}` (an object; a string is ignored). `enforce` only for a step that should retry, and a write step only with `sideEffectClass "non_idempotent_write"`. |
| Context | Set `include_context` deliberately per agent (false when no docs are attached); `include_rag_tool` false unless retrieval is used. |
| HITL | Every send / post / external write listed in that agent's `human_approval_tools`; pipeline `hitl_mode "safe"` (other modes drop the per-agent list). Local artifact creation (files, documents) is the deliverable and is not gated. Put the send step last. |
| Senders | Call the send tool exactly once ("a second call is a failure"); subject and body are different fields; never forward raw errors (name the service and say to reconnect it). |
| Run inputs | Work on the run's brief / files when present, fall back to the normal source when absent. Declare `inputs` only when a value must be typed or required. |
| Memory | `persistent_memory: true` only for recurring de-dup jobs; the last agent must print a stable fact line (for example `USED ITEM: <name>`) and read prior lines first. Leave off for stateless one-shots. |
| Triggers | Never in the payload (no trigger config, URLs, secrets or trigger ids). A template may carry `decide` steps, `listen_trigger_wakeups`, and HITL on writes; the user adds triggers after loading it. |
| Models | Pick a model available on the target account (`melaya_model_list`). Validate on a cheap fast cloud model. |
| Paid tools | Not by default; prefer free / keyless tools. Skip and report gracefully when an optional connector is not connected. |
| Personas | The agent role you pick (its `factory` from `melaya_pipeline_registry`) comes with a default persona that can pull against your instruction (for example a copywriter persona turning a plain release note into clickbait); set `system_prompt_override` on that agent when it conflicts. |
| Documents | Human-facing outputs go through the design tools (Word / slides / spreadsheet / PDF with a theme), never plain text. |
| Copy | Human-readable output uses ASCII punctuation, no em or en dashes, no AI filler phrases. |

## 4. Small-model patterns (why templates fail with correct intent)

1. Collapse fragile multi-step hand-offs: field-level data survives about one hop. Keep at most two
   data-carrying hops; merge fetch + curate + act where possible.
2. A parallel sibling's output is invisible to the other siblings. Merge at the join step.
3. The reply carries the artifact: downstream agents read messages, not files. A step that writes a
   file must also put the full text in its reply.
4. Anti-stall: never ask a question or offer options at runtime; commit to one result this turn.
5. Mandatory endings: "after N searches STOP; your final message MUST be the table"; sender steps
   end with the send call ("a turn that ends without calling <tool> is a failed run").
6. Seeded defaults are real input; never treat them as "nothing configured".
7. Filter on the enum values the tool really returns.
8. No false fallback: if a tool returned data, use it.
9. A gate rejection during validation ("rejected by user", audited at the gate) is the expected
   success signal for a gated write, not a connector failure.
10. Always-send: a reporter sends its one summary even on an empty branch.
11. Public-data realism: do not demand data the web does not publish; accept ranges; cap searches
    per item, then write.
12. Instruction wording: "Invoke the tool named X" with one `arg = value` per line; no Python call
    syntax; exact argument names; pre-substituted values.

## 5. Validation standard (before saving as a template)

GREEN:
- Run outcome done, no errored spans (a single retried transient stream error: rerun once before
  judging).
- Every tool in the pipeline was called; the reporting tool fired (or a gated write was composed
  with real arguments and audited at the gate).
- The artifact carries real, substantive, on-topic data: no raw JSON dump, no forwarded error
  text, subject distinct from body, real dates (the date is injected automatically; no literal
  dates in instructions).
- A second run from cold state behaves the same.
- Memory templates: 3-4 runs, later runs report only new or changed items.
- Run-input templates: one run without inputs, one with a brief + real file (shows a
  `read_run_input` call and the brief reflected in the artifact).
- Trigger-ready templates: one real triggered run, approval card on every write, and the same
  pipeline still works by hand.

RED: empty body ("0 sent", "unavailable"), zero external-write calls on an outreach template, a
tool auth error, or a false fallback. Empty is not success.

Legitimately empty (the tool confirms there is no data for this account) is correct behaviour:
report it honestly, do not fake a fix, and do not call it validated.

## 6. Publishing

- The user saves the validated pipeline with "Save as Template" in the Agent Builder. It is
  private to the creator; it can be shared into team projects.
- Community listing requires admin validation; unvalidated community templates are withheld from
  `melaya_pipeline_templates`.
- A shared template is immutable: to change it, duplicate and publish a new version.
- Template payload instructions stay in English; only the card (name, description) is localised.
