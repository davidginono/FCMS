# Financial report workspaces

The FCMS report workspace follows Microfinance's `accounting/statements/preview.jsp`, `financial-statements.js` and `fragments/finance-navigation.jspf` for page controls and loading. FCMS keeps its own routes, claims, reporting dates, financial calculations and approval rules.

The Reports sidebar group contains one Financial statements destination. Statement kinds are selected in the preview's wrapping navigation, ordered Financial position, Income Statement, Cash flow, Changes in equity, Notes and Trial balance. Equity reporting and Equity movements management are retired; the Changes in equity statement continues to use retained approved metadata. See [Business workspaces](business-workspaces.md) for current role bundles and recording flows.

`statement-header.jspf`, `statement-period.jspf` and `statement-tabs.jspf` share the export actions, reporting period and navigation. The report paper and its existing print/PDF rendering are unchanged. Trial balance retains bounded server search/sorting/paging, refresh and preferences. Clearing its search retains the reporting period.

The existing `WorkspaceNavigation` owns report GETs, cancellation, timeouts, retry and browser history. During report-to-report transitions only `data-fs-report-section` becomes busy; the title, period controls, export actions, navigation and view settings remain visible. A successful response updates report content, selected tab, effective date fields and export targets together. View preferences are reapplied by report kind, and trial table controls initialize against the new region. Obsolete responses cannot overwrite newer selections. Failure retains the original region and offers a local GET retry.

Each request still reaches the existing authenticated controller and application services. No separate router, new endpoint, query, cache of report data, database migration, background worker or mutation handler was added. Print, PDF, modified clicks and native form ownership are retained. When progressive navigation is unavailable, links and GET forms remain usable through native navigation.

Verification on 17 November 2026:

- `python scripts/verify-agent-references.py`: passed.
- `npm run test:ui`: 37 passed, including local pending states, supersession (including delayed response bodies), cancellation, failure/retry, Back/Forward, date/export context, preferences, native downloads and shared-shell regressions.
- `mvn -q test`: 105 executed successfully; 167 PostgreSQL-gated cases skipped without a configured integration database. No failed tests.
- `mvn -q -DskipTests package`: compiled and packaged successfully.
- Local HTTP checks: all six report pages, print pages and PDFs returned 200; unauthenticated report access redirected to login. Preview article markup matched before/after with identical dates after normalizing only the generated timestamp. The packaged Java classes matched the previous running artifact.
- Browser checks cover keyboard tab switching, Back/Forward, retained view settings, local error/retry, trial search/export context, dated account drilling, English/Kiswahili navigation and responsive widths of 320, 375, 768, 1024, 1440 and 2560 pixels. Controls fit the viewport; horizontal scrolling remains inside the paper canvas. The spinner sits 24 pixels below the report-region edge while the tabs and title stay visible. Network and viewport test overrides were reset.

The local preview runs at `http://127.0.0.1:8093`. Ignored verification artifacts and retained prior runtime files are under `.verification/report-workspace-20261117/`. Rollback uses the previous application artifact; no database rollback is required. These functional checks do not establish a throughput or capacity result.
