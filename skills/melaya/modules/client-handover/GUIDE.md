<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when a Melaya agentic system (a set of pipelines built for one client) is validated and must be documented and handed over to the client, or when that client documentation must be refreshed after a config change. Produces a non-technical, ELI5, source-cited documentation set (hub, how it works, run inputs, tools catalogue, data model and provenance, branded documents, security and human control, one 14-section note per pipeline) with mermaid diagrams, a requirement traceability matrix, pilot vs production sections and honest known limits, generated from the live pipeline configs. Triggers on "document the system for the client", "handover docs", "write the pipeline notes", "client documentation", "update the docs after the config change".

# Melaya client handover

Turn a validated set of Melaya pipelines into a documentation set that a non-technical decision maker can read, trust and repeat to a colleague. The docs are a projection of the configs: every claim must be traceable to a pipeline config, a tool schema, a validated run or a named public source.

## When to use

- The system has passed validation (every pipeline had a real, inspected run). Documenting an unvalidated pipeline is allowed only if its example section says "illustrative" and its status is honest.
- A pipeline config changed (new step, tool, schedule, HITL gate, output). Re-run the sync procedure below.
- Do NOT use for internal engineering notes, runbooks or code docs. This set is for the client.

## Audience and voice

| Reader | What they need | How to serve them |
|---|---|---|
| Decision maker (partner, director, sponsor) | What it does, what it costs them in effort, what can go wrong | One-sentence callouts, overview table, known limits |
| Operator (analyst, manager who presses Run) | How to run it, what to write in the brief, where outputs land | Run inputs note, per-pipeline "when to run" and "inputs" tables |
| Reviewer (compliance, risk, IT) | Where each number comes from, what leaves the workspace, who approves | Provenance note, security note, human control sections |
| Client engineer | API and MCP surface | One short table in the run inputs note, technical appendix per pipeline |

Writing rules (apply to every file):
- ELI5 first, precision second. Every technical term gets a job-title or everyday analogy the first time ("an agent is a junior analyst with a job description and a fixed list of databases").
- Short sentences. Concrete numbers (counts of tools, columns, steps, limits) taken from the configs, never rounded into vagueness.
- Plain ASCII punctuation. No em or en dashes, no smart quotes, no emoji.
- Quote client requirements word for word in quotes, then map them. Never paraphrase a requirement into something easier to cover.
- Never name internal people, secrets, ids of client files, server addresses, internal repo paths. Canonical pipeline names and public tool names are fine.
- The model is never presented as the source of a fact. Tools and documents are sources; the model reads, chooses and writes.

## EEAT rules (experience, expertise, authoritativeness, trust)

1. Show sources. Every tool row names the public source or system behind it. Every example value carries its source (URL, registry id, document page, "computed: formula").
2. Show provenance. Explain the tag scheme the system uses (for example SOURCE / INFERRED / MISSING) with one example per tag, and where a reader finds the tag in each output.
3. Separate code from model. Keep a table "What code does instead of the model" (normalisation, dedupe, ranking, statistics, scoring, verdicts). Flag in a warning callout any place where the model still computes something.
4. Show what a human still validates. Every pipeline note and the security note end with a numbered list of human checks.
5. Real examples beat invented ones. Use validated run output where it exists (anonymize third parties when needed). Otherwise label the block `> [!example] Illustrative, not a real <entity>`.
6. Honest known limits. Each source and each pipeline lists its limits and how the system handles them ("a failed source is MISSING, never clear").

## The documentation set

Copy `templates/` into the client folder (for example `<Vault>/<Client>/`) and fill it. File order is the reading order.

| File | Template | Purpose |
|---|---|---|
| `00 Overview.md` | `templates/00-overview.md` | Hub: one-paragraph summary, glossary with analogies, lifecycle diagram, requirement traceability matrix, pipelines at a glance, out of scope, index |
| `01 How a Pipeline Works (ELI5).md` | `templates/01-how-it-works.md` | Anatomy of one run (sequence diagram), parallel steps, how an agent picks a tool, source reliability order, model does / does not, quality signal |
| `02 Run Inputs, Schedules and Triggers.md` | `templates/02-run-inputs-schedules-triggers.md` | Run now vs Run with inputs, brief, files, API and MCP, schedules, triggers, good briefs per pipeline |
| `03 Tools Catalogue.md` | `templates/03-tools-catalogue.md` | Every tool used, grouped, with ELI5 description, source, cost class, used by; known limits of sources |
| `04 <Data Store> and Provenance.md` | `templates/04-data-model-and-provenance.md` | The system of record: location, schema, how a row gets in, normalisation, dedupe, provenance tags, status lifecycle, who writes what |
| `05 Branded Documents.md` | `templates/05-branded-documents.md` | Every produced document, anatomy, default brand, re-branding via theme keys |
| `06 Security, Human Control and Compliance.md` | `templates/06-security-human-control.md` | What runs alone vs waits for approval (pilot and production), least privilege, untrusted files, data boundaries, compliance behaviours, human checks |
| `P<n> <Pipeline>.md` (one per pipeline) | `templates/pipeline-note.md` | The 14-section per-pipeline note |
| `Pipeline Template.md` | `templates/pipeline-note.md` (the header part) | Ship the template itself so the client can see the structure is uniform |

Only create a file whose subject exists in the configs. No data store in the system means no `04` note (or a short note saying where outputs live instead). No branded output means no `05`.

## Procedure

### 1. Gather facts from the live system (never from memory)

| Fact | Where it comes from |
|---|---|
| Pipeline list, canonical names, project | `melaya_pipeline_list` with `project` |
| Steps, step kinds, agents, instructions, `agent_tools`, `human_approval_tools`, model, `hitl_mode`, schedule, persistent memory | `melaya_pipeline_get` per pipeline (config is authoritative; generated code is not needed) |
| What a tool does and its parameters | `melaya_pipeline_registry` search, the tool descriptions, public docs |
| Real example output | `melaya_run_status` (read `outcome`), `melaya_run_inspect` with `include_tool_calls`, and read-only artifact checks via `melaya_connector_call` |
| Requirements | The client's requirement document, quoted verbatim |
| If a generator script produced the configs | Read its shared constants (thesis, provenance rule, reliability order, design rule, writing rules) and quote them once in the hub or how-it-works note |

Save the fetched configs locally (scratch folder) so every later check diffs against the same snapshot.

### 2. Build the inventory tables first

Before writing prose, derive these from the saved configs, mechanically:

- Pipelines: name, display name, trigger/schedule, inputs used, outputs, email or external write, `hitl_mode`, gated tools.
- Steps per pipeline: step id, kind (single or parallel, join strategy), agent name, tools.
- Tools: the union of all `agent_tools`, with the pipelines using each, then grouped by category and cost class (keyless public, local compute, free Melaya engine, connector, paid).
- Approvals: every tool in `human_approval_tools`, per pipeline, plus the effective semantics of `hitl_mode` (in `safe` the listed tools are gated; other modes drop the per-agent list, so do not claim a gate that the mode removes).

A short script over the JSON is the right tool. Counts in the docs (number of tools, columns, steps) must come from this inventory.

### 3. Write the shared notes, then one note per pipeline

- Fill `00` last among the shared notes: its tables summarise the others.
- Write pipeline notes in dependency order (upstream producers first) so "reads / hands to" links are consistent.
- Every pipeline note follows the 14 sections in the same order. An empty section is a finding: write "None" with one line why, never delete the heading.

### 4. Requirement traceability matrix

In `00 Overview.md`, one table per requirement area:

| Requirement (quoted) | Pipeline and step | Status |
|---|---|---|

Status vocabulary, defined above the tables:
- **covered**: the pipeline does it today on public data or run inputs.
- **partly**: part is met; name the gap in the same cell.
- **needs client data**: logic exists, needs client access, templates or history.
- **not covered (out of scope)**: explicitly excluded.

Every requirement line of the source document appears once. Close with an "Out of scope, confirmed" paragraph listing what no pipeline does.

### 5. Pilot vs production

Every pipeline note and the security note have a two-column table `| Pilot | Production |`. Typical rows: recipient of emails, approval gates, data sources (public only vs client systems), templates and rubrics (draft vs client official), schedule (manual vs armed), brand (default vs client theme). State only production changes that are configuration or connection changes; label anything that needs new build work as "next step".

### 6. Diagrams that render

Use mermaid in fenced blocks. Rules that avoid broken renders:
- Quote every node and edge label: `A["Step 1<br/>Target Resolver"]`, `A -->|"new rows"| B`.
- Use `<br/>` for line breaks inside quoted labels. No raw parentheses, colons or slashes outside quotes.
- Node ids are short ASCII tokens (`P1`, `DB`, `W`). Never use `end`, `graph`, `subgraph` or `class` as an id.
- Data stores as cylinders `DB[("Name")]`, start as stadium `S(["Run"])`, decisions as `Q{"Question?"}`.
- Chart types used in the set: `flowchart TD/LR` (flows), `sequenceDiagram` (anatomy of one run), `stateDiagram-v2` (row status lifecycle), `pie showData` (tools by category). In `stateDiagram-v2` transition labels follow a colon and are not quoted.
- Write tool names on the nodes that call them; a reader should be able to match a node to the step table.
- One diagram per concept. If it needs more than about 15 nodes, split it.

Check each diagram in a mermaid renderer (Obsidian preview or mermaid.live) before handover.

### 7. Callouts (Obsidian syntax)

| Callout | Use |
|---|---|
| `> [!info] In one sentence` | Top of every note, the sentence a partner can repeat |
| `> [!warning]` | Limits, untrusted files, places the model computes, placeholder values |
| `> [!tip]` | Suggested cadence, re-branding, "during the pilot you need no internal data" |
| `> [!success]` | Validated real run output |
| `> [!example] Illustrative, not a real <entity>` | Invented example output |
| `> [!abstract]- Technical appendix` | Collapsed, last block of a pipeline note: canonical name, step ids, memory, schedule |

## Keeping docs in sync with configs

The configs are the source of truth. Docs are regenerated from them, never edited toward what we wish the system did.

1. After any `melaya_pipeline_save`, re-fetch with `melaya_pipeline_get` and diff against the saved snapshot.
2. Map each change to the doc sections it touches:

| Config change | Update |
|---|---|
| Step added, removed, reordered, made parallel | Pipeline note sections 4, 5, 6, appendix; `01` if it was the parallel example |
| Tool added or removed on an agent | Pipeline sections 4, 5, 6; `03` catalogue (count, pie chart, used by); `06` least-privilege table |
| `human_approval_tools` or `hitl_mode` changed | Pipeline frontmatter `hitl`, section 9; `06` tables and approval diagram |
| Schedule set or armed, trigger added | Pipeline section 2 and frontmatter; `02` schedules table; `00` at-a-glance |
| Output document or data store column changed | Pipeline section 7; `04` schema; `05` document list |
| Model changed | Frontmatter `model`, glossary row in `00`, data boundaries in `06` |
| Requirement newly met or dropped | `00` traceability status |

3. Bump `updated:` in every touched note.
4. Grep the set for stale numbers (tool counts, column counts, step counts) and for any tool name no longer in the inventory.
5. Never document a capability the configs do not have. Roadmap items go under "Pilot vs production" or "next step", clearly labelled.

## Definition of done

- [ ] Every pipeline in the project has a note; every note has all 14 sections in order.
- [ ] Every tool in any `agent_tools` appears in `03`, and nothing else does.
- [ ] Every client requirement line is in the traceability matrix with a status.
- [ ] Every mermaid block renders; every label is quoted.
- [ ] Every example is either a validated run (`[!success]`) or labelled illustrative.
- [ ] Approval claims match `hitl_mode` and `human_approval_tools` exactly, for pilot and production.
- [ ] Known limits listed per source and per pipeline; failed sources described as MISSING, never as a clean result.
- [ ] "What a human still validates" list present in every pipeline note and in `06`.
- [ ] No client or third-party secrets, file ids, internal emails, IPs or internal paths. ASCII punctuation only.
- [ ] `updated:` set on every file; wikilinks between notes resolve.
- [ ] No internal identifiers in any note: no run ids, file or sheet ids, internal tool-implementation names, environment variable names or server details. Canonical pipeline names, project name and public tool names only.
- [ ] If the owner is non-technical, the "Handover to a non-technical owner" checklist is done and recorded in `00`.

## Handover to a non-technical owner

When the person who will own the system day to day is not technical, the doc set is not enough. Walk them through each item live, have them do it themselves once, and tick it only when they did.

Account and access
- [ ] They sign in to the Melaya app with their own account (never a shared login) and see the project in their project list.
- [ ] They are in the project with the right role (`melaya_team_list`): owner if they will add colleagues, editor if they will run and adjust, viewer if they only read results.
- [ ] Every connector the pipelines use shows as connected under **Connectors**, on the account the runs use (personal or project credentials, per pipeline). They know that "reconnect <service>" means: open Connectors, sign in again, press Allow.
- [ ] If any pipeline runs on a local runner: they know which computer, that it must be on and online at run time, and how to start the runner.

Running
- [ ] They start one pipeline with **Run now** and one with **Run with inputs** (a short brief, plus a file if the pipeline takes one). Give them one good example brief per pipeline, written down.
- [ ] They know which pipelines run on a schedule, when, and how to pause one (or ask their assistant to).

Approving
- [ ] They have seen a real approval card: what it shows (the action and its content), **Approve** vs **Reject**, and that nothing is sent until they decide.
- [ ] They know where approvals wait: the pipeline's approval queue in the app (a pipeline waiting shows "awaiting approval"), and on their phone if paired.
- [ ] They know a run that looks stuck is usually waiting for them.

Reading results
- [ ] They open each output where it lands (the email, the Google Doc, the Sheet) and know the naming pattern to find it again.
- [ ] They can read the provenance tags (SOURCE / INFERRED / MISSING, or the system's own scheme) and know MISSING means "we could not verify", not "all clear".
- [ ] They know the run page shows each step and its messages, and that "finished" is not the same as "good": the output is the proof.
- [ ] They have the list of what a human always checks (from `06`).

Getting help
- [ ] Their AI assistant is connected to the Melaya MCP and they know they can ask it in plain words ("why did yesterday's run fail?", "how are my pipelines doing this week?").
- [ ] They know how to report a problem to Melaya: the **Report a bug** button in the app (severity, area, title, description, screenshots), and the **My bugs** tab for replies.
- [ ] They have your contact for changes to the pipelines themselves, and the rule: never edit the instructions of a delivered pipeline without re-validating it.

Put this checklist, filled in, at the end of `00 Overview.md` under "Handover" with the date and who did each item.

## Presenting the handover

- Lead with `00 Overview.md`: the one-paragraph callout, the lifecycle diagram, then the traceability matrix. That is the meeting agenda.
- Demo one pipeline live with Run with inputs, then open its note side by side: the step table matches what the run page shows.
- Close with `06`: what runs alone, what waits for a click, what a human always validates.
