# Runner and model failures: symptom -> meaning -> fix

Read the symptom from the tool result, `melaya_run_status`, `melaya_run_diagnosis` or what the user sees in the app. Say the "plain words" column to the user, then do the fix. Re-check with `melaya_setup_status` or `melaya_runner_status` after each fix.

## Runner

| Symptom | Plain words for the user | Fix |
|---|---|---|
| Run refused: "No local runner connected" (or a 503 when starting) | "This pipeline runs on your computer, and the Melaya helper is not running there right now." | `melaya_runner_status`; if `connected: false`, ask the user to start the runner again (same command if the token is still valid and was used before; otherwise `melaya_runner_setup`). Or switch every agent to a cloud model and remove `force_local_runner` if the pipeline does not really need the computer. |
| Runner never shows `connected: true` after start | "The helper did not manage to connect yet." | Ask what the window shows. Node missing or too old: install Node.js 18+. Python error: follow the install line the runner prints (Python 3.12 recommended), then start again. Token rejected or expired: `melaya_runner_setup` for a new command. "Too many runners": revoke an old one (`melaya_runner_revoke`). Company network blocking outbound websockets: try another network or ask IT to allow the Melaya app domain. |
| Runner was connected, now `connected: false` | "Your computer went to sleep or the helper window was closed." | Wake the computer, restart the runner. Advise disabling sleep during scheduled windows. |
| A run stopped halfway and is marked finished/orphaned | "The connection to your computer was lost during the run." | Runner disconnected for more than about 90 seconds. Keep the computer awake and on a stable connection, restart the runner, run again. |
| Scheduled fires show "skipped" | "Your computer was off at the scheduled time, so Melaya skipped instead of failing." | Expected with `requires_runner: true`. Move the schedule to when the computer is on, or move the pipeline to cloud models. |
| Triggered runs "skipped: runner or builder unavailable" / "trigger dropped" | "Your computer was offline" / "Your helper is an old version." | Start the runner; restart it to update (`@latest`). |
| A new feature "needs the runner updated" | "Your helper is an older version." | Restart the runner (the command always fetches the latest version). |
| Run refused with `local_runner_required` | "One of the tools must use your home internet connection, so it cannot run on Melaya's servers." | Set `force_local_runner: true` (Configure tab "Run Locally") and run with the runner on, or remove that tool. |
| Run refused with `run_input_files_cloud_only` | "Files can only be passed to pipelines that run on Melaya's cloud." | Put the content or a link in the text `brief`, or run a cloud-model copy of the pipeline for file tasks. |
| Save refused (409) mentioning static-context local folder and non-local agents | "Your local document folder is pasted into the prompts, so every agent must use a model on your computer." | Switch the listed agents (and the pipeline default) to `ollama`/`lmstudio`/`claude_code`/`codex`/`github_copilot`, or clear `static_context_local_folder_path`. |
| `melaya_agent_memory` returns `local: true` | "This pipeline keeps its memory on your computer." | Not an error. Memory cannot be listed from here. |
| Token may have leaked | "Let's switch off that key so nobody else can use it." | `melaya_runner_revoke` list, then revoke by `token_id`; user closes the running runner; mint a new one. |

## Subscription CLI providers

| Symptom | Plain words | Fix |
|---|---|---|
| `claudeCodeAvailable: false` on a connected runner | "Claude Code is not signed in on that computer." | User runs `claude` once in a terminal and signs in, then restarts the runner. |
| Run fails asking to run `claude` once to refresh | "Your Claude sign-in expired." | Open `claude` in a terminal (it refreshes), restart the runner, run again. |
| Many 429 / rate-limit errors on `claude_code`, `codex` or `github_copilot` | "Your subscription's usage limit is reached for now; it is shared with your other use of that plan." | Wait for the plan's window to reset; reduce parallel agents; move heavy agents to a cloud key (`anthropic`, `qwen`, ...). |
| No `codex` models in `melaya_runner_status` | "Codex is not signed in on that computer." | User runs `codex` and signs in with ChatGPT; restart the runner. |
| No `github_copilot` models | "Copilot is not signed in where the helper can see it." | `npx @melaya/runner@latest copilot login`, approve on GitHub, restart the runner. |
| Copilot Claude/Gemini model fails or is missing | "That model is switched off in your Copilot settings." | Enable it in the GitHub Copilot feature settings, or use `gpt-4.1`. |
| Copilot agent loses its instructions on long inputs | "Copilot limits how much that model can read." | Use `gpt-4.1` or a larger-cap model; avoid `gpt-4o-mini`. |
| `claude_code` wanted on a cloud run | "Claude Code subscriptions only work on your own computer." | Runner, or provider `anthropic` with an API key for the cloud. |
| Gemini agent fails on the runner at start ("cannot import name 'genai'") | "Your runner is older than the version that supports Gemini." | Update the runner (`npx @melaya/runner@latest`) and restart it; it rebuilds its Python environment once. |
| Gemini model returns 404 "no longer available to new users" | "Google retired that model for new keys." | Pick a current Gemini model (for example a Gemini 3 Flash or Pro model) in the model picker. |

## Local models

| Symptom | Plain words | Fix |
|---|---|---|
| "not pulled in Ollama. Run: ollama pull <name>" | "The model is not downloaded on your computer." | `ollama pull <name>`, restart the runner, run again. Check the name matches `ollama list` exactly. |
| LM Studio: asks to download the model | "The model is not downloaded in LM Studio." | Download it in the app, load it, restart the runner. |
| Run sits at "launching" for 1-2 minutes with LM Studio | "LM Studio is loading the model into memory." | Normal on first use; wait. Pre-load the model in LM Studio to avoid it. |
| Model missing from `melaya_runner_status` | "The helper did not see this model when it started." | Make sure Ollama is running / the LM Studio server is started with the model loaded, then restart the runner. |
| Agent forgets its task, truncated tool results, context-length errors | "The model can read only a limited amount of text at once." | Shorter inputs; tools that save large data to files and return summaries; LM Studio: reload with a larger context length; or a cloud model for that step. |
| Out-of-memory errors, then the run continues slower | "Your graphics card ran out of memory; Melaya reduced how much the model reads." | Close other GPU apps; pick a smaller model or accept the smaller window. |
| Agent loops, "thinks" forever, never calls a tool | "This model is too small to use tools reliably." | Use a 7B+ tool-calling model (`qwen3:8b`) or a cloud model. |
| Everything extremely slow | "Your computer is running the model without a graphics card." | Use a cloud model for validation; keep local models for private tasks; set expectations. |

## Cloud models

| Symptom | Plain words | Fix |
|---|---|---|
| `melaya_model_list` `status: "no_key"` | "Melaya has no key for this provider in your account." | User adds the key in Settings > Connectors (`melaya_connector_connect` shows where). Do not guess ids. |
| `status: "invalid_key"` / "rejected the key" | "The saved key is wrong, expired or revoked." | Replace it in Connectors. |
| "out of credits" | "Your balance with that provider is empty." | Top up with the provider, or switch provider. |
| "rate-limiting you" on a free tier | "The free tier's per-minute cap was reached; it resets by itself." | Wait, lower parallelism, or use a paid key. |
| "retired that model" / model not found at run start | "That model name no longer exists at the provider." | `melaya_model_list` (with `force_refresh: true`), pick a current id, re-save. |
| Run refused `tier_insufficient` for cloud providers | "Your plan does not include cloud AI providers." | Upgrade, or use Melaya AI, local models or a subscription CLI provider. |
| Melaya AI slow, "queued", or weak answers | "That is Melaya's free demo model; it is shared and small." | Use a cloud key or a subscription provider for real work. |
| Daily Melaya AI allowance reached | "Today's free allowance is used up." | Wait until tomorrow or connect another model. |
