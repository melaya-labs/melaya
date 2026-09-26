<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when deciding WHERE a Melaya pipeline runs and WHICH model each agent thinks with - Melaya cloud vs the user's own computer (the Melaya runner), installing and pairing the runner (melaya_runner_setup), checking it (melaya_runner_status), cutting it off (melaya_runner_revoke), reading the runner and claude_code gaps of melaya_setup_status, subscription CLI providers that only run on the runner (Claude Code, Codex, GitHub Copilot), local models (Ollama, LM Studio: install, pull or load, context limits, speed, tool-calling picks), cloud providers with the user's own keys (melaya_model_list statuses), the free Melaya AI demo model, per-agent model choice (cost vs quality vs speed, a cheap fast validation model), force_local_runner, local static-context folders (every agent must then be local), what needs the cloud vs the runner, and plain-words fixes for "no runner", "runner offline", "model not loaded", "context too small", "login expired". Triggers - "run it on my machine", "use my Claude subscription", "use Ollama", "which model should I pick", "set up the runner", "runner not connected", "model not found".

# Melaya runners and models

Every Melaya pipeline answers two questions before it does any work:

1. **Where does it run?** On Melaya's cloud, or on the user's own computer through the Melaya runner.
2. **What does each agent think with?** A model: a cloud provider the user brings a key for, a subscription the user is already signed in to on their computer (Claude Code, Codex, GitHub Copilot), a model running on their own computer (Ollama, LM Studio), or Melaya's own free demo model.

The two answers are linked: some models only exist on the user's computer, so choosing one moves the whole pipeline there. This module explains both, in words a non-technical user can follow, and gives the exact MCP calls to set up, check and fix each part.

References (load when you reach that part):

| File | Load it for |
|---|---|
| `references/runner-setup.md` | Installing, pairing, checking, updating, revoking the runner; security in plain words; step-by-step script to read to the user |
| `references/subscription-cli-providers.md` | Claude Code, Codex, GitHub Copilot: how the user signs in, which models appear, what works and what does not |
| `references/local-models.md` | Ollama and LM Studio: install, pull or load a model, context size, speed, which models can use tools |
| `references/choosing-models.md` | Cloud providers and bring-your-own keys, Melaya AI, System One decide tools, a per-agent choice table, validation model, cost |
| `references/failures.md` | Symptom -> what it means -> what to do, for every runner and model failure, in plain words |

Other modules: `../../modules/discovery/GUIDE.md` (the first inventory), `../../modules/pipeline-authoring/GUIDE.md` (where the model fields go in a config), `../../modules/validate-debug/GUIDE.md` (reading failed runs), `../../modules/automation-governance/GUIDE.md` (schedules and triggers for runner pipelines).

## 1. Plain-words glossary (say these to the user when needed)

| Word | Plain meaning |
|---|---|
| Cloud run | The pipeline runs on Melaya's servers. The user's computer can be off. |
| Runner | A small program the user starts on their own computer. It keeps a connection open to Melaya and runs pipelines there when asked. It opens no ports on the computer; it only calls out to Melaya. |
| Pairing | Starting the runner with a one-time token so Melaya knows the runner belongs to this account. |
| Provider | The company or program that serves a model (OpenAI, Qwen, Anthropic, Ollama, Claude Code, ...). In a config it is `model_provider`. |
| Model id | The exact name of one model at that provider (`qwen3.7-plus`, `qwen3:8b`). In a config it is `model_name`. |
| API key / BYOK | "Bring your own key": the user pastes a key from a provider into Melaya Connectors; that provider bills the user directly. |
| Subscription CLI provider | A paid chat subscription the user is already signed in to on their computer (Claude Code, Codex, GitHub Copilot). Melaya uses it only through the runner. |
| Local model | A model file running on the user's own computer through Ollama or LM Studio. Free, private, slower. |
| Context window | How much text a model can read at once. Too small and the agent forgets its instructions or tool results. |
| Tool calling | A model's ability to use tools (search, Sheets, email). Pipelines need it; very small models do it badly. |

## 2. Where a pipeline runs: the rule

A run goes to the **user's runner** when ANY of these is true. Otherwise it runs in the **cloud**.

| Trigger | Why |
|---|---|
| Any agent (or the pipeline default) uses `ollama` or `lmstudio` | The model only exists on the user's computer |
| Any agent uses `claude_code`, `codex` or `github_copilot` | The sign-in lives on the user's computer and is never sent to Melaya |
| The config sets `"force_local_runner": true` (the "Run Locally" switch on the pipeline's Configure tab) | The user asked for it, e.g. to use their home internet connection |
| The pipeline uses a local browser (Melaya Browser on the user's machine) | The browser is on the user's computer |
| The pipeline uses retrieval with a local embedder (`rag_embedder_provider` `ollama`/`lmstudio`) | The embedder is on the user's computer |

One local agent moves the WHOLE pipeline to the runner. On the runner, cloud-model agents still work: their keys travel with the run and the calls go from the user's computer straight to the provider. So a mixed pipeline (one Ollama agent, one Qwen agent) is fine, but it needs the runner online.

Some tools only work from a home internet connection (tools that reuse the user's own logged-in session on sites such as LinkedIn or Luma). A pipeline with such a tool is refused in the cloud with an error mentioning `local_runner_required`. Fix: set `force_local_runner: true` (or use only local models).

### What needs which side

| Feature | Cloud run | Runner run |
|---|---|---|
| Cloud models with the user's keys | Yes | Yes (calls leave from the user's computer) |
| Melaya AI demo model | Yes | Yes |
| Claude Code / Codex / GitHub Copilot | No | Yes |
| Ollama / LM Studio on the user's computer | No (unless a server URL is saved, section 6) | Yes |
| Run-input FILES (`files` or file inputs on `melaya_pipeline_run`) | Yes | **No**: refused with `run_input_files_cloud_only`. The text `brief` and text inputs do work |
| Local static-context folder | No | Yes, and every agent must be local (section 7) |
| Tools that need the user's home connection or logged-in sessions | No | Yes |
| Runs while the user's computer is off or asleep | Yes | No: the run fails or, for schedules with `requires_runner: true`, is skipped |
| Crew memory readable by `melaya_agent_memory` | Yes | No: kept on the user's computer, reported as `local: true` |
| Isolation | Each run in a sandbox | Runs as the user's own account on their computer, no sandbox |
| Cost to Melaya's meter for the model | Provider bills the user's key | Local and subscription models are free to Melaya's meter |

Decision in one line: **use the cloud unless** the user wants their subscription, a private local model, local files that must not leave the machine, or a tool that needs their home connection.

## 3. First call, every time: read the setup state

```
melaya_setup_status {}
```

Read `runner` and the two runner-related `steps`:

| `steps[].id` | `done: false` means | What to do |
|---|---|---|
| `runner` | No runner is connected for this account right now | Only needed if the pipeline must run on the runner (section 2). If yes, run the setup in section 4. If the system is cloud-only, record "runner: not required" and move on. |
| `claude_code` | The runner (if any) did not find a signed-in Claude Code on that computer | Only matters if an agent will use `claude_code`. Ask the user to open a terminal, run `claude` once and sign in, then restart the runner. If nobody uses Claude Code, accept the gap. |

`runner.models` lists every model the runner detected, each `{provider, name}` (providers `ollama`, `lmstudio`, `claude_code`, `codex`, `github_copilot`). `claudeCodeAvailable` and `claudeCodeModels` repeat the Claude Code part. The other steps (phone, allowed apps) belong to the phone skills.

`ready: false` is normal for an account that does not use a phone. Judge only the steps your pipeline needs.

## 4. Set up the runner (summary; full script in `references/runner-setup.md`)

Before you start, tell the user in one sentence what it is and ask permission:

> "To use <your Claude subscription / Ollama / ...> Melaya needs a small helper program running on your computer. It connects out to Melaya, opens no ports, and runs the pipeline on your machine. It keeps running in the background until you close it. Shall I set it up?"

Then:

1. `melaya_runner_status {}`. If `connected: true`, stop: it is already running. Go to step 5.
2. `melaya_runner_setup { "label": "<computer name>" }` once. Optional `expiry_days` (1-365, default 7). If a runner is already connected it returns `alreadyConnected: true` and mints nothing.
3. The result has `command` (`npx @melaya/runner@latest --token=...`), `requirements` (Node.js 18 or newer, Python), and `runsOn`.
   - **You have a shell on the user's own computer** (Claude Code on their laptop): after the user agrees, run the command as a background process in their terminal.
   - **You do not** (claude.ai, mobile, any hosted surface): give the command to the user, say "paste this into PowerShell (Windows) or Terminal (Mac/Linux) on the computer that should run your pipelines, and leave that window open". Never say you started it.
   - Either way: the command contains a live credential. Never write it to a file, a doc, a commit or a later message.
4. Wait 20-60 seconds (first start builds its own Python environment), then poll `melaya_runner_status {}` until `connected: true`.
5. Check `models`. It lists what the runner found at start-up. If a model the pipeline needs is missing, fix it on the computer (sign in, pull or load the model) and **restart the runner**: detection happens only when it starts.

Success looks like: `connected: true`, and the provider/model the pipeline needs appears in `models`.

When it fails, see `references/failures.md` ("runner never connects").

## 5. Subscription CLI providers (summary; detail in `references/subscription-cli-providers.md`)

| Provider id | User pays | How the user signs in on the runner computer | Model names to use |
|---|---|---|---|
| `claude_code` | Their Claude Pro/Max plan | Install Claude Code, run `claude` once in a terminal, sign in | Family aliases `sonnet`, `opus`, `haiku` (resolve to the newest of that family) or a full id listed in `claudeCodeModels` |
| `codex` | Their ChatGPT plan | Install the Codex CLI, run `codex` and sign in with ChatGPT | An id listed by `melaya_runner_status` under provider `codex` |
| `github_copilot` | Their GitHub Copilot plan | Signed in to Copilot in an editor plugin, or run `npx @melaya/runner@latest copilot login` and follow the code shown | `gpt-4.1` is the safe default; others as listed by the runner |

Facts to tell the user plainly:

- These run **only** on the runner. Melaya's cloud cannot use these sign-ins (for Claude Code, Anthropic blocks it). For Claude in the cloud, use the `anthropic` provider with an API key instead.
- The runner reads the sign-in live at run time; Melaya stores nothing. If the sign-in expires, open the CLI once (for example run `claude`) so it refreshes, then restart the runner.
- Usage comes out of the same subscription the user uses elsewhere. A long pipeline and the user's own chats share one quota; heavy or parallel pipelines hit rate limits sooner. Claude Code in pipelines does not benefit from prompt caching, so it drains quota faster than the CLI itself.
- They are free to Melaya's meter.
- GitHub Copilot caps context per model below the model's native size (gpt-4o-mini about 12k, gpt-4o about 64k, gpt-4.1 about 128k). Pick `gpt-4.1` or larger for agents. Claude and Gemini models on Copilot stay off until the user enables them in their GitHub Copilot settings.
- `melaya_model_list` does not serve these providers. Model names come from `melaya_runner_status`.

## 6. Local models (summary; detail in `references/local-models.md`)

| | Ollama (`ollama`) | LM Studio (`lmstudio`) |
|---|---|---|
| Install | ollama.com, run the installer; it runs in the background | lmstudio.ai, install, then start its local server (Developer tab) |
| Get a model | `ollama pull qwen3:8b` in a terminal | Search and download in the app |
| Name in config | Exactly as `ollama list` shows (`qwen3:8b`) | Exactly as `melaya_runner_status` lists it |
| If the model is missing at run time | Run fails fast: "not pulled in Ollama. Run: ollama pull <name>" | Downloaded but not loaded: the runner loads it first (can take 1-2 minutes). Not downloaded: fails fast asking to download it |
| Context | Melaya asks Ollama for the model's window, capped at about 16k tokens; it halves automatically if the computer runs out of memory | Set when the model is loaded in LM Studio (context length); load with at least 16k for pipelines |

Good tool-calling picks for a laptop with a graphics card: `qwen3:8b` (about 6.5 GB of graphics memory, the default in Melaya templates), `qwen2.5:7b`, `qwen3:14b` only with 12 GB or more. Models under about 7 billion parameters are unreliable at multi-step tool use; under about 4 billion are text-only in practice.

Speed: an 8B model on a decent graphics card writes roughly 35-45 tokens per second; on a computer without one it can take minutes per agent turn. Tell the user a local pipeline can take several times longer than the same pipeline on a cloud model.

A company server running Ollama or LM Studio, reachable on the internet, can be saved as a server URL on the Connectors page; then the pipeline runs in the cloud and calls that server, no runner needed. For other self-hosted OpenAI-compatible servers use the `openai_compatible` or `litellm` provider with its base URL.

## 7. Local static-context folders: the all-local rule

Static context = reference documents (style rules, policies, past examples) pasted into the agents' prompts on every run. Normally the user uploads them. A pipeline can instead point at a folder on the user's computer with `static_context_local_folder_path`; then Melaya never stores those files, and the runner reads the folder at run time.

Rule (enforced at save, the save is refused with a 409 listing the offending agents): when `static_context_local_folder_path` is set, the pipeline default and **every agent** must use a local provider: `ollama`, `lmstudio`, `claude_code`, `codex` or `github_copilot`. Reason, to tell the user: the documents are pasted into the prompt, so a cloud model would receive the whole files.

Notes: plain-text formats only in static mode (`.txt`, `.md`, `.csv`, `.json`); about 50,000 characters per file and 60,000 in total are used. Larger collections belong in retrieval mode. A local folder for retrieval (`rag_local_folder_path`) is different: it needs a local embedder (`ollama` or `lmstudio`) but chat agents may stay cloud, because they only see retrieved excerpts.

## 8. Cloud providers and keys (summary; detail in `references/choosing-models.md`)

1. Pick the provider. Call `melaya_model_list { "provider": "<id>" }` BEFORE writing any model id into a config.
2. Read `status`:

| `status` | Meaning | Say / do |
|---|---|---|
| `ok` | Key works; `models` is the live list, newest first | Copy an id exactly |
| `no_key` | No key saved for this provider | "Please add your <provider> API key in Melaya: Settings > Connectors > <provider>." Use `melaya_connector_connect { "service": "<provider>" }` for the exact place. Never ask for the key in chat. Do not guess ids meanwhile. |
| `invalid_key` | The saved key was rejected | Ask the user to replace the key in Connectors |
| `error` | Temporary | Retry, optionally with `force_refresh: true` |

3. Plan limits: on the free Sandbox plan, cloud AI providers are locked (a run is refused as `tier_insufficient`); local models, subscription CLI providers and Melaya AI still work.

## 9. Melaya AI (in-house)

`melaya_ai` / `melaya-demo-2b` (confirm with `melaya_model_list { "provider": "melaya_ai" }`). No key, free on every plan, limited by a daily token allowance, requests wait in a shared queue. It is a small demonstration model: fine to show the platform working with zero setup, too weak and too slow for real multi-step pipelines. Do not use it for validation runs. Tell the user to connect a frontier model (a cloud key, or Claude Code / Codex on the runner) for real work.

Separate from chat models: the System One `decide_*` tools (`decide`, `decide_check`, `decide_batch`, `decide_batch_file`) and the `decide` step score or classify many items with no text generation. They are free, always available, and work from cloud and runner runs. The paid hosted twin is the `jev_*` tools. Use them instead of an LLM for triage (see `../../modules/data-spine/GUIDE.md`).

## 10. Choosing a model per agent

Set `model_provider` + `model_name` on each agent (both, or the pipeline default applies). See `../../modules/pipeline-authoring/GUIDE.md` for the exact place.

| Situation | Choice |
|---|---|
| Validation runs (proving a pipeline works) | A cheap, fast cloud model with reliable tool calling, e.g. `qwen` / `qwen3.7-plus`. Never Melaya AI, never a CPU-only local model |
| Most production steps | The same cheap fast model if validation passed |
| One agent whose writing or reasoning quality is not good enough | A stronger model on THAT agent only (a larger `qwen`, `anthropic`, `openai`, or `claude_code` if the user wants their subscription) |
| User wants zero API spend and has a Claude/ChatGPT/Copilot plan | `claude_code` / `codex` / `github_copilot` on the runner; accept that the runner must be on and the plan's rate limits apply |
| Data must not leave the computer | Every agent local (`ollama`/`lmstudio`, or a CLI provider if the user accepts that the CLI vendor sees the prompts) + local folders |
| Scoring or filtering many items | Not an LLM: `decide_batch_file` or a `decide` step |
| Very large inputs | A model with a large window (check the provider's documentation) or reduce the input with file-based tools |

Before saving: every id came from `melaya_model_list` (`status: ok`) or `melaya_runner_status` `models`. A guessed id saves without complaint and fails only when the run starts.

## 11. Running and checking a runner pipeline

1. `melaya_runner_status {}` -> `connected: true` and the needed models listed. If not, section 4 or `references/failures.md`.
2. `melaya_pipeline_run { "pipeline": "<name>", "project": "<project>", "brief": "..." }`. Do not pass `files` to a runner pipeline; put short text in `brief`, or run a cloud copy for file-based tasks.
3. Poll `melaya_run_status`, inspect with `melaya_run_inspect` / `melaya_run_diagnosis` (see `../../modules/validate-debug/GUIDE.md`). The run streams to the app exactly like a cloud run.
4. Keep the computer awake and the runner window open until the run finishes. If the runner disconnects for more than about 90 seconds, the run is closed as orphaned.
5. Schedules for runner pipelines: `melaya_pipeline_schedule { "action": "set", ..., "requires_runner": true }` so fires are skipped (not failed) while the computer is off. Event-triggered runs are skipped the same way when the runner is offline.

## 12. Cutting a runner off

When a token may have leaked (pasted in a shared place), a computer is retired, or the user asks:

1. `melaya_runner_revoke {}` lists tokens (id, label, expiry, last seen, revoked). Nothing is revoked yet.
2. Confirm with the user which one, then `melaya_runner_revoke { "token_id": "<id exactly as listed>" }`.
3. Tell the user: revoking blocks any NEW connection, but a runner already connected keeps working until it stops. Ask them to close the runner window (or stop the process) on that computer for an immediate cutoff.
4. A new token is one `melaya_runner_setup` call away.

## 13. Hard rules

- Ask before starting a runner; it is a long-lived process on the user's computer.
- Call `melaya_runner_setup` once and reuse the result; minting again invalidates a token that never connected. At most 3 runners per account.
- Never print, store or repeat the runner command after handing it over.
- Never ask the user to paste an API key or token in chat; keys go into Connectors.
- Never write a model id you did not read from `melaya_model_list` or `melaya_runner_status`.
- Never claim a runner pipeline "runs automatically" without saying the computer must be on with the runner running.
- If a pipeline needs run-input files, it must run in the cloud.
- If a local static-context folder is used, every agent must be local; otherwise drop the folder.

## 14. Checklist

- [ ] Execution side decided and written down (cloud / runner) with the reason from section 2.
- [ ] Runner pipelines: `melaya_runner_status` `connected: true`, every needed provider/model in `models`.
- [ ] Claude Code agents: `claudeCodeAvailable: true`.
- [ ] Cloud agents: `melaya_model_list` `status: ok` for each provider; ids copied exactly.
- [ ] Validation model is a cheap fast cloud model; stronger models only where quality demands.
- [ ] No run-input files on runner pipelines; no cloud agent next to a local static-context folder.
- [ ] Runner schedules set with `requires_runner: true`; user told the computer must be on.
- [ ] User knows how to revoke the runner and how to restart it after signing in or adding a model.
