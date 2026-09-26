# Melaya glossary (plain words)

Use these definitions when the user asks "what is a ...". One or two sentences, then an example.
Avoid other jargon in the answer.

| Word | Plain meaning | Example |
|---|---|---|
| **Pipeline** | A saved job made of one or more steps. You run it whenever you want, or it runs on a schedule. | "Company research": find facts, write a summary, email it to me. |
| **Step** | One stage of a pipeline. Steps happen in order; some steps run several agents at the same time. | Step 1 research, step 2 write, step 3 send. |
| **Agent** | One AI worker inside a pipeline, with a role and written instructions. | A "Researcher" agent, a "Writer" agent, a "Mailer" agent. |
| **Instruction** | What an agent is told to do, in plain language. Changing it changes the agent's behaviour. | "Write a one-page summary with sources." |
| **Tool** | One specific action an agent can take: search news, read a file, write a Google Doc, send an email. Agents can only use the tools given to them. | `gmail_send`, `scrape_page`, `word_create`. |
| **Connector** | A service you linked to your Melaya account, such as Gmail, Google Drive or a CRM. It unlocks that service's tools. Many public data tools need no connector at all. | Connecting Gmail lets an agent read or send your mail. |
| **Model** | The AI brain the agents think with, from a provider you choose (a cloud provider with your key, or one on your own computer). Nothing runs until one is connected. | A fast cloud model for everyday work. |
| **Run** | One execution of a pipeline, from start to finish. Each run is recorded: what each agent said, which tools it used, what it produced, what it cost. | "Yesterday's run found 12 new articles." |
| **Outcome** | The result of a finished run: success, failure or cancelled. (A run's "status" says only whether it is still going.) | "The run finished with outcome success." |
| **Run inputs** | What you give one run without changing the pipeline: a **brief** (free text) and **files** (up to 10). Some pipelines also ask for named fields, such as "company". | Brief: "Do it for acme.com, focus on pricing." File: their deck. |
| **Brief** | The free-text part of run inputs. Every agent sees it, for this run only. | "Only news from the last 7 days." |
| **Approval** | A pause where the pipeline asks you before an important action (sending, posting, changing something outside Melaya). You approve, edit then approve, or reject. Only a person can decide. | "The Mailer wants to send this email to 3 people. Approve?" |
| **Schedule** | A repeating time at which a pipeline runs on its own. Needs a paid plan. | Every Monday at 08:00 Paris time. |
| **Trigger** | Something outside that starts a pipeline or an action: a webhook, a new item in a feed, an event from a connected service. Actions from triggers always ask for approval unless you allowed them in the app. | "When a new form is submitted, run the intake pipeline." |
| **Project** | A folder that groups pipelines and the people who can see them. Created in the app, or by your assistant if you gave it the projects permission. | "Sales ops" project with 4 pipelines and 3 members. |
| **Owner / Editor / Viewer** | Project roles. The owner manages members; editors can change pipelines; viewers can look. | Invite a colleague as Viewer. |
| **Template** | A ready-made pipeline you copy into your project, then adjust. Settings you should change are marked EDIT ME. | "Competitor news digest" template. |
| **EDIT ME** | A marked block in a template's instructions listing the settings to adjust (a watch list, a threshold, a recipient). Your own copy should end up with real values and no EDIT ME left. | `WATCHLIST = ...` |
| **Runner** | A small program you can run on your own computer so Melaya can use models and files that live there (for example your Claude Code subscription). Optional. Only works while that computer is on. | "Run Locally" pipelines need the runner. |
| **Run Locally** | A pipeline setting that makes its runs happen on your runner instead of in Melaya's cloud. Files given as run inputs reach cloud runs only today. | Keep sensitive work on your machine. |
| **Memory** | An optional setting that lets a pipeline remember what past runs learned. Off unless you want it. | "Remember which companies you already reported." |
| **Assistant** | The AI you are talking to (connected to Melaya), or Melaya's own chat inside the app. Both can run your pipelines for you. | "Run my research pipeline for acme.com." |
| **Eval / quality score** | An automatic check of whether a run did its job, when the pipeline is set up for it. | "Acceptance rate this week: 90%." |
