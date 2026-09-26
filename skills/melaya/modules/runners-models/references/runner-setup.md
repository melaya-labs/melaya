# The Melaya runner: set up, check, update, revoke

The runner is a small program (`@melaya/runner`, started with `npx`) that the user runs on their own computer. It connects OUT to Melaya over a secure websocket, receives pipeline runs for this account, runs them on the computer, and streams every message, tool call and cost back to the Melaya app so the run looks the same as a cloud run to everyone on the team.

It opens no ports and works behind home routers and company firewalls.

## What the user needs

| Need | Plain words |
|---|---|
| A desktop or laptop | A phone cannot host the runner |
| Node.js 18 or newer | From nodejs.org. Provides the `npx` command |
| Python | The runner uses an installed Python 3.10 to 3.12 if it finds one. If the computer only has a newer or no Python, recent runner versions download their own. If that fails, the runner prints the one-line install command for the user's system |
| The computer awake during runs | A sleeping computer drops the connection |
| Optional: the model software | Claude Code / Codex CLI signed in, Copilot signed in, Ollama or LM Studio running with a model |

## Setup script (what to do, what to say)

1. **Explain and ask.** "Your pipeline uses <Claude Code / a local model / your home connection>, so it has to run on your computer through a small Melaya helper called the runner. It stays open in a terminal window and only talks to Melaya. May I set it up?" Wait for yes.
2. **Check first.** `melaya_runner_status {}`. If `connected: true`, say "Your runner is already running" and skip to step 7.
3. **Mint once.** `melaya_runner_setup { "label": "<e.g. Anna laptop>" }`. Optional `expiry_days` (1-365, default 7; a connected runner keeps working after the token expires only while it stays connected, so choose a longer expiry for a machine that restarts often and that the user controls). Keep the returned `command` in memory only.
4. **Start it.**
   - With a shell on the user's own computer: run the command as a background process. Do not print it back.
   - Without one: "Please open PowerShell (Windows) or Terminal (Mac or Linux) on the computer that should run your pipelines, paste the line below, press Enter, and leave the window open." Give the command once. Do not say it is running until status says so.
5. **Wait.** First start takes 20-60 seconds (it builds its own Python environment). The window shows the detected models and "connected".
6. **Confirm.** Poll `melaya_runner_status {}` every 15-20 seconds, for up to about 2 minutes. Success: `connected: true`.
7. **Check models.** `models` lists `{provider, name}` for everything found at start-up. Compare with the providers/models the pipeline uses. Missing one: fix it on the computer (see `subscription-cli-providers.md` or `local-models.md`), then ask the user to stop the runner (Ctrl+C in its window) and start the same command again. Detection only happens at start-up.
8. **Tell the user the ground rules.** "Keep that window open and the computer awake when your pipelines should run. If you close it, runs that need it will not start. To stop it, press Ctrl+C in its window."

## Updating

The command uses `@latest`, so restarting the runner (Ctrl+C, run the same command again) picks up the newest version. Do this when a feature or fix "needs the runner updated", or when triggered runs are dropped by an old runner.

If the user has lost the command and the token has expired or was never connected, call `melaya_runner_setup` again (it only mints when no runner is connected).

## Several computers

Up to 3 runners per account. A fourth connection is refused. Revoke tokens of computers no longer used.

## Revoking

1. `melaya_runner_revoke {}` -> list of tokens with id, label, expiry, last seen, revoked.
2. Pick with the user; `melaya_runner_revoke { "token_id": "<id>" }`.
3. Revocation blocks new connections only. For an immediate stop, the user closes the runner window on that computer.

Revoke when: the command was pasted anywhere shared (chat with others, ticket, document, repository), a laptop is lost or retired, or the user asks. When in doubt, revoke; a new token is one call away.

## Security, in plain words (for the user or their IT team)

- Outbound connection only; no inbound ports, no IP address exposed.
- The runner token can only run this account's pipelines. It cannot read the account's other credentials or admin settings.
- Every pipeline sent to the runner is signed; the runner checks the signature before running it.
- Only the credentials a given pipeline needs are sent with that run.
- A run started by an outside event (webhook, trigger) has sensitive account keys removed from its environment and is forced into safe human-approval mode.
- The runner runs pipelines with the permissions of the user account that started it, without the sandbox used in the cloud. For stronger separation, run it under a dedicated user account on the computer.
- Sign-ins for Claude Code, Codex and Copilot are read on the computer at run time and never sent to Melaya's servers.

## Where the user sees it in the app

Settings > Connectors shows the runner connection and detected models, and lets the user create or revoke runner tokens. The pipeline's Configure tab has the "Run Locally" switch (`force_local_runner`).
