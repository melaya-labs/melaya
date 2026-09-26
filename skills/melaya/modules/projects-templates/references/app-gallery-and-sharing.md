# Templates for a non-technical user: gallery, set-up, sharing

Use this when the person you work for will click through the Melaya web app
themselves, or wants a pipeline "like the one in the gallery" without writing
any configuration. Everything here can also be done over MCP (see SKILL.md);
the app route is often easier for a first pipeline because the user sees
each step.

Words to define once:

| Term | Plain words |
|---|---|
| Template | A ready-made pipeline someone already built and tested. Using it makes your own copy; the original is not changed |
| Pipeline | A sequence of AI agents that does one job (for example "every Monday, summarise my inbox and email me") |
| Project | A folder that holds pipelines and decides who else can see them |
| Settings block | The few lines at the top of an agent's instructions that you are meant to change (marked `[START EDIT ME]` ... `[END EDIT ME]`) |
| Connector | A service Melaya is allowed to use for you (Gmail, Google Drive, Slack ...), connected once |

## 1. Before you start (2 minutes)

1. Call `melaya_setup_status`. If no model is connected, walk the user
   through it first: nothing runs without one ("Connectors", pick a provider,
   paste its key, or use the local runner).
2. Decide the project. Personal use: the personal project (named after the
   user) is fine. Team or client use: a dedicated project; the user creates
   it in the app under **Projects > New Project**, or the assistant creates it
   with `melaya_project_create` if the user granted the projects permission.

## 2. Find a template in the gallery

Say to the user: "Open the Melaya app, go to the Agent Builder and open the
**Templates** tab."

What they see and can use:

| Control | What it does |
|---|---|
| Search box ("Search templates by name, tag, or description") | Type the job in everyday words: "inbox", "weekly report", "competitor", "invoice" |
| Filters: All, My templates, My team, Assigned to me, Community, Validated | Narrow the list. **Validated** = tested and approved by the Melaya team (a check badge on the card) |
| Category chips | Browse by area |
| Card | Name, short description, number of steps and tools, schedule (Manual, Daily, Weekly ...), who created it |
| Card details | The list of steps, the agents, the tools each uses |

You can run the same search over MCP to recommend candidates:
`melaya_pipeline_templates { "search": "inbox" }`. Suggest two or three
cards by name and say why each fits.

How to choose, in plain words:
- Prefer a **Validated** template.
- Check the tools on the card: each service it lists (Gmail, Drive, Slack)
  must be connected, or the run will stop and ask for it.
- Prefer the smallest template that does the job; fewer steps means fewer
  things to set up.

## 3. Use it and set it up

1. The user clicks **Use this template** (or **Use template**).
2. A window **Set up your template** opens, with one card per agent that has
   settings. It says the template runs out of the box with these defaults.
3. For each card, the user either types their own values (for example their
   keywords, the recipients, a threshold) and clicks **Apply my values**, or
   clicks **Keep defaults**.
4. They can reopen the window later from **Template settings** or from the
   amber settings warning on any agent card. An amber banner above the
   instructions reminds them that only the lines between the markers are
   meant to be edited; the rest is the agent's wiring.
5. They save the pipeline into the chosen project.

What to tell them to type in the settings: concrete values, never
placeholders. "Keywords = invoice, overdue, payment reminder", not
"<your keywords>". Leave anything the tool can find on its own (their own
email address, their own calendar) alone.

Over MCP instead: `melaya_pipeline_from_template` with the template `id`, a
lowercase `name` and the `project`, then edit the settings block of each
agent with the get / edit / save loop in SKILL.md section 4.2.

## 4. Connect services, then run once

1. `melaya_connector_list`: every service the template's tools need must be
   connected. For each missing one, `melaya_connector_connect` gives a link;
   the user opens it, signs in and presses Allow. Never ask them to paste a
   password or key into the chat.
2. First run: **Run now** (as saved) or **Run with inputs** (adds a short
   brief, up to 8 KB, and optional files for that run only).
3. Watch it: `melaya_run_status` (read `outcome`), then check the real result
   (the email, the Doc, the Sheet) with the user. The `../../../modules/validate-debug/GUIDE.md`
   skill covers what to do when it fails.
4. If a step waits for approval (typically before sending an email or
   posting), the user decides in the app (the approval queue on the pipeline)
   or on their phone. You never approve for them.
5. Only after a good run, and only if they want it automatic, arm a schedule
   (`melaya_pipeline_schedule` action `set`; needs a paid plan). A schedule
   shown on the template card is not active until it is set.

## 5. Share with a team

Two different things can be shared. Say which one the user means.

### 5a. Share the pipelines (people use and see the same pipelines and runs)

Access follows the project. Everyone added to the project sees its pipelines
and runs according to their role.

| Role | Can |
|---|---|
| owner | Everything, including inviting and removing people, and managing the project's shared connectors |
| editor | Change and run pipelines, including with the project's shared connectors |
| viewer | See the project, its members and which services are connected (names only); cannot run with the shared connectors |

Steps:

1. `melaya_team_list { "project": "<Project>" }` shows who is already in and
   the caller's own role. Only the owner can invite.
2. Confirm with the user, by name, who gets access and which role. An invite
   grants a real person access to the project's pipelines, run results and
   the documents those runs produced.
3. Invite:
   - existing Melaya user: `melaya_team_invite { "project": "<Project>", "username": "<their Melaya username>", "role": "viewer" }`;
   - anyone else: omit `username` to get a shareable invite link
     (`expiry_hours` 1-720, default 72; `max_uses` 1-100, default 10). The
     user sends the link themselves; you never send it.
4. `melaya_team_list` again to confirm.

Caveats to say out loud:
- Seats count against the owner's plan limit; an invite can be refused when
  the plan is full.
- Invitees never become owner through an invite.
- Whose accounts does a pipeline use? Each pipeline has a **Credentials**
  choice in its Config tab: personal (the connectors of the person who runs
  it) or project (connectors the owner stored for the whole project). With
  personal, a teammate who runs a pipeline that sends Gmail needs their own
  Gmail connected, or the run stops at that tool. With project, the email goes
  out from the shared account. Decide this before inviting people.
- A pipeline that runs on the owner's local runner cannot run while that
  computer is off, whoever presses Run.
- Use a short-lived link (for example `expiry_hours: 24`, `max_uses: 1`) for
  one person; do not post a long-lived link in a public channel.

### 5b. Share a template (people get their own copy)

In the Templates tab, the template's actions menu offers **Share with team**
(pick a project; teammates see it under **My team**), **Unshare** and
**Duplicate**. Sharing into a team needs a project the user owns. Community
listing is decided by the Melaya team after review; a user cannot publish to
the community directly.

Caveats:
- A shared template is frozen: to change it, **Duplicate** it (this makes a
  private new version), edit, and share the new version.
- Deleting a pipeline does not delete a template saved from it, and the
  reverse.
- Templates carry no credentials and no schedule that is armed; each person
  connects their own services and arms their own schedule.
- A template you saved from a client pipeline may contain client names and
  addresses. Before sharing it beyond that client's project, replace them with
  neutral defaults.

## 6. Caveats to mention before the user relies on a template

- A template is a starting point tested on someone else's data. The first run
  on the user's data is the real test: check the output together.
- Community templates that are not validated are hidden from the MCP listing
  (`withheld_unvalidated` counts them).
- Some templates use paid tools or paid model providers; the card's tools and
  model tell you. Mention any cost before the first run.
- Approval gates on sends are a feature. Do not remove them to "make it
  faster" without the user's explicit decision.
- Renaming: there is no rename. Create a new pipeline under the new name and
  delete the old one only if the user asks.
