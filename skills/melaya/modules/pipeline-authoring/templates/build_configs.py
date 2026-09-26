"""Generic Melaya pipeline-config generator (template).

Emits one config per pipeline into ./cfg/<slug>.json (readable) and ./cfg/<slug>.min.json
(compact, paste it as `config` into melaya_pipeline_preview / melaya_pipeline_save).

How to use:
1. Replace every constant in the CLIENT block (names, thesis, store, model, feeds).
2. Verify every tool id with melaya_pipeline_registry and the model id with melaya_model_list.
3. Run: python build_configs.py
4. For each pipeline: melaya_pipeline_preview (check every agent, tool, model and the quality-loop scoring call after each agent),
   then melaya_pipeline_save mode "create" the first time, mode "update" (whole document) afterwards.
5. Change rules HERE and regenerate. Never hand-edit the saved JSON.

The example system is an early-stage investment team: sourcing -> screening -> due diligence.
Add intake, memo, forms, copilot and portfolio pipelines with the same factories.
"""
import json
from pathlib import Path

OUT = Path(__file__).parent / "cfg"
OUT.mkdir(exist_ok=True)

# -- CLIENT block: replace everything here ------------------------------------
CLIENT = "Acme"                      # client display name used in titles and footers
PROJECT = "Acme"                     # Melaya project holding every pipeline of the system
MODEL = {"provider": "qwen", "name": "qwen3.7-plus"}   # confirm with melaya_model_list
STORE_NAME = "Acme Deal Pipeline"    # Drive name search is a WORD match: share no full word set with older files
TAB = "Deals"

THESIS = (
    "Investment thesis of the client: <sectors>, <sub-sectors>, stages pre-seed to series A, "
    "priority geography <region>, global founders welcome."
)

# Targeted Bing News RSS searches are reliable keyless feeds; add a few sector feeds you have checked.
FEEDS = [
    "https://www.bing.com/news/search?format=rss&q=fintech+startup+raises+seed",
    "https://www.bing.com/news/search?format=rss&q=payments+startup+raises",
    "https://www.bing.com/news/search?format=rss&q=infrastructure+startup+series+A",
    "https://news.crunchbase.com/feed/",
]

# Store columns in sheet order (paste the header returned by dealdb_schema for your tab).
# Write-back uses letters computed from this list, never hand-typed letters.
COLUMNS = ["company", "website", "country", "sector", "stage", "status", "score", "updated_at", "dd_findings"]

# Set True only when every gated send stays in the owner's own inbox and the user agreed.
UNGATED_OWNER_INBOX_DEMO = False

# -- Shared rule blocks -------------------------------------------------------
WRITING_RULES = (
    "Writing rules for anything a person reads: plain ASCII punctuation, NEVER an em dash or en dash "
    "(use a period, a comma or 'and'), no corporate or AI tells (synergy, seamless, leverage, game-changer, "
    "delve, robust), short sentences, concrete numbers with their source."
)

FIND_STORE = (
    f'Find the data store: invoke drive_search with name = "{STORE_NAME}" and mime = "sheet". '
    "Use the id of the newest result as SHEET_ID."
)

PROVENANCE = (
    "Provenance is mandatory. For every field you fill, also set <field>__src (where it comes from: "
    "'<file name> p.N' for a deck page, a URL, or 'computed: <how>') and <field>__status: SOURCE (read "
    "directly from a document, filing, registry or the founder), INFERRED (estimated or computed by you) "
    "or MISSING (not found; leave the value empty). Never invent a number: a value without a source is "
    "INFERRED at best, otherwise MISSING. A failed source is MISSING, never 'clear'."
)

ROW_RULE = (
    "Sheet row number: in the sheets_read_range result, values[0] is the header = sheet row 1, so the "
    "item at values[i] is sheet row i + 1."
)

RELIABLE = (
    "Tool order, most reliable first: (1) a registry or data tool that answers the question directly; "
    "(2) a KNOWN URL read with scrape_page (web_fetch if scrape_page fails): the company's own website, "
    "a register page, an article you already have; if a path returns 404 do not guess others, call "
    "scrape_links on the home page and follow real links; (3) RSS feeds and GDELT for news; (4) web_search "
    "ONLY to discover a URL you do not have yet, then open it with scrape_page and use what the page says, "
    "never the search snippet. A result that is not about the subject is ignored."
)

ONE_SHOT_SEND = (
    "Invoke gmail_send exactly ONCE with the finished email. A second gmail_send call is a failure. "
    "Your final reply MUST be the raw result text of gmail_send."
)

ANTI_STALL = (
    "Nobody will answer questions during this run: never ask one, never offer options, act on the "
    "defaults and finish this turn with the required output."
)

BRAND_THEME = (
    'Brand: use theme = "melaya" by default. If this run\'s inputs carry another brand (an attached logo '
    "image and/or brand colours or a company name in the brief), pass a theme dict instead: "
    '{"preset": "melaya", "accent": "<brand hex>", "heading": "<brand hex>", "font_head": "<font>", '
    '"font_body": "<font>", "logo": "<path of the attached logo from the Run inputs block>", '
    '"mark": "<same path or empty>", "footer": "<Brand name> | Confidential"} so the document carries '
    'that brand. Use "logo": "" and "mark": "" when there is no logo file, so no other logo appears.'
)

DESIGN = (
    "Every document you produce is DESIGNED, never plain text. For each document: invoke word_create with "
    'path = "<short-file-name>.docx", theme = the brand theme (see Brand), and blocks = a cover block '
    f'{{"type":"cover","title":"<title>","subtitle":"<what it is>","meta":"{CLIENT} | <today ISO date>"}} '
    'followed by {"type":"heading"} sections, {"type":"paragraph"} text, {"type":"bullets"} lists, '
    '{"type":"callout","bold":true} for verdicts and key numbers, and {"type":"table","rows":[[header...],[row...]]} '
    "for snapshots, scorecards and comparisons. Then publish it: invoke drive_upload with name = the document "
    'title, path = that .docx and mime_type = "application/vnd.google-apps.document". '
    'Report "DOC: <webViewLink>" and "FILE: <the .docx path>" for each document.\n' + BRAND_THEME
)

DOC_TOOLS = ["word_create", "drive_upload"]
LOOP_POLICY = {"mode": "observe_only", "evaluator": "default"}


def col_letter(name: str) -> str:
    """Column letter of a store column (A, B, ..., Z, AA, ...) from COLUMNS."""
    n = COLUMNS.index(name) + 1
    out = ""
    while n:
        n, r = divmod(n - 1, 26)
        out = chr(65 + r) + out
    return out


LAST_COL = col_letter(COLUMNS[-1])
FULL_RANGE = f"{TAB}!A1:{LAST_COL}5000"


# -- Factories ----------------------------------------------------------------
def agent(name, role, instruction, tools, hitl=(), model=None, sys_prompt=None, include_context=False):
    """One agent object, written in snake_case and camelCase (UI round-trip safe)."""
    m = dict(model or MODEL)
    a = {
        "name": name,
        "role": role,
        "instruction": instruction,
        "agent_tools": list(tools),
        "agentTools": list(tools),
        "human_approval_tools": list(hitl),
        "humanApprovalTools": list(hitl),
        "model_provider": m["provider"],
        "model_name": m["name"],
        "model": m,
        "include_context": include_context,
        "includeContext": include_context,
        "include_rag_tool": False,
        "includeRagTool": False,
        "loop_policy": dict(LOOP_POLICY),   # object only; a string is ignored
        "loopPolicy": dict(LOOP_POLICY),
    }
    if sys_prompt:
        a["system_prompt_override"] = sys_prompt
        a["systemPromptOverride"] = sys_prompt
    return a


def recorder(name="Deal Recorder"):
    """Find-or-create the store, read it to a file, dedupe, normalize to CSV, append by csv_path."""
    return agent(
        name, "Data store clerk",
        f"""The previous message holds new records as a JSON array under a line "RECORDS:". Save each one to the data store.

1. {FIND_STORE}
   If no sheet exists, create it: invoke dealdb_schema with tab = "{TAB}", then invoke sheets_create with title = "{STORE_NAME}", sheet_title = "{TAB}" and initial_values_json = the values_json returned by dealdb_schema. Use the new id as SHEET_ID.
2. Invoke sheets_read_range with spreadsheet_id = SHEET_ID, a1_range = "{FULL_RANGE}" and save_to = "store.json" (the existing rows go to that file; read data_rows from the summary).
3. For EACH record (one parallel batch): invoke dealdb_dedupe with rows = "store.json", new_company = the record's company and website = its website. If the best match has a score of 0.92 or more, skip the record and note "duplicate of <matched company>".
4. Invoke dealdb_normalize ONCE with rows = a JSON array of ALL the records that are not duplicates and save_to = "new_rows.csv". Then invoke sheets_append_row with spreadsheet_id = SHEET_ID, sheet = "{TAB}" and csv_path = "new_rows.csv". Never pass row_json and never build a row by hand.
5. Final reply, exactly this shape:
SHEET: https://docs.google.com/spreadsheets/d/<SHEET_ID>/edit
ADDED: <n>
- <company> (<stage>, <country>): <one line on what it does>
SKIPPED: <n>
- <company>: <reason>
Keep every line of the previous message that starts with "SUMMARY" or "CITE" below that, unchanged.
{ANTI_STALL}""",
        ["drive_search", "dealdb_schema", "sheets_create", "sheets_read_range", "dealdb_dedupe",
         "dealdb_normalize", "sheets_append_row"],
    )


def mailer(name, subject_hint, body_rules):
    """Last step: one gmail_send to the owner's address, FILE paths attached."""
    gate = () if UNGATED_OWNER_INBOX_DEMO else ("gmail_send",)
    return agent(
        name, "Team correspondent",
        f"""The previous message is the finished work of this run. Send it to the team.
1. Invoke gmail_my_address and send to that address.
2. Compose the email from the previous message only. Do not add facts.
   subject = {subject_hint}
   body = {body_rules}
   {WRITING_RULES}
   attachments = every path listed on a "FILE:" line of the previous message (none if there is no FILE line).
3. {ONE_SHOT_SEND}
`subject` and `body` are TWO DIFFERENT fields. Never copy body text into the subject.
If the previous message reports a tool failure or a service that is not connected, send a short body naming the service and asking to reconnect it under Connectors, never a raw error or JSON.""",
        ["gmail_my_address", "gmail_send"], hitl=gate,
    )


def pipeline(slug, name, description, steps, *, persistent_memory=False, schedule="", inputs=None):
    """Build steps[] (agents embedded) in run order and write cfg/<slug>.json and .min.json.

    edges stays EMPTY: steps then run in list order and every step kind compiles. Non-empty
    edges switch to graph routing, which skips condition and loop steps.

    A step is an agent dict (kind "agent"), a list of agent dicts (kind "parallel", concat join),
    or a ready step dict with its own "kind" (e.g. a decide step)."""
    step_list = []
    for i, st in enumerate(steps, 1):
        sid = f"s{i}"
        if isinstance(st, list):
            step_list.append({"id": sid, "kind": "parallel", "label": "parallel", "agents": st,
                              "joinStrategy": "concat"})
        elif "kind" in st:
            step_list.append({"id": sid, **st})
        else:
            step_list.append({"id": sid, "kind": "agent", "label": st["name"], "agent": st})
    cfg = {
        "name": name,
        "display_name": name,
        "description": description,
        "project": PROJECT,
        "tools": [],                      # keep EMPTY: an agent with no agent_tools would inherit this pool
        "steps": step_list,
        "edges": [],                      # linear order; see the docstring
        "schedule": schedule,             # arm with melaya_pipeline_schedule action "set"
        "hitl_mode": "safe",              # only "safe" honours human_approval_tools
        "persistent_memory": persistent_memory,
        "user_lang": "en",
        "model_provider": MODEL["provider"],
        "model_name": MODEL["name"],
    }
    if inputs:
        cfg["inputs"] = inputs
    (OUT / f"{slug}.json").write_text(json.dumps(cfg, ensure_ascii=True, indent=1), encoding="utf-8")
    (OUT / f"{slug}.min.json").write_text(json.dumps(cfg, ensure_ascii=True, separators=(",", ":")), encoding="utf-8")
    return cfg


# ============================================================================
# 1. Sourcing: feeds -> free triage -> confirm -> record
# ============================================================================
TRIAGE_Q = {
    "startup_round": {"type": "noul", "instructions": "This headline announces that an operating startup raised a funding round (not a fund, public company, price move, event or opinion piece)."},
    "thesis_fit": {"type": "score", "instructions": "Fit with the thesis: " + THESIS, "criteria": ["none", "weak", "partial", "good", "core"]},
}

pipeline(
    "p1_sourcing", f"{CLIENT} - Deal Sourcing",
    "Finds real startups that announced a funding round in the last 30 days and fit the thesis, from "
    "keyless funding-news feeds, confirms each from the source article, and records them with provenance "
    "in the deal data store (deduplicated, normalized). Run it with a brief to steer the theme, region or count.",
    [
        agent(
            "Deal Scout", "Venture sourcing analyst",
            f"""{THESIS}

Goal: find REAL startups that announced a funding round in the last 30 days and fit the thesis. If this run has a brief, it overrides the theme, region, stage or number of companies (TARGET). Default TARGET: 15 companies.

HARD BUDGET: at most 40 tool calls in total. Always call tools in parallel batches (all calls of a phase in ONE turn). Stop searching the moment you have TARGET companies.

Phase 1, candidates (ONE parallel batch): invoke rss_search_entries with keyword = "rais" and limit = 30 on each of these feeds: {", ".join(FEEDS)}. Keep entries from the last 30 days.
Phase 1b, triage (ONE call, free): invoke decide_batch with items = a JSON array with one string per kept headline "<title> | <published date> | <link>", questions = {json.dumps(TRIAGE_Q)}, top_by = "thesis_fit" and top_k = the number of items. Candidates = rows with startup_round above 0.5, ordered by thesis_fit.
Phase 2, confirm (ONE parallel batch): for the best TARGET candidates, invoke scrape_page on the article link to read amount, investors, stage, country and website. If an article is unreadable, use the headline facts and mark those fields INFERRED; do not search again for it.
Only if Phase 1 gave fewer candidates than TARGET: ONE parallel batch of web_search with news = true and timelimit = "m", then confirm as in Phase 2.
Do not use any other source in this run.

For each company collect: company, website, country (ISO-2), sector, stage, round_type, round_size_usd, investors, traction_notes and the source URL.
{PROVENANCE}
Set status = "sourced" and updated_at = today's ISO date from the date context block.

Final reply, ALWAYS in exactly this format, even when you found few or no companies (then RECORDS: [] and say why in SUMMARY):
RECORDS: <a JSON array of objects on ONE line, keys = the field names above plus <field>__src and <field>__status>
SUMMARY: <n> companies from <sources used>; <which sources failed, if any>
CITE: <company> - <source URL> (one CITE line per company)
Never answer with a narrative report instead of RECORDS. {ANTI_STALL}""",
            ["rss_search_entries", "decide_batch", "scrape_page", "web_search"],
        ),
        recorder(),
    ],
    persistent_memory=False,  # the data store is the memory
)

# ============================================================================
# 2. Screening: store -> file -> decide_batch_file -> write-back -> designed shortlist -> mail
# ============================================================================
SCREEN_Q = {
    "market": {"type": "score", "instructions": "Market attractiveness: size, growth and timing.", "criteria": ["weak", "below average", "average", "strong", "exceptional"]},
    "team": {"type": "score", "instructions": "Team quality: relevant founder experience and completeness.", "criteria": ["weak", "below average", "average", "strong", "exceptional"]},
    "strategic_fit": {"type": "score", "instructions": "Fit with the thesis: " + THESIS, "criteria": ["none", "weak", "partial", "good", "core"]},
}
S, SC, U = col_letter("status"), col_letter("score"), col_letter("updated_at")  # must be contiguous for one write

pipeline(
    "p2_screening", f"{CLIENT} - Screening",
    "Scores every sourced opportunity in the data store with the free System One scorer, ranks them, writes "
    "the status and score back to the store, publishes a designed shortlist spreadsheet and emails the "
    "shortlist to the team for review.",
    [
        agent(
            "Screening Scorer", "Screening analyst",
            f"""{THESIS}

1. {FIND_STORE}
2. Invoke sheets_read_range with spreadsheet_id = SHEET_ID, a1_range = "{FULL_RANGE}" and save_to = "store.json". Read data_rows from the summary; never copy the table yourself.
3. Invoke decide_batch_file with items_path = "store.json", include_if = "status=sourced,screened", item_template = "{{company}} | {{website}} | {{country}} | {{sector}} | {{stage}}", questions = {json.dumps(SCREEN_Q)}, top_by = "strategic_fit" and top_k = 500. Each item comes back as "[row N] ..."; N is its sheet row.
4. If the brief restricts the set (a sector, a region), keep only matching rows.
5. Composite score 0 to 10 = average of the scores mapped to 0-10 (level index / (levels - 1) * 10), strategic_fit weighted double.
6. For EVERY scored company invoke sheets_update_range with spreadsheet_id = SHEET_ID, a1_range = "{TAB}!{S}<row>:{U}<row>" and values_json = [["screened", <composite score>, "<today ISO date>"]] (one parallel batch).
7. Final reply, exactly:
SHEET: https://docs.google.com/spreadsheets/d/<SHEET_ID>/edit
SCREENED: <n>
TOP: a JSON array of {{"rank","company","score","market","team","strategic_fit"}} for the top 25. {ANTI_STALL}""",
            ["drive_search", "sheets_read_range", "decide_batch_file", "sheets_update_range"],
        ),
        agent(
            "Shortlist Writer", "Investment associate",
            f"""The previous message holds the ranked TOP list and the SHEET line.
{WRITING_RULES}
Invoke excel_write_data with path = "shortlist.xlsx", sheet = "Top 25", start_cell = "A1", theme = "melaya" (or {{"preset":"melaya","header":"<brand hex>","accent":"<brand hex>"}} for another brand from the run inputs), title = "{CLIENT} - Screening Shortlist - <YYYY-MM>" and data = a 2-D array: header ["rank","company","score","market","team","strategic_fit"] then one row per TOP company.
Then invoke drive_upload with name = "{CLIENT} - Screening Shortlist - <YYYY-MM>", path = "shortlist.xlsx" and mime_type = "application/vnd.google-apps.spreadsheet".
Final reply: "SHORTLIST: <webViewLink>", "FILE: shortlist.xlsx", then one line per top-10 company "<rank>. <company> (<score>/10): <one-line why>", then the SHEET line.""",
            ["excel_write_data", "drive_upload"],
        ),
        mailer(
            "Screening Mailer", f'"{CLIENT} screening: top 10 (<YYYY-MM>)"',
            "a one-line intro with how many opportunities were screened, the top 10 as a numbered list "
            "(company, score, one-line why), then the shortlist link and the data store link.",
        ),
    ],
)

# ============================================================================
# 3. Due diligence: resolver -> parallel analysts -> designed findings -> mail
# ============================================================================
COPY_FORWARD = (
    "Start your reply with the TARGET, SHEET and ROW lines of the previous message copied unchanged "
    "(the next step only sees your reply)."
)
CHECK_FORMAT = (
    "Report each check as: CHECK <name>: <result> | status SOURCE/INFERRED/MISSING | src <registry id or URL>. "
    "A failed or unavailable source is MISSING, never 'clear'."
)

pipeline(
    "p3_due_diligence", f"{CLIENT} - Due Diligence",
    "Keyless due-diligence sweep on one company named in the run brief (or the top screened row): corporate "
    "identity, sanctions screening of the company and founders, licensing, and traction signals, in parallel. "
    "Writes a designed findings document that tags every finding SOURCE / INFERRED / MISSING, records it in "
    "the data store and emails it for review.",
    [
        agent(
            "Target Resolver", "Deal team coordinator",
            f"""The company to check: the one named in this run's brief. If there is no brief, {FIND_STORE} then sheets_read_range a1_range = "{FULL_RANGE}" and take the highest-score row whose status is "screened".
Read the company's own website with scrape_page (home page first, then real links found with scrape_links) for the legal entity name, founders, country of incorporation and GitHub organisation. Use web_search only to find the website when you do not have it.
Always locate the company in the data store too. {ROW_RULE}
{RELIABLE}
Final reply, ALWAYS exactly this, even when fields are unknown (never refuse, never a narrative):
TARGET: <company> | website: <domain> | legal name: <name or unknown> | country: <ISO-2 or unknown> | founders: <names or unknown> | github: <org or none>
SHEET: <sheet URL or none>
ROW: <sheet row number or none>""",
            ["drive_search", "sheets_read_range", "scrape_page", "scrape_links", "web_fetch", "web_search"],
        ),
        [
            agent(
                "Corporate and Sanctions", "KYB and sanctions analyst",
                f"""Use the TARGET line of the previous message. {COPY_FORWARD}
1. gleif_search_entities with name = the legal name (or company name); if an LEI is found, gleif_get_lei.
2. sanctions_screen_batch with names = the company, the legal name and every founder, comma separated. Quote overall_verdict exactly; potential_match means escalate to a human.
{CHECK_FORMAT}""",
                ["gleif_search_entities", "gleif_get_lei", "sanctions_screen_batch"],
            ),
            agent(
                "Traction and Technology", "Technical and traction analyst",
                f"""Use the TARGET line of the previous message. {COPY_FORWARD}
1. webtraffic_traction_report with domain = the domain.
2. If a GitHub org is known: github_developer_scorecard with org_or_user = that org.
3. gdelt_search_articles with query = the company name in quotes, timespan = "3m", max_results = 20.
{CHECK_FORMAT} Never infer "no traction" from a failed source.""",
                ["webtraffic_traction_report", "github_developer_scorecard", "gdelt_search_articles"],
            ),
        ],
        agent(
            "DD Writer", "Due-diligence lead",
            f"""The previous message holds the TARGET line and every CHECK line.
{WRITING_RULES}
{DESIGN}

1. Produce ONE designed document titled "{CLIENT} - DD Findings - <company> - <today ISO date>" (verdict in a callout, each area as a table Check | Result | Status | Source): summary verdict (proceed / proceed with conditions / escalate) in 3 sentences, red flags or "none found", corporate identity, sanctions, traction and technology, missing information requiring human validation. Every finding keeps its tag [SOURCE: <src>] / [INFERRED: <how>] / [MISSING]. Never add a fact that is not in the previous message.
2. If a ROW line gives a number and a SHEET line gives the sheet: invoke sheets_update_range with spreadsheet_id = the id in the SHEET URL, a1_range = "{TAB}!{col_letter('dd_findings')}<row>" and values_json = [["<DOC link>"]], then a1_range = "{TAB}!{col_letter('status')}<row>" and values_json = [["diligence"]].
3. Final reply: the DOC and FILE lines, the summary verdict, the red flags, then the SHEET line if present.""",
            DOC_TOOLS + ["sheets_update_range"],
        ),
        mailer(
            "DD Mailer", f'"{CLIENT} DD findings: <company> (<verdict>)"',
            "the summary verdict, the red flags, the 5 most important findings with their tags, the missing "
            "items that need a human, and the findings document link.",
        ),
    ],
    inputs=[{"key": "company", "label": "Company", "type": "text", "required": False}],
)

print(sorted(p.name for p in OUT.glob("*.json")))
