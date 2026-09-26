# Keyless public-data tool catalogue

Tools a Melaya pipeline agent can use with NO client data and NO connector. Use them to prove value on day one. Ids are pipeline registry ids (put them in `agent_tools`). Always re-confirm an id with `melaya_pipeline_registry` before saving a config: the registry is the source of truth and this list can drift.

Access legend:
- `keyless`: works with nothing configured.
- `keyless*`: works keyless at a low public rate limit; an optional free key raises it.
- `free key`: needs a free self-serve key the user stores in Melaya Connectors (not keyless; listed so you know the nearest option).
- `platform`: runs on Melaya infrastructure, no user key, free to the user.
- `local`: pure computation on data the run already has (files, rows).

Callability: all tools below run inside pipeline runs. Only the `melaya_core` family (`web_search`, `web_fetch`, `scrape_page`, `scrape_links`, `fetch_rss`, ...) can also be called from the conversation with `melaya_connector_call`. Validate the rest with a short validation run.

## 1. Company registries and KYB

| Tool | Access | What it proves |
|---|---|---|
| `gleif_search_entities`, `gleif_fulltext_search`, `gleif_autocomplete` | keyless | the legal entity exists (global LEI register), its legal name and status |
| `gleif_get_lei`, `gleif_validate_lei` | keyless | full LEI record; LEI check digits valid (offline) |
| `gleif_direct_parent`, `gleif_ultimate_parent`, `gleif_direct_children`, `gleif_ultimate_children` | keyless | group structure: who owns the entity, what it owns |
| `gleif_by_registration_id`, `gleif_lookup_by_isin`, `gleif_lookup_by_bic` | keyless | resolves a national company number, ISIN or BIC to the entity |
| `gleif_recent_registrations` | keyless | newly registered entities in a country (sourcing poll) |
| `annuaire_search`, `annuaire_search_filtered`, `annuaire_get_company` | keyless | French company identity, activity code, size band, status |
| `annuaire_dirigeants`, `annuaire_search_by_person` | keyless | French executives; every company a person runs |
| `annuaire_finances` | keyless | French yearly revenue and net result when published |
| `annuaire_new_companies` | keyless | newly created French companies by filter (sourcing) |
| `brreg_search_entities`, `brreg_get_entity`, `brreg_roles` | keyless | Norwegian company profile, CEO, board, auditor |
| `brreg_new_companies`, `brreg_entity_updates`, `brreg_role_updates` | keyless | new Norwegian companies and change feeds (sourcing, monitoring) |
| `acra_search_entities`, `acra_entity`, `acra_entity_status` | keyless | Singapore entity profile, status, officers |
| `acra_entities_at_address`, `acra_kyb_report` | keyless | shared/nominee address flags; KYB scorecard |
| `vies_check_vat`, `vies_approximate_match`, `vies_check_vat_batch` | keyless | EU VAT number valid; claimed name/address matches the national register |
| `vies_validate_format` | local | VAT format check without calling VIES |
| `companies_house_search_companies`, `companies_house_officers`, `companies_house_pscs`, `companies_house_kyb_report` | free key | UK company, officers, persons with significant control, KYB report |

## 2. Sanctions and financial-crime screening

| Tool | Access | What it proves |
|---|---|---|
| `sanctions_screen` | keyless | one person or company screened against OFAC SDN, UN, EU and UK lists, with a verdict |
| `sanctions_screen_batch` | keyless | up to 25 names (founders, directors, investors, company) in one call |
| `sanctions_list_status` | keyless | publication date and load status of each list (cite it next to a "clear" verdict) |
| `sanctions_entity` | keyless | full record of a listed party (for a hit review) |
| `ofac_screen_name`, `ofac_consolidated_screen`, `ofac_recent_changes` | keyless | OFAC-only screening and recent list changes |
| `ofac_screen_crypto_address` | keyless | a wallet address is on an OFAC list |
| `goplus_address_security`, `goplus_screen_addresses` | keyless | wallet/contract flagged for sanctions, mixer, theft, phishing |

Rule: a screening tool that errors is `MISSING`, never "clear".

## 3. Regulatory registers

| Tool | Access | What it proves |
|---|---|---|
| `mica_check` | keyless | verdict against the ESMA MiCA register: authorised CASP, withdrawn, non-compliant listed, not found |
| `mica_entity`, `mica_search_casps` | keyless | services and passports of an authorised crypto-asset service provider |
| `mica_non_compliant` | keyless | entity or website on ESMA's non-compliant list |
| `mica_white_papers` | keyless | token white papers notified under MiCA |
| `vara_register_check` | keyless | Dubai VARA virtual-asset register (licensed, in-principle approval only, withdrawn, absent) plus VARA enforcement and warning notices. Any notice is a red flag. "Not found" is INFERRED, never proof of unlicensed activity |
| `adgm_fsra_check` | keyless | Abu Dhabi (ADGM FSRA) register: official name, status, regulated activities, regulatory actions |
| `dfsa_register_check` | keyless | DIFC (DFSA) register: status, licence dates, crypto token permissions, restrictions, regulatory actions |
| `fca_firm_check`, `fca_warning_check`, `fca_firm_permissions` | free key | UK FCA authorisation, permissions, warning list hits |

## 4. News, feeds and public signals

| Tool | Access | What it proves |
|---|---|---|
| `rss_search_entries` | keyless | feed entries matching a keyword. Targeted Bing News RSS (`https://www.bing.com/news/search?format=rss&q=<query>`) is a reliable keyless feed |
| `rss_fetch_multiple`, `rss_get_entries`, `fetch_rss` | keyless | many feeds merged in one call (batch the source list) |
| `gdelt_search_articles`, `gdelt_event_timeline`, `gdelt_tonechart` | keyless | global news coverage, volume over time, tone |
| `hn_search`, `hn_get_thread`, `hn_top_stories` | keyless | developer community attention |
| `ground_search`, `ground_story_coverage` | keyless | coverage breadth and bias breakdown of a story |
| `wiki_get_summary`, `wiki_pageviews`, `wikidata_query` | keyless | encyclopedic facts; attention trend |
| `web_search` | keyless | DISCOVERS URLs only (tries the agent's own provider search, then news RSS, then HTML engines). Open the page with `scrape_page` before citing |
| `scrape_page`, `scrape_links`, `scrape_table`, `scrape_structured`, `web_fetch` | keyless | content of a KNOWN URL. Use `scrape_links` on the home page instead of guessing paths (missing paths return "HTTP 404 page not found") |

## 5. Filings and capital raises

| Tool | Access | What it proves |
|---|---|---|
| `edgar_form_d_search`, `edgar_form_d` | keyless | US private placements: issuer, offering size, amount sold, investors, related persons |
| `edgar_full_text_search` | keyless | a name or term appears in SEC filings |
| `edgar_ticker_to_cik`, `edgar_recent_filings`, `edgar_filing_index` | keyless | public-company filings and exhibits |
| `edgar_company_facts`, `edgar_company_concept`, `edgar_frames` | keyless | reported XBRL financials, time series, cross-company snapshot (peer benchmarks) |
| `edgar_insider_form4` | keyless | insider transactions |

## 6. On-chain and DeFi

| Tool | Access | What it proves |
|---|---|---|
| `defillama_find_protocol` | keyless | the DefiLlama slug of a project from its name, ticker or website domain. Call it FIRST instead of guessing a slug |
| `defillama_protocol_detail`, `defillama_tvl`, `defillama_protocols` | keyless | protocol TVL, chain split, audits, recorded raises and hacks |
| `defillama_protocol_fees`, `defillama_fees_overview`, `defillama_valuation_multiples` | keyless | fees/revenue run-rate and trend; P/F, P/S multiples |
| `defillama_stablecoins`, `defillama_depeg_monitor`, `defillama_yield_pools` | keyless | stablecoin supply, depegs, yield screening |
| `blockscout_contract_risk`, `blockscout_holder_concentration`, `blockscout_address_info` | keyless | contract control risk (EOA vs multisig/timelock), holder concentration, address profile |
| `goplus_token_risk_scorecard`, `goplus_token_security`, `goplus_rugpull_detect` | keyless | token honeypot/tax/mint/owner risks; rug-pull powers |
| `sourcify_verification_report`, `sourcify_contract` | keyless | contract source verified; proxy and compiler risk |
| `geckoterminal_token_info`, `geckoterminal_exit_liquidity`, `geckoterminal_new_pools` | keyless | token DEX market, exit liquidity for a position size, new pools (sourcing) |
| `dex_search`, `dex_pairs_by_token` | keyless | DEX pairs and liquidity |
| `snapshot_governance_concentration`, `snapshot_proposals` | keyless | governance activity and voting concentration |
| `growthepie_l2_scorecard`, `growthepie_chain_economics` | keyless | L2 fundamentals, fees, profitability |
| `hlinfo_vault_scorecard`, `hlinfo_perp_markets` | keyless | perp venue vault due diligence, market data |
| `alternativeme_sentiment_regime`, `alternativeme_fear_greed_current` | keyless | market sentiment regime |
| `safe_multisig_health`, `safe_info` | keyless* | multisig owners, threshold, health flags (exploration limits without a key) |

## 7. Code and developer traction

| Tool | Access | What it proves |
|---|---|---|
| `github_developer_scorecard` | keyless* | 0-100 developer-activity score with trend, bus factor, recency, red flags |
| `github_org_overview` | keyless* | public footprint: repos, stars, forks, languages, last push |
| `github_repo_activity` | keyless* | one repo's commit velocity and contributor concentration |
| `npm_package_info`, `npm_downloads`, `pypi_package_info`, `pypi_downloads`, `crates_package_info` | keyless | package adoption |
| `osv_query_package`, `osv_batch_query` | keyless | known vulnerabilities in dependencies |

keyless* for GitHub: 60 anonymous requests per hour; a scorecard costs about 14. Batch sparingly or connect GitHub.

## 8. Web traction and domain intelligence

| Tool | Access | What it proves |
|---|---|---|
| `webtraffic_traction_report` | keyless* | decision-ready scorecard: link-authority band, CrUX presence, site age, activity |
| `webtraffic_majestic_rank`, `webtraffic_wayback` | keyless | link authority rank; first capture date and activity by year |
| `rdap_domain_age`, `rdap_domain`, `rdap_bulk_domains` | keyless | domain registration date (company age signal), registrar, status |
| `crtsh_subdomains`, `crtsh_search_org` | keyless | product surface (subdomains), certificates issued to an organisation |
| `wayback_history`, `wayback_calendar` | keyless | how a site changed over time |
| `dns_lookup`, `dns_bulk` | keyless | mail/hosting setup (MX, TXT) |
| `pagespeed_core_web_vitals` | keyless* | site quality signal |

## 9. Investors and startup directories

| Tool | Access | What it proves |
|---|---|---|
| `openvc_search_investors`, `openvc_get_fund` | keyless | investors matching sector, stage, geography, check size; fund thesis |
| `thesaasdir_search_companies`, `thesaasdir_recent_companies`, `thesaasdir_company_details` | keyless | SaaS products by sector and recency (sourcing) |

## 10. Research and macro context

| Tool | Access | What it proves |
|---|---|---|
| `openalex_search_works`, `openalex_search_authors` | keyless | scientific depth of a founder or technology |
| `wb_indicator`, `wb_country_batch` | keyless | country macro indicators |
| `treasury_exchange_rates` | keyless | official FX rates (normalising money fields) |
| `uspto_patent_search`, `uspto_ip_report` | free key | patents by assignee or inventor |
| `fred_series_observations` | free key | US macro series |

## 11. Documents and OCR (run inputs)

| Tool | Access | What it proves |
|---|---|---|
| `read_run_input` | platform | reads a file attached to the run (added automatically when a run has files; not in the registry) |
| `deck_extract` | local | per-page text of a PDF/PPTX pitch deck, OCR on image-only pages, page numbers for "deck p.N" citations. It already OCRs image-only slides: never OCR the deck again in parallel (that exhausted a small run container's memory); only re-OCR the pages it lists as failed, one at a time |
| `pdf_to_text`, `pdf_extract_tables`, `pdf_render_pages` | local | text and tables from PDFs; page images for OCR or a vision model |
| `ocr_image` | local | text from an image |
| `excel_read_sheet`, `word_read`, `pptx_read` | local | content of attached Office files |

## 12. Deal database and scoring

| Tool | Access | What it proves / does |
|---|---|---|
| `dealdb_schema` | local | canonical deal-database columns with `__src` / `__status` provenance and a header row |
| `dealdb_dedupe` | local | a new company already exists in the database (domain first, then name) |
| `dealdb_normalize` | local | money/FX, stage, country, sector normalised; can save CSV for appending |
| `dealdb_benchmark`, `dealdb_rank` | local | peer percentiles and outliers; shortlist by score |
| `decide`, `decide_check` | platform | typed judgement on one item (free, self-hosted) |
| `decide_batch`, `decide_batch_file` | platform | scores/classifies MANY items in one free call; `_file` reads items from a saved JSON file |
| `jev_batch`, `jev_batch_file`, `jev_score` | connector | paid hosted twin of decide with the same schema |

## 13. Output (needs connectors or produces local files)

| Tool | Access | Output |
|---|---|---|
| `sheets_create`, `sheets_read_range`, `sheets_append_row`, `sheets_update_range` | connector (google_sheets) | the data spine (`sheets_create` takes a `folder`) |
| `sheets_update_by_header`, `sheets_delete_rows` | connector (google_sheets) | write back by column NAME (many rows in one call); delete rows without leaving a blank gap |
| `drive_search`, `drive_upload`, `drive_share` | connector (google_drive) | find-or-create; publish a local file as a Google Doc/Sheet (`drive_upload` takes a `folder`) |
| `drive_create_folder`, `drive_move` | connector (google_drive) | find-or-create a folder by name path ("Client Pilot/Memos"); file an existing document into it |
| `docs_create`, `docs_read` | connector (google_docs) | native Google Docs; `docs_read` returns tables as markdown rows |
| `gmail_my_address`, `gmail_send` | connector (gmail) | one mailer step at the end |
| `word_create`, `pptx_create`, `excel_write_data`, `pdf_from_html` | local | designed, themed documents (attach file paths in the mailer) |

## Quick picks by requirement

| Requirement | First choice |
|---|---|
| "Is this company real and in good standing?" | national register tool (annuaire / brreg / acra / companies_house) + `gleif_search_entities` + `vies_check_vat` |
| "Are the founders or the company sanctioned?" | `sanctions_screen_batch` + `sanctions_list_status` |
| "Is this crypto firm licensed?" | `mica_check` (EU), `vara_register_check` + `adgm_fsra_check` + `dfsa_register_check` (UAE) (+ `fca_firm_check` with a free key) |
| "Did they raise money?" | `edgar_form_d_search`, news via `rss_search_entries` (Bing News RSS) and `gdelt_search_articles` |
| "Is the product real and growing?" | `webtraffic_traction_report`, `rdap_domain_age`, `github_developer_scorecard` |
| "Is the token/contract safe?" | `goplus_token_risk_scorecard`, `blockscout_contract_risk`, `sourcify_verification_report` |
| "What does the deck claim?" | `deck_extract` on the run input |
| "Which of 200 candidates matter?" | `decide_batch_file` over a saved file, one call |
