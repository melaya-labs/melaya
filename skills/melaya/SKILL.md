---
name: melaya
description: Use for anything done on the Melaya platform through its MCP server (tools named melaya_*). Covers running, building, fixing and handing over Melaya pipelines and complete multi-pipeline agentic systems; connecting services (Google, Slack, CRMs, API keys); the local runner, Claude Code, Codex, Copilot, Ollama and LM Studio models; projects and templates; run inputs (brief and files); schedules, event triggers and approvals; data stores in Google Sheets and bulk scoring; validation, debugging and reading results; client documentation; phone and browser agents. Works for non-technical users (plain-language journeys) and for integrators (full end-to-end method). Load this first, then open only the module the task needs.
version: 1.1.0
---

# Melaya

Melaya runs AI agent pipelines for a user: agents that research, read documents, write designed reports, update spreadsheets, send email, and operate phones or browsers. You drive it through the Melaya MCP tools (`melaya_*`). This file is a router: it holds the rules that always apply and tells you which module to open. Open a module only when the task needs it.

## Rules that always apply

1. Start every session with `melaya_setup_status` and act on its gaps before anything else.
2. Never ask for, repeat or store passwords or API keys in chat. Services are connected by the user in the app (`melaya_connector_connect` gives the link).
3. Never approve, reject or edit an approval on the user's behalf. Show what is waiting (`melaya_approval_list`) and point them to the app or their phone.
4. `melaya_pipeline_save` in update mode replaces the WHOLE config: always `melaya_pipeline_get` first, change the copy, `melaya_pipeline_preview`, save, then read it back.
5. Judge a run by `outcome`, never by `status`, and verify real artifacts (the sheet rows, the document, the email) before you say it worked.
6. Do not invent tool names or parameters: load the schema (tool search) or check `melaya_pipeline_registry` / `melaya_connector_tools`.
7. Confirm before anything irreversible or outward-facing (sending, posting, deleting, paying). Connector writes over MCP (`melaya_connector_call`) need the user's `melaya:connectors.write` permission and run at once, with no approval card: confirm the exact send or change first. Anything that moves money or trades is never run over MCP; it stays an approval in the Melaya app.
8. If a capability is missing, the user did not grant it or has not connected it: say so and offer the fix. Never look for a workaround.

## Who are you helping?

| The user | Start with |
|---|---|
| Non-technical: wants to run, watch, approve, schedule or tweak existing pipelines | `modules/quickstart/GUIDE.md` |
| Integrator or builder: wants a complete multi-pipeline system for a client, from requirements to handover | `modules/agentic-systems/GUIDE.md` (the end-to-end method; it routes to the others by phase) |
| Anyone with one specific task | the intent table below |

If you cannot tell, ask one question: "Do you want to run something that exists, or build something new?"

## Intent table

| The task is about | Open |
|---|---|
| What Melaya is, first setup, running a template, run with a brief and files, watching a run, approving, scheduling, sharing | `modules/quickstart/GUIDE.md` |
| Building or replicating a whole agentic system for a client, phase by phase | `modules/agentic-systems/GUIDE.md` |
| Which tools and connectors exist, connecting a service, a connection that expired, which account is used, a service with no connector | `modules/discovery/GUIDE.md` |
| Where a pipeline runs (cloud or the user's computer), installing the runner, Claude Code / Codex / Copilot, Ollama / LM Studio, choosing and costing models | `modules/runners-models/GUIDE.md` |
| Projects (create, rename, delete over MCP), templates, the template gallery, canonical names, sharing with a team | `modules/projects-templates/GUIDE.md` |
| Writing or changing a pipeline config: step kinds, agents, instructions, approvals, run inputs, memory, quality loop, designed and branded documents, email steps | `modules/pipeline-authoring/GUIDE.md` |
| A shared Google Sheet as the system's database, adding and updating rows, dedupe, scoring or triaging many items | `modules/data-spine/GUIDE.md` |
| Schedules, event triggers (webhooks, app events, streams, polls), approval modes, memory policy, cost and security | `modules/automation-governance/GUIDE.md` |
| Testing a pipeline, a failed or slow run, reading diagnoses and evals, checking outputs, known failure patterns | `modules/validate-debug/GUIDE.md` |
| Documenting a system for a client or a non-technical owner, handover | `modules/client-handover/GUIDE.md` |
| Operating an Android phone or a browser, device steps in pipelines, the Melaya Assistant | `modules/devices-browser/GUIDE.md` |

Each module starts with a "Use when" line, then its method; deeper material sits in its `references/` (and `templates/`) folder. Read those only when the module points you there.

## Typical flows

- Run something for a user: quickstart, then validate-debug if the run fails.
- Build one pipeline: discovery, pipeline-authoring (plus data-spine if it stores records), validate-debug, automation-governance to schedule it.
- Build a full client system: agentic-systems drives every phase and names the module for each.
- Phone or browser work: devices-browser (plus runners-models when a browser runs on the user's computer).

## Adding a module

New capability areas (for example dedicated mobile-agent or browser-agent playbooks) are added as `modules/<area>/GUIDE.md` with a first line `> Use when: ...`, plus one row in the intent table above. Keep module files in plain ASCII, describe behaviour (never platform internals), and link other modules by relative path.

## Changelog

- 1.1.0: MCP 1.2.0: connector writes with the `melaya:connectors.write` permission (money-moving refused at every level), project tools with `melaya:projects` (a client pilot runs end to end over MCP); click-by-click triggers walkthrough and trigger fixes (secret shown once, no account or action picker on instant triggers, concurrency skips, case-sensitive prefilter); pilot lessons: Drive folders, write-back by column name, composite scores by tool, provenance enforced in code, data health check pattern, designed-document blocks (KPI tiles, charts, status columns, PDF export), deck intake, research quality, UAE regulator checks, model location.
- 1.0.1: native web search fallbacks (Qwen Token Plan, Groq), Engine header, Gemini runner and retired-model failures.
- 1.0.0: first packaged release: 11 modules (quickstart, agentic-systems, discovery, runners-models, projects-templates, pipeline-authoring, data-spine, automation-governance, validate-debug, client-handover, devices-browser).
