# Reading results for a non-technical user

Two tools summarise quality: `melaya_run_diagnosis` (one run: why it went the
way it did) and `melaya_eval_report` (many runs: how the agents are doing
overall). Their output is technical. Your job is to turn it into a verdict,
an action and, only if asked, the detail.

Words to define once, in plain language, the first time you use them:

| Term | Say it like this |
|---|---|
| Run | One time the pipeline was carried out, from start to finish |
| Step / phase | One stage of the pipeline, handled by one agent (or several at once) |
| Tool call | One action an agent took: a search, reading a sheet, sending an email |
| Evaluation (eval) | An automatic check that grades each step's answer after it is written. In "observe" mode it only grades; it never changes the answer |
| Accepted | The check found the step's answer good enough |
| Approval | A pause where a person must click Approve or Reject before an action (like sending an email) happens |

## melaya_run_diagnosis (one run)

Call: `melaya_run_diagnosis { "run_id": "<id>", "message_limit": 80 }`.

What comes back and how to read it:

| Part | What it tells you | Plain-words reading |
|---|---|---|
| Per-phase eval verdicts, with reasons | Whether each step's answer passed its check, and why not | "Step 2 (research) was graded as weak because it cited no sources" |
| Tool forensics | Tool calls that went wrong, grouped by kind | See the table below |
| Agent messages (previews, oldest first) | What each agent actually said | Use them to check every step ran; a step with no message did not run |
| Token and dollar cost | What the run cost in model usage | "This run cost about 3 cents in AI usage" (costs of your own model key are billed by that provider) |

Forensics kinds, translated:

| Kind | Say it like this | Usual action |
|---|---|---|
| `auth` | "Melaya could not get into <service>; it needs you to reconnect it" | Send the `melaya_connector_connect` link |
| `error` | "<tool> returned an error" | Look at the reply: wrong input, dead link, or the service was down |
| `empty` | "<tool> found nothing" | Fine if the source truly had nothing today; otherwise fix the query |
| `oversized` | "The result was too big to read in one go" | Save to a file and read from there |
| `bad_args` | "The agent asked the tool the wrong way" | Clarify the instruction |
| `contract` | "The answer was not in the format the next step expects" | Clarify the output format |
| `stall` | "The agent stopped making progress" | Add a stop rule and a budget |
| `coverage` | "A step skipped a tool it was supposed to use" | Make that phase explicit in the instruction |

Eval failure kinds you may see: `empty` / `empty_result` (no real content),
`tool_error` (a tool failed underneath), `false_fallback` (the agent said a
source failed although it returned data), `ungrounded` / `citation_missing`
(claims without a source), `missing_sections`, `schema_invalid` (wrong
structure), `timeout`.

How to decide the verdict for the user:

1. `outcome` from `melaya_run_status` says success, failure or cancelled.
2. Check every configured step has messages. Missing later steps after a
   "success" means the run was interrupted: re-run, and tell the user so.
3. Any `auth` forensics: the user must reconnect something. Say which.
4. Otherwise, the eval verdicts and the artifact check decide whether the
   result is good. A run is good only when the file, sheet or email it was
   meant to produce exists and is right.

Example message:

> The weekly digest ran and your email arrived. One source (the news feed)
> returned nothing today, so that section says "no news found". Nothing for
> you to do.

> The run stopped at step 3. Melaya lost access to your Google Drive during
> the run. Please open this link and press Allow, then I will run it again.

## melaya_eval_report (many runs)

Call: `melaya_eval_report { "project": "<project>", "range": "week" }`
(`range`: `week` = last 7 days, `month` = last 30, `all`).

Fields and how to read them:

| Field | Meaning | Plain-words reading |
|---|---|---|
| `totalRuns` | Runs started in the window | "Your pipelines ran 42 times this week" |
| `evaluated` | Runs that had automatic checks switched on | If far below `totalRuns`, some agents have no quality check configured (`loop_policy` missing): results for those runs are not graded, not bad |
| `acceptanceRate` | Share of graded steps that passed, from 0 to 1 | 0.64 = "about two out of three steps passed their check" |
| `avgScore` | Average grade, 0 to 1 | Trend matters more than the number |
| `failureKinds` | Count per failure kind | The biggest bar is where to look first: many `tool_error` usually means a connector or a source problem, not bad writing |
| `finalizedPhases`, `failedPhases` | Steps graded, and how many of those failed | Detail behind the acceptance rate |
| `totalCost`, `inTokens`, `outTokens` | Model usage and dollar cost in the window | "About $1.60 of AI usage this week" |
| `costPerAccepted` | Cost divided by accepted results | "Each good result cost about 1 cent" |

Rules of thumb:

- Compare week against week for the same project; one bad day on a flaky
  source is noise.
- A falling acceptance rate with rising `tool_error` points to a connector
  that expired or a source that changed: run `melaya_connector_list` and
  check `auth` forensics on a recent run.
- A high acceptance rate is not proof the outputs are right. The checks are
  automatic; spot-check a real artifact each week.
- Never present the numbers as a grade of the user's work; they grade the
  agents.

Example message:

> This week your 3 pipelines ran 21 times. About 8 in 10 steps passed their
> automatic checks. Most failures came from the news source timing out, not
> from your connectors. AI usage cost about $0.40 in total. I suggest no
> change; I will look again next week.
