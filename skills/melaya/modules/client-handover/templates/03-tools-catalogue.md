---
title: Tools catalogue
type: reference
client: <Client>
updated: <YYYY-MM-DD>
tags: [<client-tag>, tools, sources, eeat]
related:
  - "[[00 Overview]]"
  - "[[01 How a Pipeline Works (ELI5)]]"
  - "[[04 <Data Store> and Provenance]]"
---

# Tools catalogue

> [!info] In one sentence
> A tool is a precise function an agent is allowed to call. The <N> pipelines use <T> tools. <K> read free public sources or compute locally, with no account needed. <C> act inside the connected <workspace> through its connector.

Generate this note from the union of every `agent_tools` list in the configs. A tool not in any config does not appear here.

Cost legend:
- **Keyless** = free public source, no account or key.
- **Local** = computed by Melaya on the data passed in, no outside call.
- **Free (Melaya)** = Melaya-hosted engine, no per-call charge.
- **Connector** = runs in your connected account after you connect it once.
- **Paid** = billed per call by a third party (name it).

```mermaid
pie showData
    title The <T> tools by category
    "<Category 1>" : <n1>
    "<Category 2>" : <n2>
    "<Category 3>" : <n3>
```

## 1. <Category 1>

| Tool | What it does (ELI5) | Source behind it | Cost | Used by |
|---|---|---|---|---|
| `<tool_name>` | <one plain sentence> | <named public source or system, coverage, refresh cadence> | <Keyless / Local / Free (Melaya) / Connector / Paid> | <P0, P2> |

## 2. <Category 2>

| Tool | What it does (ELI5) | Source behind it | Cost | Used by |
|---|---|---|---|---|
| `<tool_name>` | <...> | <...> | <...> | <...> |

<Repeat one section per category. Common categories: news and discovery, registries, regulatory and sanctions, traction and technology, documents and OCR, data store, scoring, workspace, document design, email.>

## Known limits of the sources

| Source | Limit | How the pipelines handle it |
|---|---|---|
| <source> | <coverage, lag, rate limit, what absence does and does not mean> | <recorded as MISSING / INFERRED / escalated to a human> |

> [!warning] Slow or failed sources show as MISSING, never as a guess
> When a source does not answer in time, that check is recorded as MISSING with the source named, and the run continues. It is never replaced by an estimate, and a failed check is never read as a clean result.

## Related

[[00 Overview]] | [[01 How a Pipeline Works (ELI5)]] | [[04 <Data Store> and Provenance]] | [[05 Branded Documents]] | [[06 Security, Human Control and Compliance]]
