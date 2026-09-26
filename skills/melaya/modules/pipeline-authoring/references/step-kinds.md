# Step kinds and routing, exact behaviour

A pipeline is an ordered list of steps (`steps[]`). Each step has a unique `id` and a `kind`.
There are five kinds: `agent`, `parallel`, `condition`, `loop`, `decide`.
This page says exactly what each one does at run time, what the next step receives, and the traps.

Plain words:
- **Reply**: the final text an agent produces. Tool results are NOT passed on; only the reply is.
- **Previous output**: the reply (or joined replies) of the step just before.
- **Builder**: the Melaya service that turns your config into the program that runs.

## 0. What every agent message contains (you do not write these)

The builder assembles each agent's input message from:

1. The agent's `instruction` (with `{{inputs.<key>}}` / `{{brief}}` filled in).
2. From the second step on: "Output from <previous step>:" and the previous output.
3. A date block: today's UTC date, weekday, ISO week and a rule not to invent dates.
4. The run inputs block ("Run inputs for THIS run": brief, declared values, attached file list with a short preview), when the run has inputs.
5. On a run started by an event trigger: the event, fenced and labelled untrusted, in the FIRST agent's first message only.
6. Tool playbooks: standing usage rules for some tool families, only for tools that agent holds.
7. The static-context block, when the agent has `include_context: true` and the pipeline has documents.
8. A language line when `user_lang` is `fr`, `zh`, `es` or `pt`.

Cross-run memory (when `persistent_memory` is on) goes into the agent's system prompt, not the message.

Consequences for authors:
- Never write today's date into an instruction; say "today's ISO date from the date context block".
- Do not restate a tool's standing rules; they arrive with the tool.
- The first step has no "previous output". Its subject must come from its instruction or the run inputs.

## 1. `agent`

```json
{"id": "s1", "kind": "agent", "label": "Deal Scout", "agent": { ...agent object... }}
```

- One agent, run once. Its reply becomes the next step's input.
- The agent works in a think, call tools, read results loop until it replies. Its iteration ceiling comes from the agent persona, not from the config (small local models are clamped harder, see limits-costs-languages.md).

## 2. `parallel`

```json
{"id": "s2", "kind": "parallel", "label": "Lenses", "joinStrategy": "concat",
 "agents": [ {...}, {...}, {...} ]}
```

- All agents start at the same time. Each gets its OWN message: its own instruction plus the previous output.
- When the parallel step is the FIRST step, each agent gets only its own instruction (there is no previous output).
- A failing agent is logged and dropped; the run continues with the others ("degraded mode"). If all fail, the next step gets an empty input.
- `joinStrategy`:

| Value | Next step receives |
|---|---|
| `concat` (default) | Every successful reply, joined with a `---` separator line |
| `summary` | The same, prefixed with "Parallel summary:" |
| `first` | Only the first successful reply (the others are discarded) |

Any other value behaves like `concat`.

Traps:
- The joined output contains ONLY the parallel replies. Lines from the step BEFORE the parallel block (TARGET, SHEET, ROW) are gone unless an agent copies them. Tell one (or every) parallel agent: "Start your reply with the TARGET, SHEET and ROW lines of the previous message copied unchanged (the next step only sees your reply)."
- Siblings never see each other. "Use the table produced by the traction analyst" inside a sibling is structurally broken. Merge at the next step instead: "Every item from EITHER input must appear."
- Two agents with the same `name` in one pipeline are renamed "Name", "Name 2". Give every agent a unique name.
- Quality loop: each parallel agent is scored on its own; `enforce` degrades to scoring only (see quality-loop.md).

## 3. `condition` (keyword routing)

```json
{"id": "s3", "kind": "condition",
 "rules": [
   {"keyword": "ESCALATE", "label": "Escalation", "agent": { ...agent... }},
   {"keyword": "PROCEED",  "label": "Proceed",    "agent": { ...agent... }}
 ],
 "defaultAgent": { ...agent... }}
```

- The previous output is upper-cased; each rule's `keyword` is upper-cased and searched as a substring. First match wins, in rule order.
- A rule with no agent (`"agent": null`) passes the previous output through unchanged. No `defaultAgent` means "no match = pass through".
- The branch agent's message is only "<label> condition." plus the previous output, the date and the run inputs block. Its `instruction` becomes the agent's standing goal (system level), not message text, and it receives no static context and no tool playbooks. Keep branch instructions short and self-contained.
- Branch agents are not scored by the quality loop. If a branch does important work, route to a label only and put the work in a following `agent` step, or accept that it is unscored.
- Substring matching is loose: "PROCEED" also matches "DO NOT PROCEED". Make the upstream agent end with a controlled line such as `VERDICT: ESCALATE` and use `VERDICT: ESCALATE` as the keyword.
- Shape trap: rules use `rules` + `defaultAgent`. A plural `agents` + keyword list shape does not compile.

## 4. `loop`

```json
{"id": "s4", "kind": "loop", "agent": { ...agent... }, "maxIterations": 3, "stopKeyword": "DONE"}
```

- One agent (singular `agent`), repeated up to `maxIterations` times (default 3; keep it between 1 and 10).
- Every iteration re-issues the agent's instruction as the directive, with the previous iteration's reply attached as reference, so the agent advances to the next item instead of echoing.
- A reply containing `stopKeyword` (case-insensitive substring) ends the loop early.
- Each iteration's message tells the agent it runs unattended and must advance with real tool calls; static context is not included in iteration messages.
- The quality loop scores the loop once, on the final reply.
- The step's output is the LAST iteration's reply only. Earlier iterations are not concatenated: make each iteration carry forward what matters, or write results to a file or sheet as you go.
- Use only for real iteration ("process the next unprocessed row"). A writer or publisher placed on a loop step stalls, repeats itself or never reaches its tools; use `agent`.
- `{{inputs.<key>}}` placeholders work in a loop instruction.

## 5. `decide` (System One gate)

```json
{"id": "s2", "kind": "decide", "name": "anything_new",
 "decide": {
   "engine": "laya",
   "questions": {
     "has_new_items": {"type": "noul", "instructions": "The previous message lists at least one new record."},
     "urgency": {"type": "score", "instructions": "How urgent is this for the team?", "criteria": ["low", "medium", "high"]}
   },
   "act_when": {"question": "has_new_items", "op": ">", "value": 0.5},
   "on_unavailable": "skip"
 }}
```

What it is: one direct call to a small decision model ("System One") that answers typed questions with probabilities and writes no text. No LLM, no agent, no tool budget.

- `engine`: `laya` (default; free, run by Melaya) or `jev` (paid hosted twin; needs the user's Jev connector). An unknown value falls back to `laya`.
- `questions`: 1 to 8. Names are lower snake case (start with a letter, up to 64 chars). Types:
  - `noul`: probability 0..1 that the statement is true. Write a statement, not a question.
  - `score`: `criteria` = an ORDERED list of 2 to 10 short level labels, lowest first. Answer = expected level index (a decimal).
  - `choice`: `criteria` = a map option -> description. Answer = the winning option (plus probabilities).
- `act_when` (optional): `{"question", "field"?, "op", "value"}`. `op` is one of `==`, `!=`, `>=`, `<=`, `>`, `<`, `in` (`in` needs a list value). Without `field` the comparison uses the question's own answer (probability, level or option). `field` may also be `confidence` or `act_probability`. A missing answer compares as false.
- `on_unavailable`: `skip` (default) closes the gate when the engine cannot answer and `act_when` is set; `act` lets the run continue.
- Without `act_when` the gate never closes; it only adds its answers.

What it judges: the previous output, cut to its first 4,000 characters. Make the upstream agent put the deciding facts FIRST. When a decide step is the very first step of a run started by an event trigger, it judges the event payload instead; on any other first-step run it has nothing to judge, so do not put it first.

What happens next:
- Gate open: the next step receives the previous output plus a `## Decision (System One)` block with every answer.
- Gate closed: the run ends right there and returns the gate result as its final output. Remaining steps are skipped. Crew memory is still saved.

Placeholders `{{inputs.<key>}}` / `{{brief}}` work inside questions, options and examples.
A bad shape fails the save with a 422 error that names the problem.
For many items inside one agent, use the tools `decide_batch` / `decide_batch_file` instead (same question schema; see the data-spine skill).

## 6. Edges: leave them empty unless you need a real graph

Two ways the builder can order steps:

| `edges` | Ordering | Supports |
|---|---|---|
| `[]` (empty) | Linear: steps run in list order | All five kinds. The safe default |
| Non-empty | Graph dispatcher: after each step, outgoing edges decide the next step | `agent`, `parallel`, `decide` only |

Hard caveat: when `edges` is non-empty, `condition` and `loop` steps are NOT compiled. They become an inert placeholder and the run silently skips their agents. The preview shows such a phase as a comment only. If a pipeline has a condition or loop step, send `"edges": []`.

Graph edges (only when you truly need branching or cycles between steps):

```json
"edges": [
  {"id": "e1", "fromStepId": "s1", "toStepId": "s2", "condition": "\"ESCALATE\" in output.upper()", "label": "escalate"},
  {"id": "e2", "fromStepId": "s1", "toStepId": "s3"}
]
```

- Edges are read by `fromStepId` / `toStepId`. Edges written as `{"from", "to"}` are ignored, which silently falls back to "next step in the list".
- `condition` is a short Python expression over `output` (the previous reply as text). Conditional edges are tried first, in order; an edge without a condition is the fallback; no matching edge means "next step in the list".
- Any step visited more than 10 times stops the run (cycle guard).
- Prefer a `condition` step on a linear pipeline over graph edges: it is simpler and fully supported.

## 7. Choosing a topology

| Need | Use |
|---|---|
| Fetch then write | 2 `agent` steps (or one agent that does both on a strong model) |
| Independent lenses on one subject | `agent` resolver -> `parallel` analysts -> `agent` writer |
| Stop cheaply when there is nothing to do | `agent` fetch -> `decide` -> the expensive steps |
| Different follow-up by verdict | `agent` with a controlled VERDICT line -> `condition` |
| Work through N items one by one | `loop`, only when the items cannot be batched in one call |
| Human approval before a send | Put the send in the LAST agent and gate only that tool |

Keep at most about two data-carrying hand-offs between where a value is fetched and where it is printed. Field-level data survives one hand-off, rarely three.
