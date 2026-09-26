# Phase checklists and exit evidence

Tick every box before leaving a phase. "Evidence" is what you show the user (or paste into the engagement log) to prove the gate is met.

## Phase 0: Frame

- [ ] Requirements numbered R1..Rn, each phrased as a business outcome.
- [ ] For each: user of the output, cadence, trigger, forbidden actions.
- [ ] Pilot vs production scope agreed (who receives outputs, which data is allowed).
- [ ] Success criteria per requirement (what a correct output looks like).

Evidence: the confirmed requirement list.

## Phase 1: Discover (module: `../../../modules/discovery/GUIDE.md`)

- [ ] `melaya_setup_status` read; every reported gap noted with its fix.
- [ ] `melaya_account_usage` read: pipeline, run and project caps known.
- [ ] `melaya_connector_list` read; connected vs missing services listed.
- [ ] `melaya_connector_tools` searched per requirement keyword.
- [ ] `melaya_pipeline_registry` searched for every planned capability; ids copied exactly.
- [ ] `melaya_model_list` for the validation provider returns `status: "ok"`; model id copied exactly.
- [ ] `melaya_pipeline_templates` searched for each workflow.
- [ ] Keyless public-data tools chosen first wherever they satisfy a requirement.

Evidence: requirement-to-tool table (requirement | tool ids | connector needed | keyless? | template?).

## Phase 2: Connect

- [ ] For every missing service: `melaya_connector_connect` link handed to the user.
- [ ] User confirmed each consent.
- [ ] `melaya_connector_test` passes per service.
- [ ] One real read per service through `melaya_connector_call` succeeds.
- [ ] Nothing worked around (no scraping a service the user did not connect).

Evidence: per-service test result + one real read result.

## Phase 3: Project (module: `../../../modules/projects-templates/GUIDE.md`)

- [ ] Project created with `melaya_project_create` (needs the `melaya:projects` permission), or by the user in the Melaya app.
- [ ] `melaya_team_list` returns it; the account's role can save pipelines there.
- [ ] Template vs raw authoring decided per workflow.
- [ ] Collaborator invites confirmed with the user before `melaya_team_invite`.

Evidence: `melaya_team_list` output.

## Phase 4: Design

- [ ] `system-design-template.md` (in this folder) filled for every section.
- [ ] One pipeline per workflow; dependency graph drawn.
- [ ] Data spine schema fixed (fields, `__src`, `__status`, status lifecycle, dedupe key, unique name).
- [ ] Run inputs declared per pipeline (brief, typed inputs, files).
- [ ] Every human-facing output is a designed document with a theme.
- [ ] Every external write listed with its gate decision.
- [ ] Per-agent tool budget and stop condition written.
- [ ] User approved the design.

Evidence: the approved design document.

## Phase 5: Author (module: `../../../modules/pipeline-authoring/GUIDE.md`)

- [ ] One generator script emits every config from shared constants.
- [ ] Agents embedded in `steps[]` (`step.agent`, or `step.agents[]` for `kind: "parallel"` with `joinStrategy: "concat"`).
- [ ] Every agent: `instruction` (singular), `agent_tools`, `human_approval_tools`, `model_provider`, `model_name`, `include_context`, `loop_policy` object.
- [ ] Pipeline: `hitl_mode`, `persistent_memory`, `schedule`, `inputs`, `description`, `user_lang`.
- [ ] No `[START EDIT ME]` blocks, no credentials, no private ids hard-coded where a run input or find-or-create would do.
- [ ] `melaya_pipeline_preview` clean for every config (no dropped fields, no unknown tool ids, no undeclared `{{inputs.x}}`).
- [ ] `melaya_pipeline_save` done; canonical names recorded.
- [ ] `melaya_pipeline_get` read-back matches the generator output.

Evidence: list of canonical pipeline names + preview/read-back confirmation.

## Phase 6: Validate (module: `../../../modules/validate-debug/GUIDE.md`)

Per pipeline, in dependency order:

- [ ] `melaya_pipeline_run` with a realistic `brief` (and `files` / `inputs`).
- [ ] `melaya_run_status` polled to `terminal`; `outcome` is success.
- [ ] `melaya_run_inspect` `include_tool_calls: true` reviewed: no retyped wide data, no repeated failing calls, no guessed URLs, within budget.
- [ ] `melaya_run_diagnosis` reviewed: eval verdicts, tool forensics, cost.
- [ ] `melaya_approval_list` checked; pending approvals reported to the user, never approved by you.
- [ ] `melaya_agent_memory` checked when memory is on.
- [ ] Artifact verified with `melaya_connector_call`: spine rows aligned, provenance filled, documents open and readable, exactly one email.
- [ ] Duration and cost recorded.
- [ ] Runaway runs cancelled with `melaya_run_cancel`.

Evidence: per-pipeline table (run id | outcome | duration | cost | artifact checked | defects).

## Phase 7: Fix

- [ ] Every defect classified: config/prompt, platform code, setup, design.
- [ ] Config fixes made in the generator, regenerated, previewed, saved.
- [ ] Platform issues: reported to Melaya with a minimal reproduction; release confirmed before dependent configs are saved.
- [ ] Stale memory reviewed after tool fixes (user decides on clearing).
- [ ] Affected pipelines re-validated.

Evidence: defect log (symptom | class | fix | re-run id).

## Phase 8: Document (module: `../../../modules/client-handover/GUIDE.md`)

- [ ] Hub, ELI5, run inputs/schedules/triggers, tools, data model/provenance, documents/branding, security/human control.
- [ ] One note per pipeline using the shared template.
- [ ] Examples from real validated runs only.
- [ ] Pilot vs production differences explicit.
- [ ] No ids, keys, hosts, internal paths.

Evidence: the documentation set.

## Phase 9: Hand over (module: `../../../modules/automation-governance/GUIDE.md`)

- [ ] Schedules armed (`melaya_pipeline_schedule` `set`) and confirmed (`status`).
- [ ] Triggers created and tested (`melaya_pipeline_trigger` `create`, `test`, `deliveries`); user copied signing secrets from the UI.
- [ ] Production gates on for every external write; pilot exceptions written down.
- [ ] Client team invited (confirmed with the user).
- [ ] `melaya_eval_report` baseline captured.
- [ ] First-week runbook delivered.
- [ ] Open platform issues listed with release status.

Evidence: schedule status, trigger test deliveries, eval baseline, signed-off definition of done.
