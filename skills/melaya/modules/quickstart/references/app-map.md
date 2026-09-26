# Where things are in the Melaya app

The app lives at https://app.melaya.org. Menu labels below are the English ones; the app is also
available in other languages, so describe the place ("the Connectors page") as well as the label.

Give the user one click path at a time and wait for "done" before the next.

## Main menu

| Menu item | What it is for |
|---|---|
| Overview | The home dashboard: projects, runs and usage at a glance |
| Assistant | Chat with Melaya's own assistant inside the app |
| Agent Builder | Create, open, change and run pipelines |
| Monitor | Projects, their runs, run details and the project's Team |
| Connectors | Connect services (Gmail, Drive...) and AI model keys |
| Tools | Browse what agents can do |
| Device Control / Phone Setup | Pair an Android phone and approve the apps agents may open |
| Traces | Detailed technical record of every run (for troubleshooting) |
| Guided tutorials, Documentation, Report a bug | Help |

## Click paths

**Connect a service or a model key**
1. Menu: Connectors.
2. Find the service (search box) and open it.
3. For Google, Microsoft and other sign-in services: click connect and approve on the provider's
   own screen. For key-based services and AI models: paste the key there (never in a chat).
4. The card shows as connected. Ask the assistant to test it.

**Create a project**
1. Menu: Monitor. Click **New Project**, give it a name.
2. Or in the Agent Builder, **Configure** tab, **Project** field: type a new name and choose
   **+ Create project**.
3. Or ask the assistant: with the "projects" permission it creates, renames and (only when empty)
   deletes projects for you.

**Open a pipeline**
1. Menu: Agent Builder. Open the pipeline list and pick it (or go through Monitor, then the project).
2. Tabs across the top: **Configure** (name, project, model, "Apply to all agents", "Run Locally",
   memory), **Schedule & Triggers**, **Docs** (reference files the agents read), **Pipeline** (the
   steps on a canvas; click an agent to see its instruction and tools), **Code**, **History** (past
   runs), **Memory**.

**Run a pipeline**
1. Open the pipeline. Click the Run control.
2. **Run now** (as saved) or **Run with inputs** (type a Brief, drop files, fill named fields, then
   **Run**).
3. The live run view appears. Later, the run keeps a **Run inputs** card with **Run again**.

**Approve or reject**
1. Open the pipeline that is waiting in the Agent Builder (a waiting pipeline pulses in the list; the
   browser tab shows a count).
2. Click the approvals bell on the canvas. The panel "Pending tool approvals" lists each action.
3. For each: **approve**, **edit** then **approve with edits**, or **reject** (optional reason).
4. Approvals for automatic triggers also appear in the **Schedule & Triggers** tab under Pending
   approvals.
5. With the Melaya app on a paired Android phone, approvals can also be answered there.

**Schedule**
1. Open the pipeline, **Schedule & Triggers** tab, Schedule section.
2. Pick the time and time zone, save. Event triggers (webhooks, new items) are in the same tab.

**Invite a teammate**
1. Menu: Monitor, open the project, **Team** tab.
2. **Invite** by username (choose Viewer or Editor), or **Invite by link** for a QR code and link.
   Only the project owner can manage members.

**Use a template**
1. Agent Builder: **Templates** (or "Start from a template" on a new pipeline).
2. Pick one, **Use this template**, choose the project.
3. Look for settings marked EDIT ME in the agents' instructions and fill them in before the first run.

**Share a pipeline design with your team**
1. Agent Builder, Templates: **Save as template**, then **Share with team** and pick the project.
