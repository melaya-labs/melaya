<!-- Module of the melaya skill. Entry point: ../../SKILL.md -->
> Use when organising Melaya pipelines into a project, creating, renaming or deleting a project (melaya_project_create / _update / _delete), finding or addressing an existing pipeline (melaya_pipeline_list, melaya_pipeline_get), browsing the template library (melaya_pipeline_templates), instantiating a template (melaya_pipeline_from_template), deciding whether to start from a template or author a config from scratch, handling canonical names vs display names, applying or removing [START EDIT ME] blocks, authoring a reusable template to the platform standard, walking a non-technical user through the in-app template gallery, sharing pipelines or templates with a team (melaya_team_list, melaya_team_invite), or deleting a pipeline (melaya_pipeline_delete). Load it at the start of any build, before the first save, and whenever a pipeline tool returns "not found".

# Melaya projects and templates

How pipelines are grouped (projects), how they are addressed (canonical names), how to reuse known-good
documents (templates), and when to write your own. Config authoring details live in the
`../../modules/pipeline-authoring/GUIDE.md` skill; validation in `../../modules/validate-debug/GUIDE.md`.

| Reference | Load when |
|---|---|
| `references/app-gallery-and-sharing.md` | The user will click through the app themselves (Templates tab, "Use this template", "Set up your template"), or wants to share pipelines or templates with a team |
| `references/template-standards.md` | You are authoring a template others will reuse |

## 1. Projects

A project is the container every pipeline lives in. Access, team membership, plan caps and the
per-pipeline visibility rules are all scoped to it.

| Fact | Consequence for you |
|---|---|
| Every account has a personal project named after the user, created at sign-up. | There is always somewhere to save. |
| Additional projects are created with `melaya_project_create` (needs the user's `melaya:projects` permission) or in the Melaya web app. The same plan rules apply either way: Forge plan or above, within the plan's project limit. | If the client needs a dedicated project, create it over MCP (section 1.1); without the permission, ask the user to create it in the app, then continue. |
| Saving or instantiating into a project requires the account to be a member (or the project's creator). | A "not a member of project" error means: wrong spelling, or the user must add this account. |
| Project names are matched exactly (case and punctuation). | Copy the project string from `melaya_pipeline_list` output, never retype it. |
| Two pipelines in different projects can share a name. | Always pass `project` to get / save / run / schedule / delete. |
| Team access: `melaya_team_list` (members, roles, caller's role) and `melaya_team_invite` (owner only; `viewer` or `editor`; direct `username` or a shareable link with `expiry_hours` 1-720 and `max_uses` 1-100). Seats count against the owner's plan. | Confirm with the user, by name and role, before inviting anyone: it grants a real person access to the pipelines, runs and produced documents. Full procedure and caveats (whose connectors a teammate's run uses): `references/app-gallery-and-sharing.md`. |

### 1.1 Managing projects over MCP

| Tool | Permission | What it does |
|---|---|---|
| `melaya_project_list`, `melaya_project_get` (`name`) | read | The account's projects; one project's description, creation date, the account's role, member count |
| `melaya_project_create` (`name`, optional `description`) | `melaya:projects` | A new project owned by the account. Names are unique across Melaya: a taken name is refused, pick a distinctive one |
| `melaya_project_update` (`name`, `new_name` and/or `description`) | `melaya:projects`, owner or editor | Rename, or replace the description (an empty string clears it) |
| `melaya_project_delete` (`name`, `confirm`) | `melaya:projects`, creator only | A dry run unless `confirm: true`. Removes only an EMPTY project: no pipelines, no run history, no other members, and not the user's only project. There is no cascade: delete the pipelines first (`melaya_pipeline_delete`), and a project with run history stays |

Procedure for a client pilot: `melaya_project_create` once, save every pipeline into it (`project` on every call), and at the end of a throwaway test run the delete as a dry run, show the user what it reports, and confirm only when they agree. A missing tool means the user did not grant `melaya:projects` (or connected before it existed): offer a reconnect.

### Rule: one system, one project

All pipelines of one client system live in ONE project (for example `Acme-Investments`). Benefits:
`melaya_pipeline_list project=<Project>` shows the whole system, team access is granted once, and
a same-named pipeline elsewhere can never be hit by mistake.

### Which tools take `project`

| Tool | `project` | Notes |
|---|---|---|
| `melaya_pipeline_list` | optional filter | Filter is an exact string match on the project name. |
| `melaya_pipeline_get` | optional, recommended | Disambiguates same-named pipelines. |
| `melaya_pipeline_save` | argument overrides `config.project` | `mode "create"` fails without a project (argument or `config.project`). |
| `melaya_pipeline_from_template` | REQUIRED | Stamped last; overrides cannot change it. |
| `melaya_pipeline_run` | optional, recommended | |
| `melaya_pipeline_schedule` | REQUIRED | |
| `melaya_pipeline_delete` | REQUIRED | Pins the delete to one exact pipeline. |
| `melaya_team_list`, `melaya_team_invite` | REQUIRED | |
| `melaya_project_get`, `_update`, `_delete` | `name` (REQUIRED) | The project itself, by exact name. |

## 2. Canonical names vs display names

The name you send is NOT necessarily the name that addresses the pipeline.

- On create, the builder canonicalises the name: lowercased and snake_cased, camelCase-aware
  (`LinkedIn` becomes `linked_in`, `Deal Screening` becomes `deal_screening`).
- The canonical name is returned by `melaya_pipeline_save` (`pipeline` field and the "Created
  pipeline ..." text), by `melaya_pipeline_from_template` (`pipeline` field), and by
  `melaya_pipeline_list` (`name` field). If it differs from what you sent, the response says so.
- `display_name` is the human title shown in the app. It is NOT an address. `melaya_pipeline_list`
  returns both `name` and `display_name`.

Checklist:

- [ ] Send a lowercase snake_case `name` (letters, digits, underscores) so it canonicalises to itself.
- [ ] Set `display_name` in the config to the human title (for example "Acme - Deal Screening").
- [ ] Record the canonical name from the create response in your build notes / generator script.
- [ ] Use only that canonical name in every later call. On any "not found", call
      `melaya_pipeline_list project=<Project>` and copy `name` from there.
- [ ] Use a consistent prefix per system (for example `acme_p1_sourcing`, `acme_p2_screening`) so
      the list sorts in workflow order.

Note: `schedule_in_config` in the list output is only the cron written in the config. It does not
mean the scheduler is armed; check with `melaya_pipeline_schedule action=status`.

## 3. Listing and reading pipelines

```
melaya_pipeline_list  project="<Project>"  limit=60
```
Returns `total`, and per pipeline: `name`, `display_name`, `project`, `description` (first 200
chars), `schedule_in_config`, `agents`, `tools`, `is_mobile`, `force_local_runner`. Page with
`offset` when `total` > `limit` (max 60). Includes pipelines that have never run. If a pipeline
created seconds ago is missing, retry once without the filter before concluding it does not exist.

```
melaya_pipeline_get  pipeline="<canonical>"  project="<Project>"
```
Returns `name`, `project`, `config` (the full editable document), `docs` (files attached in the
Docs tab) and a note listing the declared run inputs. Add `include_code: true` only when you need
the generated Python.

### ALWAYS get before you update

There is no patch. `melaya_pipeline_save mode="update"` replaces the WHOLE document; any field you
omit is erased. The only safe edit loop:

1. `melaya_pipeline_get` (or regenerate the full config from your generator script).
2. Modify locally. Agents are embedded in `steps[]` (`step.agent`, or `step.agents[]` for a
   parallel step): edit THOSE copies. Editing only a top-level `agents[]` is a silent no-op.
3. `melaya_pipeline_preview` the full config; check every edited value appears in the code.
4. `melaya_pipeline_save mode="update" pipeline="<canonical>" project="<Project>" config=<full>`.
5. `melaya_pipeline_get` again and compare: the parser silently drops unknown fields.

## 4. Templates

### 4.1 Browse

```
melaya_pipeline_templates  search="<keyword>"  category="<substring>"  limit=25  offset=0
```

- Returns the account's own private templates, templates shared into its team projects or assigned
  to it, and the admin-validated community library.
- Unvalidated community templates are withheld; `withheld_unvalidated` counts them. Absence from
  the list does not mean absence from the platform.
- `search` matches name, description or category (substring, case-insensitive); `category`
  matches the category. Max `limit` 60.
- Each entry: `id`, `name`, `emoji`, `category`, `categories`, `description` (300 chars),
  `visibility`, `validated`, `created_by`, and `builds` (step count, per-step agent name, model,
  tool count, `schedule`, `force_local_runner`, `is_mobile`).
- `builds.agents` summarises only single-agent steps; agents inside a parallel step are not listed.
  Instantiate and `melaya_pipeline_get` to see everything.

Search several angles (the business verb, the data source, the output channel): for example
`search="digest"`, `search="gmail"`, `search="due diligence"`, `category="research"`.

### 4.2 Instantiate

```
melaya_pipeline_from_template
  template_id = "<id from melaya_pipeline_templates>"
  name        = "acme_p5_weekly_digest"
  project     = "<Project>"
  overrides   = { "display_name": "Acme - Weekly Digest", "hitl_mode": "safe" }   (optional)
```

Semantics (verified against the tool):

| Behaviour | Detail |
|---|---|
| Merge order | template payload, then `overrides`, then `name` + `project` stamped last. |
| Overrides are SHALLOW | One level deep. Overriding `steps` replaces ALL steps. Keep overrides to scalar top-level fields (`display_name`, `description`, `hitl_mode`, `user_lang`, `persistent_memory`, `force_local_runner`). |
| Credentials | Never in overrides (refused). Connect services under Connectors. |
| Inline code | Any `code` in the payload or overrides is removed; the builder regenerates from config. |
| Name clash | A name that canonicalises to an existing pipeline in that project fails; pick another name or get the existing one. |
| Schedule | A cron in the template is NOT armed. Call `melaya_pipeline_schedule action=set` if the client wants it. |
| Response | `pipeline` (canonical name), `project`, `template_id`, `template`, `overrides_applied`. |

After instantiating:

1. `melaya_pipeline_get` the new pipeline.
2. Replace every `[START EDIT ME] ... [END EDIT ME]` block with concrete client values inline
   (see section 5). Change instructions, tools or models by editing the embedded step agents,
   not through `overrides`.
3. Check each agent has `loop_policy {"mode":"observe_only","evaluator":"default"}`, an explicit
   `agent_tools` list, and `human_approval_tools` on every send/post/external write with
   `hitl_mode "safe"`.
4. Preview, save `mode="update"`, read back.
5. Connect every service the tools need; validate with a real run.

### 4.3 Template or scratch?

| Situation | Choose |
|---|---|
| A validated template covers the workflow shape (fetch -> analyse -> deliver) and its tools exist on the account | Template. Payload is known-good; you avoid dropped-field and steps/agents traps. |
| Template is close but differs in tools, step count or topology | Template for reference only: read its config for structure, then author from scratch or regenerate. |
| A multi-pipeline client system sharing constants (data schema, provenance rule, writing rules, mailer) | Scratch, from a generator script, so every pipeline is re-emitted from one source of truth. |
| The workflow needs client data stores, custom schemas, run inputs, decide gates, or designed documents with a client theme | Scratch (or template + heavy rewrite). |
| Quick demo, single recurring job (digest, monitor, alert) | Template. |
| No template matches after 3 searches | Scratch. Use `melaya_pipeline_registry` for tool ids. |

Even when you author from scratch, browse templates first: they show proven instruction wording,
HITL placement and step topology for the same tools.

## 5. The [START EDIT ME] convention

TEMPLATE-ONLY. It marks the standing settings a template user must adjust.

```
[START EDIT ME]
- KEYWORDS = invoice, overdue, payment reminder (topics to track, comma-separated)
- SCORE_THRESHOLD = 7 (only report items scoring at or above this, 0-10)
[END EDIT ME]

<the directive that uses the configured values above>
```

Rules:

- Marker lines are literal and exact, ONE pair per agent instruction, at the top of the
  `instruction`.
- Defaults are REAL, runnable values (validation runs them verbatim), in `KEY = value (hint)`
  form. Never angle-bracket `<placeholder>` tokens: small models cannot substitute them.
- An agent with nothing to edit gets NO block at all.
- No block for values a tool resolves itself (the user's own mailbox address, "my events",
  own-account lookups).
- Never write the marker tokens in directive prose; say "the configured values above". The app's
  Apply step strips every marker token, and a stray token keeps the setup nudge on.
- The per-run SUBJECT (company, deck, topic) is never an EDIT ME value: it is a run input
  (`brief`, `files`, or declared `inputs` with `{{inputs.key}}`).
- A directive must be self-contained: put each value where it is used.

### In a client's real pipelines: NEVER

A delivered client pipeline contains no `[START EDIT ME]` / `[END EDIT ME]` / `[EDIT ME]` text.
Write the concrete values inline:

```
Track these topics: invoice, overdue, payment reminder. Report only items scoring 7 or more.
```

Before handover, scan every instruction of every pipeline in the project:

- [ ] `melaya_pipeline_get` each pipeline; search the config for `EDIT ME`. Zero hits.
- [ ] No `<...>` placeholder tokens, no `example.com`, no dummy ids.
- [ ] Standing settings inline; per-run subjects come from run inputs.

## 6. Authoring a reusable template (summary)

MCP cannot publish a template. Build and validate the pipeline, then the user saves it with "Save
as template" in the Agent Builder (name, description, category, emoji, tags); it starts private and
can then be shared into a team project from the Templates tab. Community promotion is an admin validation
step, not something an integrator does. Full standards: `references/template-standards.md`.

Minimum bar before "Save as Template":

- [ ] Title-Case display name; description of at least 200 characters (what it does, the benefit,
      one-time setup).
- [ ] EDIT ME blocks only where a user must supply a value; real defaults; brand-neutral for a
      shared template.
- [ ] Every agent: explicit `agent_tools`, `loop_policy` observe_only, `include_context` set
      deliberately; top-level `tools` empty.
- [ ] Every external write in `human_approval_tools`, `hitl_mode "safe"`.
- [ ] No trigger config, secrets, ids or credentials in the payload.
- [ ] Works on a run with no inputs AND a run with a brief + file.
- [ ] Functional output validated (the real artifact, not just "done").

## 7. Deleting a pipeline

```
melaya_pipeline_delete  pipeline="<canonical>"  project="<Project>"
```

- Permanent: config, generated code and uploaded Docs-tab documents are removed and cannot be
  recovered. Any armed schedule is cleared. Past runs and logs stay readable.
- Only on the user's explicit request. To stop a pipeline firing, use
  `melaya_pipeline_schedule action=pause` instead.
- Deleting a pipeline does NOT delete a template saved from it.
- Before deleting: `melaya_pipeline_get` and keep a local copy of `config` (your generator script
  should already be the source of truth), and confirm no other pipeline or trigger depends on it
  (`melaya_pipeline_trigger` list filtered by pipeline).
- Rename = create under the new name + validate + delete the old one (on request). There is no
  rename call.

## 8. Common errors

| Error / symptom | Cause | Fix |
|---|---|---|
| `No pipeline "x" is visible to this account` | Display name or pre-canonical name used | `melaya_pipeline_list project=...`, copy `name`. |
| `This account is not a member of project "x"` | Typo, or no membership | Copy the project string from `melaya_project_list`; create it with `melaya_project_create`, or ask the owner to add the account. |
| Project create refused | Plan below Forge, plan project limit reached, or the name is taken across Melaya | `melaya_account_subscription`; pick a more distinctive name; upgrade or delete an empty project. |
| Project delete refused | Not the creator, or the project still has pipelines, run history or other members, or it is the user's only project | Delete its pipelines first; a project with run history stays (no cascade). |
| `already exists` on create / from_template | Name canonicalises to an existing pipeline | Get it and use `mode "update"`, or choose a new name. |
| Fields vanished after an update | Partial config sent | Always get, edit, send the whole document. |
| Instruction edit had no effect | Only top-level `agents[]` edited | Edit the embedded `steps[]` agent copy. |
| Template instantiated but a step is missing | `overrides.steps` replaced the whole list | Re-instantiate without it; edit via get/save. |
| Template not in the list | Unvalidated community (withheld), or not shared to the account | Check `withheld_unvalidated`; ask the owner to share it into the project. |
| Pipeline "has a schedule" but never fires | Cron only in config | `melaya_pipeline_schedule action=set`. |
| `melaya_team_invite` refused | Caller is not the owner, or the owner's plan has no free seat | `melaya_team_list` shows the caller's role; the owner invites, or upgrades the plan. |
| Teammate's run stops at a send/read tool with `auth` | The pipeline uses personal credentials and the teammate has not connected that service | Teammate connects it, or the owner switches the pipeline to project credentials (see the sharing reference). |
| Shared template cannot be edited | Shared templates are frozen | Duplicate it (private new version), edit, share the new version. |
