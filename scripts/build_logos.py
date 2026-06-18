"""Copy provider + exchange logos into the repo and render logo grids.

Sources (the Melaya app's own brand assets):
  C:\\Github\\melaya-platform\\client\\src\\assets\\connectors  (AI provider logos)
  C:\\Github\\melaya-platform\\client\\src\\assets\\exchanges    (venue logos)

Outputs:
  assets/providers/*  + fills <!-- PROVIDERS_GRID --> in README.md
  assets/exchanges/*  + fills <!-- EXCHANGES_GRID --> in docs/exchanges.md
"""
import json
import os
import re
import shutil

SRC_CONN = r"C:\Github\melaya-platform\client\src\assets\connectors"
SRC_EXCH = r"C:\Github\melaya-platform\client\src\assets\exchanges"
ROOT = r"C:\Users\tokaNo\melaya-oss"

# ── Providers (display name, source filename) — all 19 we list ──────────────
PROVIDERS = [
    ("OpenAI", "openai.svg"), ("Anthropic", "anthropic.png"), ("Google Gemini", "gemini.png"),
    ("NVIDIA", "nvidia.png"), ("Mistral", "mistral.png"), ("DeepSeek", "deepseek.png"),
    ("xAI (Grok)", "grok.png"), ("Moonshot (Kimi)", "moonshot.png"), ("Zhipu", "zhipu.png"),
    ("Qwen", "qwen.png"), ("Cerebras", "cerebras.png"), ("Groq", "groq.png"),
    ("SambaNova", "sambanova.png"), ("Upstage", "upstage.png"), ("Reka", "reka.png"),
    ("MiniMax", "minimax.png"), ("OpenRouter", "openrouter.png"), ("Ollama", "ollama.png"),
    ("LM Studio", "lmstudio.png"),
]

# ── Exchange logo resolution ────────────────────────────────────────────────
PARENT = {"binanceusdm": "binance", "bingxfutures": "bingx", "bitgetfutures": "bitget",
          "bybitlinear": "bybit", "okxswap": "okx"}
SPECIAL = {"drift_pm": "drift"}


def norm(s):
    return re.sub(r"[^a-z0-9]", "", s.lower())


def copy(src_dir, fname, dst_dir):
    os.makedirs(dst_dir, exist_ok=True)
    shutil.copy2(os.path.join(src_dir, fname), os.path.join(dst_dir, fname))


def fill_placeholder(path, marker, html):
    with open(path, encoding="utf-8") as f:
        text = f.read()
    text = text.replace(marker, html)
    with open(path, "w", encoding="utf-8") as f:
        f.write(text)


# ── Providers grid ──────────────────────────────────────────────────────────
prov_dst = os.path.join(ROOT, "assets", "providers")
cells = []
for name, fname in PROVIDERS:
    copy(SRC_CONN, fname, prov_dst)
    cells.append((name, fname))

COLS = 5
rows = []
for i in range(0, len(cells), COLS):
    chunk = cells[i:i + COLS]
    tds = "".join(
        f'<td align="center" width="110"><img src="./assets/providers/{fn}" height="38" alt="{nm}"/><br/><sub>{nm}</sub></td>'
        for nm, fn in chunk
    )
    rows.append(f"  <tr>{tds}</tr>")
prov_html = '<table>\n' + "\n".join(rows) + "\n</table>"
fill_placeholder(os.path.join(ROOT, "README.md"), "<!-- PROVIDERS_GRID -->", prov_html)
print(f"providers: copied {len(cells)} logos")

# ── Exchange grid ───────────────────────────────────────────────────────────
venues = json.load(open(os.path.join(ROOT, "data", "exchanges.json"), encoding="utf-8"))
index = {}
for f in os.listdir(SRC_EXCH):
    stem = os.path.splitext(f)[0]
    index.setdefault(norm(stem), f)

exch_dst = os.path.join(ROOT, "assets", "exchanges")
imgs, missing = [], []
for v in venues:
    vid = v["id"]
    fname = None
    for cand in (vid, SPECIAL.get(vid), PARENT.get(vid)):
        if cand and norm(cand) in index:
            fname = index[norm(cand)]
            break
    if not fname:
        missing.append(vid)
        continue
    copy(SRC_EXCH, fname, exch_dst)
    imgs.append(f'<img src="../assets/exchanges/{fname}" height="30" alt="{v["name"]}" title="{v["name"]}"/>')

exch_html = '<p align="center">\n  ' + "\n  ".join(imgs) + "\n</p>"
fill_placeholder(os.path.join(ROOT, "docs", "exchanges.md"), "<!-- EXCHANGES_GRID -->", exch_html)
print(f"exchanges: copied {len(imgs)} logos; missing={missing}")
