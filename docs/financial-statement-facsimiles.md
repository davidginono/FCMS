# Financial-statement presentation and export fidelity

## Approved source and adaptation

The user authorized reuse from C:/Users/USER/Desktop/Microfinance and requires facsimile exports. AccountingStationery ports the original accounting/reports/AccountingPdfDocument.java STATEMENT, EQUITY and TRIAL geometry into the already installed OpenPDF renderer. No new production dependency is introduced. The isolated comparison runner compiles the unmodified original with the source project’s PDFBox3.0.3 dependency.

Preserved: A4 portrait/landscape media boxes, 32pt document margins, centered14pt titles, original Helvetica/Times/Courier fonts and bold variants, red equity title, serif trial title/header underlines, normalized column weights, section spacing, numeric alignment and underlined totals, repeated headers, lossless continuation and page N / total footer. Unsupported standard-font glyphs use the existing licensed NotoSans fallback. OpenPDF serializes coordinates to hundredths of a point.

FCMS has one company and its own immutable financial postings, approvals, cutover policy and source coverage. The layout does not import Microfinance institution/branch data, lending categories or voucher semantics. Account classifications prepare FCMS income, expense, asset, liability and owner-equity rows. Accumulated earnings remain separate. Cash-flow uses the existing authorized OPERATING/INVESTING/FINANCING classifications. Equity columns show owner equity, retained earnings and their total. No unsupported current/non-current split or note mapping is invented; notes cells stay blank.

The statement face retains the original draft-use financial disclosure. Full policy, unposted counts, coverage, unresolved source amounts and reconciliation remain in a separate provenance appendix; both periods are included when comparison is available. Trial exports use the same original TRIAL stationery, retain FCMS separate closing debit/credit columns and mark filtered totals with their search context.

## Comparison and consistency

Prior comparison is the same from/to dates one year earlier. If previousFrom predates the approved book opening, the whole comparison is Unavailable, never zero. A genuinely empty, within-books posting period remains zero. Current/prior summaries and account projections share the StatementService.comparison REPEATABLE_READ read-only transaction and accounting.report authorization. Account drill links use existing account-ledger routes; aggregate drill links retain their original line contribution routes and dates.

Account detail is bounded at1000 leaf accounts per relevant statement classification; limit1001 detects excess and refuses with the existing narrow-report error. Cash-flow and equity summaries do not load accounts or fail because an unrelated classification exceeds the detail bound. Trial exports retain their existing1000-row bound, search and sort behavior. No schema, financial write, cache, background worker or permission change is introduced.

StatementDocument is the immutable, prepared view shared by JSP/print/PDF. Server-rendered preview keeps the original font/density controls, comparison visibility setting and details-link exception for statements. All app text remains short labels except the necessary retained financial disclosure. HTML print hides drill controls, uses portrait or landscape according to the document, and page-breaks provenance.

## Acceptance gates

StatementFacsimileTests: exact comparative values, account/aggregate drill identifiers, unavailable-history semantics, signed formatting, owner/retained equity reconciliation, search-qualified trial totals, original fonts/page bounds, both languages, all page totals, separate provenance and long text.

StatementComparisonTests: real PostgreSQL approved books and posted current/prior amounts; pre-opening unavailable behavior; forbidden actor; invalid dates/types; repeatable-read snapshot while another independently reviewed financial posting commits; 1000-account refusal and unrelated-summary success.

PostgresModuleTests: actual EN/SW statement JSPs, account links, retained aggregate drill arithmetic, native print view, PDF HTTP content/no-store responses and current-year unavailable comparison.

Original comparison evidence is under .verification/statements-facsimile: source-*.json contains the exact FCMS-prepared face; reference-*.pdf comes from the original renderer; spring-*.pdf comes from the application. compare.py checks equal page counts and raster differences at1100px width for every page. Manual PNG review supplements text/pixel checks.

Accepted package: mvn -o package passed97 tests, no failures/errors/skips, at2026-11-03 23:17:58 +03, on the fresh fcms_codex_verify_statements_20261103_b database. Log: .verification/statements-facsimile-package.log. Tests include the32 main module checks, six PostgreSQL comparison/concurrency/bounds checks, five new statement layout checks, retained voucher tests and architecture/security suites.

Original-renderer comparison:26 pages across eleven EN/SW/long fixtures match page counts, with at least99.77419% identical pixels at1100px width. Accepted evidence: reference-accepted-run.log, comparison-accepted.log and comparison.json. The first comparison rerun accidentally selected the source-freeze manifest as a document fixture; it was renamed frozen-source-manifest.json and the reference run completed successfully. This verification-runner issue did not change application code.

Packaged HTTP acceptance: ten EN/SW export cases on the retained main fixture and six actual prior-year comparison cases on a temporary8094 fixture passed native preview/print/PDF and account-drill checks. All17 downloaded PDFs (including filtered trial) comprise38 pages, with correct page numbers and all text inside page bounds. Evidence: http-checks.json, comparison-http-checks.json and http-pdf-checks.json. The available-history fixture verifies actual JSP rendering of both periods and prior coverage. Main fixture verifies Unavailable before book opening. Financial-print views omit drill controls and carry the appropriate portrait/landscape class.

Manual PNG inspection covers actual income, balance sheet, cash flow, equity, trial and provenance pages, plus reference EN/SW faces and long continuation. Reviewable copies of the five actual English HTTP exports are under output/pdf/fcms-*.pdf. Their data are synthetic fixture data; unposted source coverage remains visible and does not become a false complete status.

Accepted preview: owned PID8124, loopback8093, WAR SHA-2568EE05594F1F6D0A61BDFD0C8EB5F59EEC68109808F8A05FE5B93C5EB1198E210, retained fcms_codex_verify_config_20261103_d database. Main financial/user/client/sales record counts match before/after. Temporary comparative fixture PID26776 was stopped after checking its command-line database/schema/port identity. Source hashes match the tested freeze; git diff --check and node --check financial-statement-view.js pass. No production data or server was modified.

## Rollout and remaining scope

Rollback uses the prior WAR; there is no schema/data rollback. Upgrade only the owned fixture preview after packaging and compare retained financial/user/client/sales counts. Production capacity and sustained throughput remain unproven. Journal/opening/reversal export facsimiles now have separate acceptance evidence in docs/journal-export-facsimiles.md; legacy-report format audits remain pending; completing statement fidelity does not complete the whole FCMS goal.

Browser availability: after restoring CUA documentation, the selected browser binding returned “Local browser use is disabled in Work settings”. No setting was changed, alternate browser selected or control mechanism used. Packaged HTTP and PDF acceptance can continue; live responsive/keyboard/preferences checks remain pending while browser use is disabled.
