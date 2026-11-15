# Prospects and invoice acceptance

Scope: the user explicitly approved the November 3 PHP Prospective clients/proforma and standalone Invoice creator workflows, with facsimile exports. FCMS remains one-company; the new sales claims grant company scope. Saving these documents does not create an active client, a payment or a GL entry. Historical PHP data is not silently imported.

## Changes and boundaries

The sales module owns validated boundary forms/DTOs, query projections, short application transactions and PostgreSQL persistence. Thin MVC controllers prepare shared JSP views and authorized no-store PDF responses. Public IAM access checks reload active identities and claims; the shared audit application interface records mutations in the same transaction. No new framework, dependency, executor or cross-module repository access was introduced.

V23 adds constrained document and immutable revision storage. BigDecimal item amounts and totals are calculated on the server. Database checks independently reject invalid item counts/amounts and inconsistent totals. Register and revision queries are bounded by supported page size plus one, with literal escaped search, whitelisted server sorts and deterministic identity ties. Revision search covers the retained record history, not a loaded client-side page. No row-dependent queries or unbounded ID/choice lists were added.

An actor/request UUID plus payload fingerprint protects retries. A transaction-scoped advisory lock serializes identical commands, optimistic row versions arbitrate edits, and a retained snapshot plus audit event commits atomically. Changed-payload retries and stale edits are rejected. PostgreSQL prevents destructive document deletion and revision changes. Separate read/write/export claims, request allowlists, service validation, escaped output and CSRF are enforced. No sensitive customer data is copied into application log messages.

The PDF renderer reproduces the original PHP stationery using the existing OpenPDF library, original artwork and page coordinates. At most two documents render concurrently; excess work is rejected rather than queued. Item/description limits bound rendering. Currency labels follow the selected document currency without calculating money in JavaScript. The formal document language and company/payment details retain the original stationery.

## Executed checks

| Gate | Result | Evidence |
| --- | --- | --- |
| Fresh PostgreSQL complete suite: mvn -o test | 82 passed; zero failures/errors/skips, 20:48:54 +03 | .verification/sales-full-final.log; database ...sales_20261103_c |
| Final affected package: mvn -o -Dtest=SalesWorkflowTests,SalesPdfTests,ArchitectureTests package | 12 passed; zero failures/errors/skips, WAR success 21:21:28 +03 | .verification/sales-acceptance-final-package.log |
| JavaScript syntax and asset copies | node --check and scripts/sync-webapp-assets.mjs passed | Shared form command/currency hook and mirrored WAR assets |
| Original invoice/proforma facsimile raster comparison | 99.5914% identical pixels; same 210 × 249 mm page | .verification/sales-facsimile/facsimile-raster-comparison.json |
| Decimal and justified terms comparison | 99.5914% first-page and 99.7264% terms-page exact pixel matches | .verification/sales-facsimile/decimal-terms-comparison.json |
| Long invoice | Same eight pages; final description/totals/payment box/stamp retained; 99.6287% final-page match | .verification/sales-facsimile/long-facsimile-comparison.json |
| Browser PDF downloads | Actual authorized invoice-2.pdf and proforma-1.pdf downloads; one correctly titled/sized page each | .verification/sales-facsimile/browser-invoice.pdf and browser-proforma.pdf |

The affected PostgreSQL/HTTP tests cover exact totals and fifty items, invalid monetary/date/type/currency/control-character values, separate claims and disabled identities, duplicate-payload refusal, concurrent identical requests, one-winner concurrent edits, retained snapshots/database constraints, stable register pagination and literal search, server-side revision sorting/search, HTTP CSRF and XSS escaping, value-preserving Add/Remove/validation flows and complete EN/SW JSP rendering. PDF tests cover original geometry, distinct invoice/proforma meaning, decimal cents, justified terms, repeated headers and long-page content retention. Architecture tests inspect the owning-service persistence and claims-only contracts.

Browser evidence additionally checks a single table title, real numeric page-size URLs, Preferences Cancel/Confirm, server sorting, Actions-only selected commands, native resource ID links, search/clear recovery, focused edit/create navigation, live USD labels, non-saving item commands and discard recovery. Across the acceptance runs, eight register/create/edit/detail variants cover 48 unique page/width combinations at 320/375/768/1024/1440/1920 px with no horizontal document overflow. Final-WAR history search, empty message and clear recovery passed; the mobile Save button ends at x=350 within the 360 px document client area. Responsive observations and screenshots are recorded separately in .verification/sales-browser-checks.json. The viewport override was restored and the final verification tab was closed.

## Rollout and remaining limits

The retained preview fixture database ...config_20261103_d was backed up before V22/V23. Journal, line, user and active-client counts match the pre-upgrade manifest. The browser draft checks left exactly one invoice and one prospect, both version zero. V23 starts empty on a live deployment; reviewed import/reconciliation is a separate explicit operation. Retain all revision evidence during rollback and prefer forward corrections. See sales-documents.md and operations.md for migration, retention and stationery provenance.

A declared load test was not run for this slice; sustained 1,000 RPS and production capacity remain unproven. Local verification is not a production deployment or a real mail-provider/antivirus acceptance certificate. The wider FCMS goal remains active for its outstanding module-specific UI, installed scanner and capacity gates.
