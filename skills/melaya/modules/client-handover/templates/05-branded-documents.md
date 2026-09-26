---
title: Branded documents
type: reference
client: <Client>
updated: <YYYY-MM-DD>
tags: [<client-tag>, documents, branding]
related:
  - "[[00 Overview]]"
  - "[[02 Run Inputs, Schedules and Triggers]]"
  - "[[03 Tools Catalogue]]"
---

# Branded documents

> [!info] In one sentence
> Every document the system produces is designed like a professional deliverable (cover, headings, verdict callouts, tables, logo and footer), in <default brand> by default, and can carry <Client>'s own brand by attaching a logo and naming the colours in the run brief.

## 1. What gets produced

| Pipeline | Document (name pattern) | Format where it lands |
|---|---|---|
| [[P<n> <Pipeline>]] | "<Client> - <Document> - <item> - <date>" | <Google Doc / Google Sheet / .docx / .xlsx / .pptx / .pdf> |

<How the file is built and published, taken from the writer instructions: for example built with word_create, published with drive_upload as a native Google Doc; the same file attached to the email.>

<Formats the brand system supports but no pipeline produces today: say so plainly.>

## 2. Anatomy of a designed document

| Block | Used for |
|---|---|
| Cover | Title, what it is, "<Client>, date" |
| Headings | One per section required by the pipeline |
| Callout | Verdicts and key numbers, provenance counts |
| Tables | <the real table shapes, for example Check / Result / Status / Source> |
| Bullets | Strengths, weaknesses, open questions |
| Header and footer | Logo on every page, confidentiality footer |

## 3. The default brand

| Element | Value |
|---|---|
| Font | <...> |
| Accent | <...> |
| Headings | <...> |
| Header / cover | <...> |
| Footer | <...> |

## 4. Re-branding to <Client>

A run switches brand when its inputs carry one: attach the logo and give colours, fonts or name in the brief. The writer then passes a **theme** instead of the default.

### Theme keys for documents

| Key | What it controls | Example |
|---|---|---|
| `preset` | Starting point | `<preset>` |
| `accent` | Dividers, callouts, table accents | `#<hex>` |
| `heading` | Heading colour | `#<hex>` |
| `text`, `muted` | Body and secondary text | `#<hex>` |
| `font_head`, `font_body` | Fonts | `<font>` |
| `logo`, `mark` | Header image, cover image | path of the attached logo from the Run inputs |
| `footer` | Footer line | `<Client>, Confidential` |

Verify the key list against the current `word_create`, `excel_write_data`, `pptx_create` and `pdf_from_html` tool descriptions before publishing; list only keys those tools accept.

### Example

Brief: *"<brief naming colours, font and footer>"* plus `<logo file>` attached.

```json
{
  "preset": "<preset>",
  "accent": "#<hex>",
  "heading": "#<hex>",
  "font_head": "<font>",
  "font_body": "<font>",
  "logo": "<path of the logo from the Run inputs block>",
  "footer": "<Client> | Confidential"
}
```

> [!tip] Colours here are placeholders
> The values above are illustrations until <Client> shares official brand values. In production the theme can be fixed in the pipeline so no brief is needed.

## Related

[[00 Overview]] | [[02 Run Inputs, Schedules and Triggers]] | [[03 Tools Catalogue]] | [[06 Security, Human Control and Compliance]]
