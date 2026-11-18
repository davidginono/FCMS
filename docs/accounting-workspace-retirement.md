# Accounting workspace retirement

The requested removal retires Equity reporting, Equity movements, Accounting books and Accounting periods management. Navigation, breadcrumb targets, controllers, forms, management DTOs and exclusive application commands are removed. The Finance cutover/period mutation adapter is removed because it had no surviving production caller.

Old bookmarks and requests under `/accounting/books`, `/accounting/periods` and `/accounting/equity-reporting` receive an empty HTTP 410 response with no-store caching through the shared retired-workspace handler. This covers descendants and all HTTP methods. Authentication and CSRF remain enabled. This terminal handler exists for previously exposed bookmarks, including the equity movements URL shown in the request; it does not retain a compatibility workflow.

## Retained dependencies

- `gl_books`, `gl_periods`, released migrations and database guards retain policy, review and period history. Journal foreign keys, opening journals, balances and audit records remain intact.
- `AccountingService.books()` and its Book view remain read-only dependencies of journal, source-posting and statement services. They require accounting.read.
- JournalService and SourcePostingService still reject absent/closed periods and unapproved books. Existing Finance-owned cutover and period guards protect operational entries and reversals. No default approval, automatic period creation, date widening or reopening is introduced.
- `gl_equity_reporting`, read-only EquityReportingService, immutable statement DTOs and EquityMatrixCalculator support Changes in equity, provenance, profit exclusions and dated drills. Only previously approved metadata is read; pending evidence is never promoted. Statement labels, formatting and exports remain unchanged.
- accounting.setup and accounting.review remain necessary for account reporting, COA, transaction configuration and journal approval. V34 removes the exclusive accounting.period.close permission and assignments/denials. Effective permissions are read from the database; removed handlers cannot be recovered by an existing session.
- Shared tables, forms, navigation, notifications, reports and audit components remain in use. No exclusive scripts, styles, dependencies or jobs were found.

## Operational consequence and rollback

Existing approved books, periods and equity classifications continue to be consumed. There is no application command to configure/approve books, create/close/reopen periods or change equity metadata. A fresh installation or a date outside retained periods remains blocked for GL posting until an explicitly reviewed replacement provisioning process is supplied. This removal does not authorize manual financial mutations or invent that replacement process.

Deploy using normal Flyway migration. V34 deletes only a retired capability; it does not change financial data or schema. Restoring management requires a deliberate code rollback and a forward migration restoring approved permission assignments. Do not edit released migrations or delete financial history.

## Verification

Regression coverage checks direct authenticated GET/POST/PUT/PATCH/DELETE, export-like paths, anonymous access and CSRF; removed navigation; retained approved metadata and denied report access; immutable evidence; posting, statements and period database guards. Historical fixture setup exists only in test sources. The task report records actual commands and verification limits.

Scope review found no new cache, executor, thread or background-job behavior. Financial reads remain in the accounting services with report/read claims; no account/journal amounts, ownership checks or posting predicates were changed. No throughput benchmark was run and capacity is unproven.

## Changed files and checks

Removed AccountingSetupController, EquityReportingController, BookForm, PeriodForm, FinanceCutoverService and nine book/period/equity management JSPs. Trimmed AccountingService, EquityReportingService, AccountingViews and EquityReporting; updated SecurityConfig, RetiredWorkspaceController, ShellAdvice, finance navigation, three message bundles, creation/report/operations documentation and affected tests. V34 retires the exclusive period-management permission. Shared financial renderers and released migrations are retained.

Commands: `mvn -o -DskipTests -Dmaven.compiler.useIncrementalCompilation=false test-compile` passed; `npm run test:ui` passed all 70 tests; `python scripts/verify-agent-references.py` passed. The clean Java run compiled all 202 production and 53 test source files and confirmed retired controllers are absent from output. Its result was 304 discovered tests: 118 passed, one error and 185 PostgreSQL-dependent tests skipped. All four ApplicationErrorHttpTests passed, including the new retirement/authorization/CSRF matrix. The full suite failed at ResourceFeedbackTests.anInternalCommandFieldMissingFromThePublicFormRemainsRecoverable because FormErrors tries to bind lines[0].accountId on UserForm (NotReadablePropertyException). Those mapper/form files were not changed by this removal. PostgreSQL integration requires FCMS_TEST_DB_URL and verification credentials, which were not configured in this chat. No native browser check at the six responsive widths was performed.
