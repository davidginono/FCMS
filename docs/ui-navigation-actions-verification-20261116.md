# Navigation, breadcrumbs and global actions — 16 November 2026

## Changes

The shared breadcrumb uses the supplied Microfinance reference: blue ancestor link, gray bold current page, SVG chevron and compact spacing. Financial-statement previews use the localized Reports Preview current label. The ancestor remains an authorized native link.

Accounting navigation has three sibling, keyboard-operable groups:

- Transactions config: Activities and Transactions.
- Vouchers: Receipts, Payments and Journals.
- Financial statements: Financial position, Income Statement, Cash flow, Changes in equity, Notes and Trial balance.

Chart of accounts, books, periods, posting sources and operational finance links remain separate. Existing permissions still govern visibility and server access.

The shared report-export tag adapts the original Microfinance print, PDF and Excel SVG geometry; existing actions retain their actual formats. The payment-summary PDF retains its specific localized label. Page-wide actions occupy the title's right-hand action stripe, following [Cloudscape global actions](https://cloudscape.design/patterns/general/actions/global-actions/). Tables retain the shared resource Actions menu, refresh and independent Create action. Financial forms needing input or a reason retain their contextual form actions.

## Verification

- `npm.cmd run build:css`: passed; source and webapp assets synchronized.
- `node --check src/main/resources/static/js/app.js`: passed.
- Both generated application CSS and workspace CSS parsed with Lightning CSS.
- `python scripts/verify-agent-references.py`: passed (110 reference files, four skills).
- Fresh PostgreSQL 18 database; `mvn -o -B -Dspring.test.context.cache.maxSize=2 -Dspring.datasource.hikari.maximum-pool-size=6 -Dspring.datasource.hikari.minimum-idle=0 -Dtest=ArchitectureTests,PostgresModuleTests,CashEquityWorkflowTests package`: 44 tests, no failures/errors/skips.
- Native authenticated HTTP baseline and updated rendering: 134 module/create/detail/archive pages. Form destinations, methods and named controls matched; POST forms retained CSRF fields.
- English/Kiswahili financial previews and the existing read-only accounting actor: passed.
- Eight statement account/transaction drill pages: rendered; report and export links retained the selected dates.
- Final JSP-only payment-summary label adaptation repackaged successfully and checked through native HTTP.

Local evidence is under ignored `.verification/ui-navigation-actions-20261116/`. The local application was rebuilt and restarted on loopback port 8093, preserving the existing verification schema, PostgreSQL 18 backup executable and ten-second backup worker interval. No business records were changed by the UI checks.

No browser or Computer Use checks were performed, as instructed. CSS supports wrapping at narrow widths, but pixel fidelity and rendered behavior at the required viewport sizes remain visually unverified. This change does not alter financial-statement stationery, PDF renderers or amounts, and does not constitute a new export-facsimile certification.

## Column-divider follow-up

The shared Microfinance resource-table adaptation now confines sticky column headings and decorative dividers to `thead`. Body row headings remain in normal flow, following the body-header exception in the original `console-components.css`. Short dividers use stable block insets, logical positioning and the existing neutral border color. Their selector follows visible header siblings so hiding or reordering columns does not leave a divider after the last visible column. Group headings retain their spanning cells and quiet horizontal rules.

`sync-webapp-assets.mjs` now synchronizes the resource-table stylesheet, and the shared header uses a new asset version. CSS build/parser checks, reference verification and `mvn -o -B -Dtest=ArchitectureTests package` passed (two tests). Twelve native HTTP checks covered grouped COA, Activities, Transactions, SACCOs, Expenses and Trial balance in English/Kiswahili; served stylesheet bytes matched source, COA group cells and POST CSRF fields were preserved. The rebuilt app runs on port 8093. Evidence: `.verification/table-column-dividers-20261116/`. Browser appearance, preference interactions and viewport geometry remain unverified.
