# Subscription CLI providers: Claude Code, Codex, GitHub Copilot

These providers let agents use a chat subscription the user already pays for, instead of an API key. They run ONLY on the user's runner, because the sign-in lives on the user's computer and Melaya never receives it. Using any of them on any agent sends the whole pipeline to the runner.

Common facts:

- Free to Melaya's meter; the user's subscription pays.
- The runner must be connected when the run starts and stay connected until it ends.
- Model names come from `melaya_runner_status` (`models`, filtered by provider). `melaya_model_list` does not serve these providers.
- The runner detects sign-ins when it starts. After signing in, restart the runner.
- Rate limits are the subscription's limits, shared with everything else the user does with that subscription (including an AI assistant session driving Melaya on the same plan). Long or parallel pipelines hit them first.
- Run-input files are not available on runner runs (text brief works).

## Claude Code (`claude_code`)

**User setup, step by step**

1. Install Claude Code on the runner computer (from Anthropic's Claude Code page).
2. Open a terminal, type `claude`, press Enter, and sign in with the Claude account that has the Pro or Max plan. Close it when it says you are signed in.
3. Restart the runner (Ctrl+C in its window, run the command again).
4. `melaya_runner_status {}`: success is `claudeCodeAvailable: true` and names in `claudeCodeModels`.

**Model names**: the family aliases `sonnet`, `opus`, `haiku` always resolve to the newest model of that family available to the subscription. A full model id from `claudeCodeModels` pins one release. Prefer aliases unless the user wants a fixed release.

**Works**: full tool calling, human approvals, multi-agent steps, web search through the provider's own search.

**Does not work / watch out**

- Not in the cloud. Anthropic blocks server-side use of Claude Code sign-ins, so Melaya will never offer it there. For Claude on a cloud run, use provider `anthropic` with an API key.
- Sign-in expiry: Melaya only reads the sign-in; the Claude Code program refreshes it. If runs fail with a message asking to run `claude` once, the user opens `claude` in a terminal (it refreshes), then restarts the runner.
- `claudeCodeAvailable: false` on a connected runner means "not signed in on that computer", not "provider unavailable".
- No prompt caching in this path, so pipelines use the plan's allowance faster than the Claude Code app itself. For heavy or parallel pipelines prefer `anthropic` with a key or a cheaper cloud model.
- Output per call is capped (large but finite); ask agents to write long documents through document tools rather than one giant reply.

## Codex (`codex`)

**User setup**

1. Install the OpenAI Codex CLI on the runner computer.
2. Run `codex` in a terminal and sign in with the ChatGPT account (Plus/Pro or higher). An API-key sign-in in the CLI also counts.
3. Restart the runner. `melaya_runner_status {}` should list models with provider `codex`.

**Model names**: exactly as listed by the runner (the list follows the CLI's own model list, so new releases appear automatically).

**Works**: tool calling (validated end to end), multi-agent pipelines.

**Watch out**: same runner-only, same shared-quota, same restart-after-sign-in rules as Claude Code.

## GitHub Copilot (`github_copilot`)

**User setup** (one of):

- Already signed in to GitHub Copilot in an editor plugin that stores its sign-in in the usual place (Vim/Neovim plugin, JetBrains). Nothing more to do.
- Otherwise: in a terminal on the runner computer run `npx @melaya/runner@latest copilot login`, open the GitHub page it shows, enter the code, approve. The sign-in is saved for the runner.

The standalone GitHub Copilot command-line app's sign-in cannot be read; use one of the two options above.

Then restart the runner and check `melaya_runner_status` for models with provider `github_copilot`.

**Model names and context caps**: Copilot limits how much each model may read, below the model's own size:

| Model | Approximate input cap on Copilot |
|---|---|
| `gpt-4o-mini` | 12k tokens (too small for agents) |
| `gpt-4o` | 64k |
| `gpt-4.1` (default, recommended) | 128k |
| Claude models on Copilot | 200k |
| newest GPT-5.x | about 272k |

Claude, Gemini and the newest GPT models are switched off on Copilot by default; the user enables them in their GitHub account's Copilot feature settings. Until then they do not appear or fail.

**Does not work**: embeddings (so Copilot cannot be the retrieval embedder). Everything else as the other CLI providers.

## Mixed pipelines

A pipeline may mix a CLI provider on one agent with a cloud key on another. It still runs on the runner; the cloud calls leave from the user's computer with the user's saved key.

## Static context folders

`claude_code`, `codex` and `github_copilot` count as local providers for the local static-context folder rule. Tell the user honestly: the files stay off Melaya's servers, but they are sent to Anthropic / OpenAI / GitHub as part of the prompt, like any conversation with that subscription. If the files must not leave the computer at all, use Ollama or LM Studio.
