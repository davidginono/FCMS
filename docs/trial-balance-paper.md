# Trial-balance paper acceptance — November 12

Trial balance now uses the original Microfinance Times paper, with Account, Description, BF balance, Debits, Credits, Net change and Ending balance. The preview keeps authorized account details between Description and BF balance; print and PDF retain the original seven columns. The existing original header, font/density controls and print face are reused.

The paginated count, matching totals, visible rows and coverage are read in one read-only REPEATABLE_READ transaction. Totals cover every matching account rather than the visible page. Search keeps literal wildcard escaping, sorting is allowlisted, and out-of-range page requests clamp. Preview sizes remain 10, 25, 50 and 100; full print/PDF remains bounded to 1,000 matching accounts. Export restores the caller's page and size.

Shared resource-table statement mode uses complete GET navigation so dates, metadata, totals and rows refresh together. Its preferences expose page size; generic column, stripe and sticky formatting does not alter financial paper. Other resource tables retain their controls. The shared view-settings fragment replaces duplicated markup.

Twenty-one distinct affected checks passed: architecture 2, accounting HTTP workflow 1, PostgreSQL comparisons 8, statement facsimiles 7, consolidated PDFs 2, and module-page/permission checks 1. The first module-page attempt timed out on the initial GET /login before reaching any module. Its unchanged retry passed with the same 45-second assertion deadline. Both attempts are retained; no assertion or application timeout was weakened.

Commands:

```powershell
node scripts/sync-webapp-assets.mjs
node --check src/main/resources/static/js/resource-table.js
mvn -o '-Dtest=StatementComparisonTests,StatementFacsimileTests,ConsolidatedStatementPdfTests,ArchitectureTests,PostgresModuleTests#allModulePagesRenderWithJspAndCorrectPermissions+doubleEntryAccountingCompletesReviewPostingStatementsAndControlledClosing' package
mvn -o '-Dtest=PostgresModuleTests#allModulePagesRenderWithJspAndCorrectPermissions' package
```

The database checks use isolated fcms_codex_verify_ fixtures. They cover 125 matching accounts across pages, full export, net-change ordering, literal search, empty results, page clamping, opening-date validation and denied actors.

Acceptance evidence under `.verification/trial-ledger-paper-20261112`:

- Fourteen original-CSS header comparisons: EN/SW previews at 320, 375, 768, 1024, 1440 and 1920 px plus both print views. Field positions, spacing, fonts and table start positions have zero differences; all twelve preview cases retain internal scrolling without page overflow.
- Ten exact original-renderer PDF pages: four fixture pages plus six native pages covering all 44 matching accounts, continuation headers and status appendices. Pixels are 100% identical; text, font, colour, x position, baseline and size have zero differences. Native print/PDF timestamps already matched. The isolated reference uses Microfinance's PDFBox 3.0.3 and unmodified renderer SHA-256 `3c307bc43199d3ae607a86ac2a30fa1b2bb5b4154429fbbad89f6c9576bd9a5f`; it adds no FCMS dependency.
- Twelve native financial PDFs, 26 pages and 346 preview monetary cells pass value, precise A4-boundary and footer checks. Full trial print contains 44 accounts while the selected page contains 10. Search, sorting, size changes, empty results and clamping preserve matching totals in both languages.
- Thirteen browser workflow checks include date/metadata refresh, account date scope, filtering, PDF criteria, page-size Cancel/Confirm, Escape/focus restoration and workspace-only modal blur. COA preferences, stripe toggle/restore, grouped selection and server sorting match the earlier baseline.
- All six native trial PDF pages were visually inspected. Ten financial-table snapshots and all eighteen frozen source hashes remain unchanged.

The final package passed at 19:52:58 +03. WAR SHA-256: `46ac5ee72740c57b00bada05c4215087a38c2308466d3d84e2dff9ced88206c0`; all 658 extracted runtime files match the archive. Preview PID23420 serves loopback8093 against the retained synthetic config_d database. SMTP is excluded and preview background jobs have 24-hour delays. Only owned preview17840 was replaced; the temporary original-CSS server23280 was stopped. Browser viewport overrides were reset and cleared, leaving the trial page open.

Reviewed samples: `output/pdf/fcms-trial.pdf` and `output/pdf/fcms-trial-sw.pdf`. Screenshot: `output/screenshots/fcms-trial-balance.jpg`. Sample hashes, browser metrics, native scope checks and comparisons are retained with the acceptance evidence.

This slice changes no schema, production dependency, financial mutation or approval policy. Rollback can restart the prior preserved header runtime against the same schema. It is not a production rollout or a capacity result. Ledger paper/export, approved COA reporting roles, Notes and the remaining module/export gates stay open under the active goal.
