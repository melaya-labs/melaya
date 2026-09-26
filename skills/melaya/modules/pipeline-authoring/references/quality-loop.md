# Quality loop (evals): observe vs enforce

Plain words:
- **Eval**: an automatic check of one agent's work after it replies. It looks at the reply AND at every tool call and tool result of that step.
- **Verdict**: pass ("finalize") or fail with a reason ("failure kind"), plus a score from 0 to 1.
- **Quality loop**: what the pipeline does with the verdict. Observe = record it. Enforce = retry the step with a lesson.

## 1. How to switch it on

Per agent, as an OBJECT:

```json
"loop_policy": {"mode": "observe_only", "evaluator": "default"}
```

Full shape: `{"mode": "off" | "observe_only" | "enforce", "evaluator": "default", "maxAttempts": 1-5, "sideEffectClass": "read_only" | "idempotent_write" | "non_idempotent_write"}`.

- A pipeline-level `loop_policy` is the default for agents that set none. An agent-level value wins.
- A bare string (`"loop_policy": "observe_only"`) is ignored and the loop stays off. No error is raised.
- camelCase `loopPolicy` is accepted too.
- Without it, `melaya_eval_report` counts the runs as unevaluated and the Eval page has nothing to show. Some run paths add observe by default, but never rely on that: set it on every agent.

Builder UI: each agent card on the Pipeline tab has a **Quality loop** control: Off / Observe / Enforce, with **Max attempts** under Enforce.

## 2. The three modes

| Mode | What happens | Cost | When |
|---|---|---|---|
| `off` | No check. Byte-identical to a pipeline without evals | None | Throwaway utility steps only |
| `observe_only` | The agent runs once. The eval scores it and publishes the verdict to the Eval page and to `melaya_eval_report`. The reply is never changed, nothing is retried | Negligible (deterministic checks) | The standard for EVERY agent, including writers and senders |
| `enforce` | On a failed verdict the step re-runs with a revision note and a short lesson, up to `maxAttempts`, then accepts, escalates or halts | Each retry is a full extra agent turn (tokens and time) | A step whose checks you trust, after observing it on real runs |

Enforce details:
- `maxAttempts` default 2, clamped to 1..5.
- `sideEffectClass` default `read_only`. Set `non_idempotent_write` on any agent that sends, posts, pays or appends: such a step is clamped to ONE attempt, so a retry can never send twice. `idempotent_write` (overwriting the same cells, re-uploading the same file) may retry.
- A retry is judged only on the new attempt's tool calls, not on the earlier ones.
- On `parallel` and `loop` steps, `enforce` degrades to observe (scored, not retried).
- Start with observe on real traffic; switch a step to enforce only when its failures are genuine and retrying helps.

## 3. What the eval checks, in order (first failure wins)

1. **Empty reply**: the agent produced no text -> fail `empty_reply`.
2. **Tool forensics, hard failures** (the root cause, so it outranks prose checks):
   - `tool_error`: a tool returned an auth error (401, 403, not configured, expired) or an error body -> fail, score 0, the tool is named.
   - `false_fallback`: the reply says "unavailable / could not / not connected" while a tool in that step actually returned data -> fail, score 0. The single most useful check.
3. **Deterministic checks**: expected JSON shape, required sections present, no dangling tool-call text, and (when enabled) that the reply cites something fetched in this step.
4. **An LLM judge, last and reject-only**: it can veto; it can never bless a write, a trade, a citation or a grounding claim.

If all pass, the verdict is "finalize" with `score = 1 - soft penalties`.

## 4. Tool forensics (stamped on every verdict, even a pass)

Each tool result is classified:

| Status | Meaning | Effect |
|---|---|---|
| `ok` | Real data of normal size | none |
| `empty` | Nothing came back. Informational for "my inbox / my calendar" tools (can be legit), a warning for news, search and market tools | soft penalty |
| `error`, `auth` | The tool failed | hard fail (`tool_error`) |
| `transient` | Rate limit or temporary failure | soft penalty |
| `oversized` | Output large enough to crowd the model's context | soft penalty |
| `bad_args` | Called without a required argument or with a wrong type | soft penalty |
| `contract` | A write tool gave no success confirmation (no id / ok / sent) | soft penalty |
| `stall` | The reply asks the user a question or offers options instead of acting | soft penalty |
| `coverage` | A send or write tool was granted but never called, and nothing else delivered | soft penalty |

The summary line reads like "3/4 tools ok (1 empty, 1 contract)". `melaya_run_diagnosis` returns it per phase.

What this means for authoring:
- Give every agent ONLY the tools it must use. A granted-but-unused send tool shows as `coverage`.
- Anti-stall wording ("never ask a question, act on the defaults") removes `stall`.
- "A failed source is MISSING, never 'unavailable' when data came back" removes `false_fallback`.
- A send agent must end with the raw result of the send tool, so the confirmation is visible.

## 5. Side-effect classes of tools (why reads and writes behave differently)

Every tool in the registry is marked read-only or not (`read_only` in `melaya_pipeline_registry` results). That flag drives three behaviours:

| Behaviour | Read-only tool | Non-read-only tool (writes, sends, creates, even local files) |
|---|---|---|
| Write-confirmation check in the eval | exempt | checked (`contract`) |
| Run started by an event trigger | runs freely | pauses for human approval, listed or not |
| Enforce retries | normal | set `sideEffectClass: "non_idempotent_write"` so it never repeats |

Scheduled and manual runs gate only the tools you list in `human_approval_tools` (with `hitl_mode: "safe"`).

## 6. Reading the results

- Eval page in the app (per phase: pass or fail, score, failure kind, tool forensics).
- `melaya_run_diagnosis` for one run.
- `melaya_eval_report` for the account or one project: acceptance rate, failure-kind histogram, average score, token and dollar cost, cost per accepted run.
- Details of the validation loop: the `../../../modules/validate-debug/GUIDE.md` skill.

## 7. Checklist

- [ ] Every agent has `loop_policy` as an object, `observe_only` by default.
- [ ] Senders and appenders on `enforce` carry `sideEffectClass: "non_idempotent_write"`.
- [ ] No agent holds a write tool it is not meant to call.
- [ ] After the first real run, every phase finalizes with real `ok` data and no `tool_error` / `false_fallback`.
