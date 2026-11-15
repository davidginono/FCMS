# Financial report header fidelity

The November 12 request makes the actual Microfinance report top fields authoritative for position, spacing and typography. Sources: C:/Users/USER/Desktop/Microfinance/src/main/webapp/WEB-INF/jsp/accounting/statements/preview.jsp and print.jsp, their financial-statements.css and financial-statement-print.css, StatementExportService.context and AccountingPdfDocument.

## Change

Removed the added Unavailable/Haipatikani suffix from the previous-period header in the GL and consolidated presentation builders. The original header contains the date range only. Comparison amount cells still explicitly distinguish unavailable history from zero; no prior values, financial amounts, periods, posting states or source coverage changed. There is no schema, query, authorization, dependency or executor change.

Preview retains the centered institution name in regular 15px type, 12px metadata, original inherited typeface, 8px row and 24px column gaps, and 26px spacing before the table. Titles retain their original face, weight and spacing, including the red equity title and Courier metadata. Standalone print retains its separate stacked four-line metadata layout, 14pt title and 8pt metadata. PDF retains the original 14pt title, 8pt metadata, 12pt metadata leading and 32pt left inset. Print stylesheet rules are identical to Microfinance. No additional stylesheet override was necessary.

## Direct rendered evidence

A temporary read-only loopback reference served the original preview/print markup structure and full original stylesheet stack with the same FCMS field values and paper widths. It did not execute Microfinance application/database workflows or import its institutional identities. Header coordinates are compared relative to their paper, preserving the existing FCMS shell.

| View | Coverage | Result |
| --- | --- | --- |
| Preview | Income, financial position, cash flow, equity and consolidated; EN/SW; 320/375/768/1024/1440/1920px | 60 unique cases; zero differences |
| Standalone print | Same five views in EN/SW | 10 unique cases; zero differences |
| PDF original renderer | 15 deterministic cases including trial, long reports and oversized values | 34 pages; 100% identical pixels, zero text/font/color/coordinate/size differences |
| Native PDF | Six financial formats in both languages | 12 downloads, 26 pages, correct top fields, bounds/footers and all 86 monetary preview cells |

The browser comparison checks each top field's x/y position, dimensions, font family, size, weight, style, line height, letter spacing, case, alignment, decoration, color, margins, row/column gaps and display, plus the table start. Requested and observed widths are validated on both pages. Two-tab viewport targeting and asynchronous resize observations were corrected in the test harness; only the 70 successful unique cases are acceptance evidence. All original CSS hashes remained stable. Temporary reference tab/server were closed, viewport overrides reset and device metrics cleared. The native FCMS income view remains open. Screenshot: output/screenshots/fcms-report-header.png.

Evidence is in .verification/report-header-fidelity-20261112: comparisons.json, browser-acceptance.json, original-source-css-manifest.json, print-css-equality.json, reference-comparison.log, reference-geometry.log, native/http-checks.json, native/pdf-checks.json, financial-before/after.txt and output-samples.json. The drawing helper is the unmodified original identified in the main financial-report source review. The isolated reference driver retains the separately identified FCMS provenance appendix boundary.

## Build and runtime

Command: mvn -o -Dtest=StatementFacsimileTests,ConsolidatedStatementPdfTests,ArchitectureTests package. Ten tests passed with zero failures/errors/skips; BUILD SUCCESS at 2026-11-12T18:17:15+03:00. Independent header assertions cover both languages and available/unavailable history; comparison cells remain asserted. Log: package.log in the evidence folder.

Accepted WAR SHA-256: db994e3087ee18ba51490582dbdf7b3aa92c14673c7a2746fa309b9cdb3f6078. All 654 runtime file payloads match the WAR. All 25 affected source hashes match full-source-manifest.json. Owned fixture preview PID17840 serves loopback8093 from .verification/exploded-report-headers-20261112 against retained synthetic fcms_codex_verify_config_20261103_d. SMTP is disabled and private storage/job settings remain. Ten financial-table counts/content hashes matched before/after all report checks; health returned200. The temporary reference server18500 was identified and stopped.

Rollback requires the previous compatible application artifact, with no data rollback for this change. The earlier financial-format package remains historical evidence. No production rollout, capacity result or new security guarantee is claimed.

Trial paper preview, account-ledger PDF, Notes/approved COA reporting metadata and other remaining module/export gates stay open under the original goal; see financial-report-source-review.md and remaining-export-format-audit.md. This header acceptance does not redefine completion of that goal.


## Ledger header completion — November12

The account ledger now uses the same original header contract. EN/SW preview at six required widths and both standalone print views passed14 source-CSS comparisons: all style properties match; maximum floating coordinate delta 0.000072 CSS px (0.02 px tolerance), print 0. Complete native and deterministic ledger PDFs match the original renderer across 8 pages with zero text/font/color/coordinate/size delta. See docs/account-ledger-paper.md for current runtime, evidence, samples and limits. The earlier trial workspace and ledger-paper gaps are closed; Notes and approved reporting metadata remain separate.


## Current original equity header — November 13

The Microfinance source changed during this check. The frozen authority is `.verification/header-recheck-20261113/original-snapshot/manifest.json`, including its actual presentation/export builders, JSPs, styles and unmodified PDF renderer. This section supersedes the earlier red, centered equity-title description. FCMS now places the institution above the left-aligned black equity title, with the scope/currency below it; it uses the original font sizes, weights and vertical spacing in preview, standalone print and PDF. The title carries the localized period/year-ended date. The selected full range, prior period and generation timestamp remain in the separate provenance appendix. The original preview/print source contains a replacement character before TZS; FCMS retains a valid middle-dot separator as an explicit encoding correction.

All eight report faces (income, position, cash flow, equity, consolidated, trial, ledger and Notes) passed 112 current-style browser fixture comparisons in EN/SW: six preview widths and both print views. All top-field positions, dimensions, typography and table-start positions matched exactly (maximum observed CSS-pixel delta 0). Fixtures use the actual stylesheet stacks and original header structure with identical prepared values/paper widths; this is not a new authenticated live-app workflow run. Both static and webapp CSS copies match. Temporary browser tab and owned loopback reference server were closed.

Fourteen generated PDF first-page headers, including Notes, match the unmodified frozen original renderer: identical cropped header pixels and zero character x/baseline/size, font or color differences. This deliberately checks header bounds; it does not assert complete current equity-table/body facsimile. PDF reference dependencies remain isolated verification dependencies.

Validation: `mvn -o -Dtest=StatementFacsimileTests,ConsolidatedStatementPdfTests,ArchitectureTests test` passed 13 tests, zero failures/errors/skips (2026-11-13 16:59:57 EAT). Jasper compiled statement, print, trial, account-ledger and consolidated JSP pages with zero errors. The initial audit assertion assumed that the generation timestamp fit on provenance page 2; corrected coverage searches all provenance pages while still asserting that it is absent from the financial face. Initial browser harness failures were connection-backlog/proxy and stale-function issues; only the final 112 fully loaded cases are acceptance evidence. No financial records, database schema, authorization, query, dependency or executor change was made by this header slice.

Evidence: `.verification/header-recheck-20261113/` contains browser comparisons/acceptance, original snapshots, exact PDF header audit, successful test/JSP logs and 17 final source hashes. No new application WAR/runtime was deployed. The older local preview on 8093 was not running when this slice began. Revert the listed header source changes to roll back; there is no database rollback. Notes/COA reporting and the broader module/export goal retain their separate unfinished verification gates.
