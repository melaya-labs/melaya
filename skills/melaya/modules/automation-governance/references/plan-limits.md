# Plan limits that affect automation

Check the user's plan with `melaya_account_subscription` and current usage with `melaya_account_usage` BEFORE promising a cadence, a trigger or a volume. Limits below are the platform's documented values; if a call is refused, the refusal is the truth: report it, never loosen the design silently.

Plans: Sandbox (free), Outpost, Forge, Bastion, Citadel (Team / Scale / Private).

## 1. Runs and schedules

| Limit | Sandbox | Outpost | Forge | Bastion | Citadel |
|---|---|---|---|---|---|
| Scheduled pipelines (cron) | no (manual only) | no (manual only) | yes, at most every 12 h | yes, at most every 1 h | yes, at most every 5 min (Scale and Private: no floor) |
| Runs per month | 10 | 50 | 120 | 500 | 5,000 / 10,000 / unlimited |
| Concurrent runs | 1 | 2 | 3 | 5 | 15 / 50 / 100 |
| Cloud execution | no | no (runs on the user's local runner) | yes | yes | yes |
| Cloud AI providers in pipeline runs | no (Melaya AI or local models) | yes | yes | yes | yes |
| Run history kept (logs) | 7 days | 14 days | 30 days | 90 days | not deleted automatically |

What this means in practice:
- A cron tighter than the plan floor is refused at `set`, not loosened. Example: on Forge, `0 7 * * 1-5` (weekday mornings) fits; `0 */6 * * *` (every 6 h) does not.
- Every scheduled and triggered run counts toward the monthly runs. A weekday-morning schedule is about 22 runs a month per pipeline: on Forge (120) that leaves room for about 5 such pipelines plus manual runs. Do this arithmetic with the user.
- When all concurrent lanes are busy, a new run waits in a queue rather than failing; the monthly cap refuses.
- On Outpost the pipelines run on the user's local runner, so schedules would also need the runner online; scheduling itself starts at Forge.
- Run history older than the retention window disappears: monitor within it.
- A user can also be restricted by their organisation: without the "schedule pipelines" permission, schedule calls are refused. In a shared project, an editor can run pipelines with the project's connectors; a viewer cannot.

## 2. Event triggers (beta: Forge and above for now)

| Limit | Sandbox | Outpost | Forge | Bastion | Citadel |
|---|---|---|---|---|---|
| Beta access today | no | no | yes | yes | yes |
| Triggers per user (push triggers included) | 3 | 3 | 20 | 100 | 500 |
| Stream sources (WebSocket or SSE) | 0 | 0 | 1 | 3 | 10 |
| Events per minute, all triggers together | 60 | 60 | 600 | 3,000 | 12,000 |
| System One decisions per minute for triggers (events x questions) | 120 | 120 | 240 | 300 | 300 |
| Poll interval floor | 15 min | 15 min | 5 min | 2 min | 1 min |
| Poll calls per UTC day, all poll triggers together | 300 | 300 | 6,000 | 30,000 | 100,000 |
| Crews listening for wake-ups at once | 2 | 2 | 5 | 10 | 25 |
| Live gateway connections (Discord, Slack) | 0 | 0 | 1 | 3 | 10 |
| Kept-alive exchange account streams (engine private events) | 0 | 0 | 2 | 10 | 50 |

Per trigger (any plan): `max_events_per_min` 1-6000 (default 60), `max_runs_per_day` 0-10000 (default 50), `max_concurrent_runs` 1-10 (default 1). All triggers of a user together may use at most half the plan's concurrent-run lanes (minimum 1). Jev decisions: at most 1000 per trigger per UTC day, billed to the user's own Jev account.

Worked examples:
- Forge, one poll every 5 minutes = 288 polls a day: fine against 6,000. Twenty such polls = 5,760: at the limit.
- Forge, trigger with 3 System One questions: 240 / 3 = 80 events per minute can be judged. Add a prefilter and `coalesce_ms` if the source is louder.
- Forge has 3 concurrent runs, so all triggers together start at most 1 run at a time; others wait or are skipped (`user_concurrency`). Keep triggered pipelines short, or use `notify` / a read-only `tool_call` for high-volume sources and a scheduled digest for the heavy work.

## 3. Cost is separate from plan limits

Plan limits cap counts. Model spend is capped per run by `max_cost_usd` on the pipeline and on individual agents (cost-security.md). Provider-native web search is billed by the agent's own model provider. Check real spend with `melaya_account_usage` after the first unattended day.
