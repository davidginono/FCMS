# Operational ledger report facsimiles

The four ledger register exports now use dedicated stationery instead of the generic equal-width PDF table. The owning LedgerService prepares rows, exact money/unit totals and current account counters in a read-only REPEATABLE_READ transaction. Financial commands, permissions, migrations and dependencies are unchanged. Exports retain the existing 1,000-row ceiling and original request pagination is restored on success or failure. The controller returns application/pdf with no-store.

## Source formats

- Petty cash: legacy print_cashpetty.php; landscape A4, 10 mm margins, 12 pt centered bold title, 10 pt printed timestamp, the original seven columns and widths [10,25,30,25,140,22,25] mm, 9 pt body, 7 mm line height, final current balance and printed footer.
- Bulk SMS: legacy print_bulk.php; landscape A4, current SMS units and selected net profit, eleven columns and original [8,24,24,30,45,25,25,17,17,31,31] mm widths. Before/after unit balances are the recorded entry snapshot, independent of query ordering. Separate in/out totals preserve zero-money unit adjustments and corrections.
- Cloud backup: legacy print_cloud.php and ReportPDF in fpdf_report_helpers.php; portrait A4, four columns [10,40,70,70] mm, separate money in/out, selected net balance and centered page footer.
- Other income: the legacy income register has no standalone PDF counterpart; its existing PDF link goes to the consolidated income statement. The FCMS standalone export deliberately reuses the ledger stationery with actor/category/description and separate money in/out. It is a consistent adaptation, not a claim that an original standalone income report exists.

Helvetica normal/bold/oblique matches the original FPDF Arial aliases. The existing licensed Noto Sans font handles supported Unicode outside WinAnsi. Header word fitting, cell padding, line rules, baseline placement and total borders follow the original helper.

## Necessary FCMS adaptations

- Exact BigDecimal amounts; no float conversion. Reversal movements retain their signed financial effect and correction references.
- Explicit generated time in Africa/Dar_es_Salaam, labeled EAT, and TZS. EN/SW labels use the existing message bundles; retained user-entered text is unchanged.
- Active date/search filters appear above the table. Filtered petty reports label the current account balance separately from selected net movement. Bulk current units remain a current account counter while profit/totals describe selected rows.
- Petty/bulk/income descriptions retain the source reference and linked correction. The original narrow cloud table stays four columns, with correction references after the totals when needed.
- Ordinary final rows stay with their total block when it fits on a fresh page; this avoids orphan grand totals. Continuation pages repeat headings. Oversized descriptions continue across pages instead of being clipped by the original fixed-row/page-break behavior. Numeric total text can fit its original cell width rather than overwrite a neighboring cell.
- Ordinary continuation uses a 15 mm body boundary to keep text clear of the footer, including cloud reports whose original automatic break boundary was 10 mm. First-page geometry remains unchanged.

## Verification

Focused command: mvn -o -Dtest=LedgerFacsimileTests,LedgerReportSnapshotTests test, using only a fresh fcms_codex_verify_* PostgreSQL database and isolated fixture schema. Ten tests passed with no failures/errors/skips. They cover original paper/columns, exact cents, independent unit snapshots, corrections, filtered current/selected totals, immutable projections, empty reports, 1,000/1,001-row boundary, restored pagination, denied actors, invalid dates, concurrent posting/snapshot consistency, all four native HTTP exports in English/Kiswahili and staff denial.

The presentation-only reference isolates the original PHP row classes and calls the original FPDF helper with synthetic prepared values. It does not include connect.php or execute an application entrypoint. A fixed timestamp and translated labels allow deterministic comparison; source hashes are retained in .verification/ledger-facsimile/reference-provenance.json. Six original-format EN/SW pages and two income-adaptation pages had 100% identical raster pixels at 1,100 px width. This is fixture-specific evidence, not a claim that every possible export is identical.

The long bulk fixture spans 33 pages with repeated headings and retained final tokens, exact totals and Unicode. The 17 generated/native HTTP fixture documents span 49 pages; every extracted character was within its paper bounds. Contact sheets and first/middle/final continuation pages were rendered and visually inspected. Live browser use remains disabled in Work settings, so no browser-click or responsive UI acceptance is claimed for this slice. No JSP was changed.

Full regression packaging and retained-preview acceptance are recorded after completion below.

Full offline mvn -o package passed 111 tests, zero failures/errors/skips, using fresh PostgreSQL database fcms_codex_verify_ledgerpdf_20261104_b and JOURNAL opening mode. Build finished 2026-11-04 05:20:41+03:00; log .verification/ledger-facsimile-package.log. The 364-file source manifest remained unchanged during verification. Packaged WAR SHA256: E88DE6B70205A26755CAEF7BA9B485050DE7F02AD0B75677679FB0D0805EDFF8.

Packaged preview acceptance: 16 native EN/SW ledger PDF cases passed on loopback8093, including unfiltered and date/search-filtered reports and their rendered register export links. These downloads span20 pages; exact selected/current totals match the retained fixture database and all text stays within paper. Native first pages and the final income page were visually inspected. Four representative English downloads are retained under output/pdf. Retained journal/line/user/client/sales-document/ledger-entry counts are unchanged (30/60/13/3/2/27). Preview health returned200; owned PID28736 remains bound to127.0.0.1:8093 with SMTP auto-configuration disabled and the existing fixture database. No production database or legacy PHP database was written.

Final visual correction: a total could previously occupy an otherwise empty final page. LegacyLedgerStationery now reserves room for the ordinary final row plus its total block when they fit together on a fresh page. A new two-page assertion verifies the final source reference and exact total stay together. Only this renderer and its test changed after the111-test regression baseline. Final mvn -o -Dtest=LedgerFacsimileTests test passed all6 checks (zero failures/errors/skips), finished2026-11-04 05:24:40+03:00. Final mvn -o -DskipTests package then assembled that tested source without repeating the unchanged suite, finished05:25:43+03:00. Final WAR SHA256: BFD3CD345102264184A5D7AE1AB8B02E49BE6DE2B42771579637F4883D2466A5. The regression and final source manifests are retained separately; this is not a claim of a112-test full run.

Final packaged acceptance after the orphan-total correction: the16 localized/native cases and exact database total checks passed again, spanning20 pages. Page3 of the income export now contains the final retained row, repeated table headings and both totals; its fresh render was visually inspected. All364 final source hashes remain unchanged. Retained record counts match the original snapshot. Owned final preview PID2348 serves the accepted BFD3CD... WAR on127.0.0.1:8093; health200. The earlier PID28736 and earlier artifact hash above describe the superseded baseline preview. Four output/pdf samples were replaced with the final verified native downloads.
