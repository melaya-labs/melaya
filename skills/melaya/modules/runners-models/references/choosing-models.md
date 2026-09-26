# Choosing models: cloud keys, Melaya AI, System One, per-agent choice

## The four families

| Family | Provider ids (examples) | Runs | Who pays | Setup |
|---|---|---|---|---|
| Cloud, your own key | `qwen`, `openai`, `anthropic`, `google`, `mistral`, `deepseek`, `groq`, `cerebras`, `nvidia`, `openrouter`, `grok`, `moonshot`/`kimi`, `zhipu`, `together`, `fireworks`, `bedrock`, `azure_ai`, `openai_compatible`, `litellm`, ... (full list: the `provider` enum of `melaya_model_list`) | Cloud or runner | The provider bills the user's key | Paste the key in Settings > Connectors |
| Subscription CLI | `claude_code`, `codex`, `github_copilot` | Runner only | The user's subscription | Sign in on the runner computer (`subscription-cli-providers.md`) |
| Local | `ollama`, `lmstudio` | Runner (or a company server URL) | Nobody | Install and load a model (`local-models.md`) |
| Melaya AI | `melaya_ai` | Cloud or runner | Free, daily allowance | None |

## Cloud keys (bring your own key)

1. Ask which provider the user already has an account with. If none: several have free tiers (Google Gemini, Groq, Cerebras, NVIDIA NIM, SambaNova, OpenRouter's `:free` models). Free tiers are rate-limited, not broken: when the cap is hit the model pauses and comes back.
2. `melaya_connector_connect { "service": "<provider id>" }` tells the user where to store the key. Never ask for the key in chat.
3. `melaya_model_list { "provider": "<id>" }` until `status: "ok"`. Copy the id from `models` (newest first). `force_refresh: true` bypasses the one-hour cache after the user adds a key.
4. Write `model_provider` and `model_name` on the agent.

Some providers take a per-user address as well as a key: `azure_ai` (the model name is the user's deployment name), `openai_compatible` and `litellm` (the user's server address), `bedrock` (a region), `gonka` (a broker address). The Connectors form asks for it.

Plan note: the free Sandbox plan cannot use cloud AI providers (runs are refused as `tier_insufficient`). Upgrading, or using local, subscription CLI or Melaya AI models, solves it.

## Melaya AI

- Provider `melaya_ai`, model `melaya-demo-2b` today (always confirm with `melaya_model_list { "provider": "melaya_ai" }`).
- Free on every plan with a daily token allowance; requests wait in a shared queue and show their position.
- A small demo model: it can call tools but is weak at multi-step work and slow under load. The app itself tells users to connect a frontier model for real work.
- Use it to show "Melaya works" with no setup. Do not validate or run production pipelines on it.

## System One decisions (not a chat model)

For "score these 500 items", "which of these fits", "is this relevant yes/no", use:

- `decide_check` (one yes/no or one choice), `decide` (several typed questions on one item), `decide_batch` / `decide_batch_file` (many items at once, free, run on Melaya's own servers and reachable from cloud and runner runs).
- A `decide` step in the pipeline to gate the rest of the run cheaply.
- `jev_*` tools: the paid hosted twin with the same question format, faster, billed per use.

They return probabilities, not prose. This is cheaper and more consistent than asking an LLM to rate items one by one. Method: `../../../modules/data-spine/GUIDE.md`.

## Cost vs quality vs speed

| Priority | Pick | Trade-off |
|---|---|---|
| Lowest cost, good enough | A "plus/flash" class cloud model (e.g. `qwen3.7-plus`, a flash model) | Occasional format slips on very long outputs |
| Best writing / reasoning | A frontier model (`anthropic` Sonnet/Opus class, `openai` top model, `qwen3.x-max`) on the one agent that needs it | Several times the cost per token |
| Zero API spend | `claude_code` / `codex` / `github_copilot` | Runner must be on; subscription rate limits |
| Privacy | `ollama` / `lmstudio` | Slow, smaller context, weaker |
| Speed | A fast cloud host (`groq`, `cerebras`) or a flash model | Smaller model families |

Practical rule: build and validate everything on one cheap fast cloud model, then upgrade only the agents whose output is not good enough, one at a time, and re-validate.

## The validation model

Validation runs happen many times while a pipeline is being built, so they must be cheap and quick:

- Use a fast cloud model with reliable tool calling, for example `"model_provider": "qwen", "model_name": "qwen3.7-plus"` (after `melaya_model_list { "provider": "qwen" }` returns `ok` and lists it).
- Not Melaya AI, not a local model on a computer without a graphics card: runs would take many minutes per turn and hide real problems behind slowness.
- If the user has no cloud key and refuses to add one, validate on `claude_code` (runner) or a local 8B model with a graphics card, and set expectations about time.

## Cost control

- Local and subscription CLI models cost nothing on Melaya's meter.
- Cloud models are billed by the provider to the user's key; `melaya_run_diagnosis` shows each run's token and dollar cost.
- A per-run ceiling `max_cost_usd` in the pipeline config stops runaway runs (cloud and runner). See `../../../modules/automation-governance/GUIDE.md`.

## Worked choices

| Request | Answer |
|---|---|
| "Use my Claude Max subscription for everything" | All agents `claude_code` / `sonnet`; runner set up; `claudeCodeAvailable: true`; warn about shared rate limits; any file inputs must be given as text or links in the brief |
| "Our style guide must never go to a cloud model" | `static_context_local_folder_path` + every agent `ollama`/`lmstudio` (CLI providers only if sending to Anthropic/OpenAI/GitHub is acceptable) |
| "Cheapest thing that works, runs every morning" | Cloud `qwen3.7-plus` on every agent, cloud run, schedule without the runner |
| "The memo writer is too shallow" | Keep the cheap model elsewhere; switch only the memo agent to a stronger model; re-validate |
| "Rank 2,000 leads" | `decide_batch_file`, not an LLM loop |
