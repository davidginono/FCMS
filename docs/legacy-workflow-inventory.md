# Legacy FCMS workflow inventory

The sanitized ZIP's258 root PHP files are mapped individually in [the inventory](legacy-workflow-inventory.csv). Root `fcms/*.php` is the canonical source; older `CODES`/`CODE2` copies and bundled third-party libraries remain references. These mappings identify native capabilities and shared components. Only the compatibility-controller routes are PHP URL aliases. The source was inspected without running its database/authentication entrypoints.

| Capability | Native workflow and verification |
| --- | --- |
| SACCOS, AMCOS, Union, Microfinance, Other clients | Five bounded registers, create/details/edit, exact fee/cost fields, financial-year/payment filters, contracts, archive/recovery, versions, native CSV/PDF and full/single-fee/payment summaries; PostgreSQL/native/browser and original-renderer evidence. |
| CSV upload/download | Valid legacy headers and native template, inspected/private staged upload, exact review, explicit atomic commit/discard, owned idempotent evidence and canonical client creation; parser, rollback/concurrency/reference locking, native HTTP and33 rendered checks. |
| IAM and account self-service | Effective atomic claims, custom claim bundles, user create/edit/activation, profile/photo/password, recovery/OTP and bounded durable mail. Password/session/scanner/SMTP integration tests and prior EN/SW responsive acceptance. |
| Tasks | Assignment, allowed lifecycle transitions, creator/owner scope, exact allowance/amount/profit, bounded staff/client/date filters and dedicated PDF. |
| Leave and staff/group reports | Draft/edit/submit, independent review/outcome, owner/team scope and selected staff/client reports. Group participants share one retained work record; scoped leave remains private. Native create/submit/review/participant access tests and rendered acceptance. |
| Expenses and category summary | Reasoned retained lifecycle, independent review/payment evidence/history, selected staff/date/year/category scope, exact whole-result summary/drill and original category/expense PDF. |
| Other income, petty cash, cloud and bulk SMS | Immutable money/unit entries, idempotency, authorized linked reversals, bounded register/reconciliation and source-specific exports/receipts. Zero-money SMS units remain supported. |
| Cloud billing | Actual recorded SACCOS fees/costs, year/status/search/sort, native PDF and eight exact source-layout fixtures. The unpaid PHP120,000 constant is excluded from accounting; recorded costs remain visible. |
| Backups | One-active-job admission, private job history/download, bounded process/timeouts, retained failures and restore verification. The V26 round trip includes private import evidence and associations. |
| Consolidated reporting | Authorized received-income and paid-expense snapshots, exact totals/client-margin drills and the approved Microfinance income stationery. |
| Dashboards | Claims-scoped native metrics/portfolios, owner-scoped work, company-scoped authorized client/finance facts and EN/SW responsive rendering. |
| Accounting extension | Reviewed COA/reporting/Notes, Activities/Transactions, source coverage/cutover, periods, balanced journals/receipts/payments/openings, independent posting/review/reversals, trial/account ledger and financial statements with dated drills; dedicated source-format/security/concurrency evidence documents. |
| Prospects/invoices extension | Separate claims, exact versioned lines, retained documents and original proforma/invoice artwork, native workflows and rendered checks. |
| Shared PHP helpers | Native JSP components, scoped lookups, generated private file storage and normal security/validation services replace source database/form/navigation helpers. |

Unsafe arbitrary-file frames resolve to authorized native contract/photo resources. Legacy financial deletions resolve to retained reversal/cancellation workflows; client deletions use reviewed archive/recovery. The source's410 client-activity mutation uses native versioned editing. Role names seed claim bundles; runtime authorization checks effective claims and record scope. FCMS has one company; no invented tenant/branch ownership is attached to the source data.

No uncovered business capability was identified in this root-source inventory. Evidence is bounded to the documented synthetic fixtures and workflows; deployment-specific mail/scanner/backup configuration and sustained1,000-RPS capacity remain operational acceptance checks. Historical pending statements in earlier verification ledgers should be read with their later acceptance entries.
