# End-to-end legacy parity audit — accepted locally

The goal is to verify every canonical FCMS PHP business workflow against Spring, fix missing/partial behavior and push all resulting changes after verification. The implementation and local verification gates below pass. GitHub delivery is verified after committing this report; the final delivery response records the pushed commit.

The scope is the read-only canonical root PHP snapshot and the documented single-company FCMS model. [The source contracts](legacy-parity-contracts.md) record concrete fields, actions, filters, exports and deliberate authorization/retention/financial adaptations. The 258-file inventory is the navigation index, not proof by itself. This audit supersedes the earlier blanket completeness statement at `d85bc16`.

## Gaps found and resolved

| Finding | Accepted behavior |
| --- | --- |
| D1 Dashboard year and missing financial/status facts | Latest configured financial year by default; explicit year retained in every applicable metric drill. Five family counts/payment states, annual/received/tax differences, fee breakdowns, cloud facts, signed income/SMS net, scoped expenses and canonical fee/expense margin. Missing/noncalendar years produce unavailable figures rather than all-year or invented totals. |
| D2 Administrative expense assignment/year | Atomic `expense.assign` claim restores selected active staff and configured fiscal-year metadata. Actual creator and latest preparer remain distinct from assigned owner; owners/creators/preparers cannot independently approve or reverse their own request. Custom assigners see their own created assignments without unrelated company scope. |
| D3 Individual/group report assignment and participation | Atomic `staff-report.assign` claim restores on-behalf creation/preparation. Groups contain selected owner/members without silently adding the maker. Optional directory controls respect their own claims; a report assigner can complete an unlinked report without gaining client-directory access. Participants receive scoped activity access and are excluded from review at both service and database boundaries. Staff filters and activity exports include actual members while canonical ownership remains unchanged. |
| D4 Long expense category history | Accepted 255-character categories survive submit/review/payment/history; retained history column widened in forward V27. |
| D5 Task creation-date reporting | Explicit Start/Created date basis preserves old start-date callers and supports the original creation-date workflow. Tanzania inclusive calendar bounds, filters/paging/language links and native PDF context agree. Leave/report activity dates retain their original basis. |
| D6 Selected staff beyond lookup page | Bounded first-page choices union the selected staff identity, including read-only global work claims. Reload/error/language recovery retains the chosen person. |
| D7 Report/group deletion had no safe native equivalent | Reasoned, versioned withdrawal retains DRAFT/REJECTED/SUBMITTED records, participants, origin and audit evidence. Only an owner/authorized preparer can withdraw; approved reports stay immutable. Cancelled activity remains filterable/exportable. |
| D9 Reporting context on language changes | Language links retain selected year, literal category and task date basis. They continue excluding credentials and mutation request keys. Native language clicks and HTTP/CSRF coverage verify the selected financial context remains stable. |
| D8 Mixed business calendars | Defaults, reversal/payment dates and future-date checks now share Tanzania's calendar, independently of the host timezone. UTC audit/event instants remain unchanged. Frozen midnight/New-Year tests and actual UTC-host payment/posting/reversal regression pass. |

Native create/edit/action successes use shared PRG feedback. The shared dismiss icon uses an HTML entity, avoiding a JSP-fragment encoding defect spotted in the rendered review. Changed forms/details/dashboard use current reusable Cloudscape components, short EN/SW labels and no explanatory UI paragraphs/subtitles. Financial export geometry remains unchanged.

## Workflow review

| Workstream | Result and evidence |
| --- | --- |
| Dashboards/metric drills | D1 accepted: 279 final browser checks plus PostgreSQL/native/default-year/claim-scope tests. |
| All five client registers/lifecycle/contracts | Source fields and family support traced in the contracts. Existing canonical create/edit/archive/recovery/reference/version/upload tests pass; native register/report controls and actual downloads pass. |
| Fees/revenue/payment/cloud billing | Full/single-fee/paid/unpaid/empty/continuation variants, actual cost semantics and selected context pass.62 native client checks,8 billing checks and84 exact original-layout cases. Long multilingual cases retain all content and totals rather than reproducing source clipping. |
| CSV | Parser,14 PostgreSQL/native workflow tests and33 final browser checks pass. Private inspected staging, row recovery, owned atomic commit/discard, retries and associations survive backup restoration. |
| IAM/accounts/recovery | Source administration/profile/password/photo/status/role/token/OTP contracts traced. Existing claims/session/password/upload/recovery/mail/production-bootstrap tests pass; native account/admin pages are included in final rendered acceptance. |
| Tasks | Assignment, exact allowance/amount/profit, type, activity states, owner/creator scope and PDF remain verified; D5 date basis/boundary/native-context checks pass. |
| Staff/group reports/leave |14 new PostgreSQL/native staff-report tests plus existing task/private-leave/review/notification tests pass.113 final rendered assignment/group/review/return/correction/withdrawal/filter/PDF checks pass. |
| Expenses/categories/payment evidence |9 new PostgreSQL/native assignment/migration tests plus existing history/category/payment/cutover/evidence tests pass.77 final browser checks cover actual create/edit/submit/independent approval/payment/reversal, exact values and retained unknown history. |
| Other income/petty/cloud/SMS accounting | Source dates, money, units, references/payment modes, descriptions and categories traced. Retained reversal adaptations, exact retry identity, concurrent petty withdrawals, unit-only SMS, reconciliation/source integration and receipts pass regression. Native ledgers are checked in both languages at six widths. |
| Consolidated financial reporting | Existing safe financial policy explicitly reviewed: actual received fees/costs from all five families, archived financial retention, signed other income and actual-date paid expenses; no invented annual/cloud receivables or GL receipts. Operational Fee and expense summary and posted GL statements remain distinct. Original Microfinance presentation/statement/drill tests pass. |
| Backups | Final native job/download/restore READY: 5,413,184 bytes; 23 table snapshots match exactly across operational/import and assignment schemas. V27 provenance, participants, audit and financial facts survive; restored assignment schemas remain at V27. |
| Prospects/proformas/invoices | Source line-item/customer/date/document contracts and existing native/DB/sales PDF tests pass regression. Pages participate in final rendered checks. |
| Accounting expansion | Existing COA/reporting/Notes/configuration/source coverage/cutover/period/journal/review/reversal/statement/drill suites pass, including the timezone regression that initially exposedD8. |
| Shared UI/localization/authorization |260 shared native/rendered checks plus the focused suites below: all seven browser suites report zero JavaScript errors. Changed pages checked in EN/SW at320/375/768/1024/1440/1920. Service/DB claims and owner/creator/team scope, CSRF, forged recipients, error recovery and immutable provenance are tested. |

No known missing canonical module remains within these traced contracts. Unsafe source GET deletions, arbitrary file access, client-supplied balances and role-name authorization are deliberately replaced by the retained native workflows documented in the contracts.

## Final acceptance

Machine-readable evidence: [verification/legacy-parity-20261115.json](verification/legacy-parity-20261115.json).

- Fresh PostgreSQL database `fcms_codex_verify_parity_full6_20261115`, V1–V27, `mvn clean package`: 254 tests, zero failures/errors/skips. This adds 33 meaningful checks to the 221-test baseline. Log/workspace/.fcms/parity-full-regression6.log. Test defaults now use the same business calendar; existing assertions remain intact. Subsequent test-source cleanup removed formatter-only churn and passed `test-compile` without changing the accepted production WAR.
- Immutable WAR SHA256: `c4b0195a25111857fa5bf72bb1f3a8028a0b0c44b2b70609149434116467bfff`. All final native runtime identities match this artifact; previously loaded WAR files were never overwritten. The earlier 8090/8091 review processes and fixture databases are retained; new operational acceptance runs on 8095.
- 832 final browser checks: 279 dashboard,77 expense assignment,113 staff assignment/withdrawal/task date basis,62 client reports,8 billing,33 CSV and260 shared screens. Zero failures/JavaScript errors. Results under.verification/dashboard-parity-native,expense-assignment-native,report-assignment-native,parity-client-reports,parity-billing-native,parity-import-native andparity-rendered-workflows.
- 84 exact original-renderer export comparisons:8 operational,10 client registers,24 revenue/payment,10 empty registers,24 empty revenue/payment and8 cloud billing. Text/font/coordinates and raster pixels match the original presentation with identical synthetic inputs; long-content and required financial corrections remain documented separately. Log/workspace/.fcms/parity-final-source-export-comparison.log.
- Native backup/restore: .verification/parity-backup/results.json and/workspace/.fcms/parity-final-backup.log; all 23 compared table snapshots match, including the new assignment schemas and private import associations.
- PHP archive checksum and bundled skill/reference verifier pass: 110 files/four skills. Architecture checks and `git diff --check` pass. No dependency/lockfile or released V1–V26 migration change.

The first full audit regression exposed the UTC/Tanzania mismatch in two accounting workflows; D8 fixed the root cause before the fresh 254-test acceptance. Browser harness selectors were scoped to their actual portal/review form, and narrow PDF cell line breaks were normalized for member-name assertions. Those harness failures are not counted as passing evidence.

## Schema and operational review

V27 adds claim bundles, nullable attested expense creator/preparer and work preparer/creation hashes, maker-scoped retry uniqueness, draft-preparation/independent-review guards and the history category width. Audit detail is bounded to 2,500 characters so a valid 1,000-character transition reason and prior 1,000-character review are both retained. Historical expense actors come only from retained first/last preparation audit events. Work preparer uses the last retained save actor; unchanged version-zero rows can use their retained native creator, while edited rows lacking evidence stay unknown. Creation evidence is immutable; identical maker-scoped concurrent retries return one result and changed payloads conflict. Legacy unknown request collisions fail without disclosing another record.

Services own transactions/queries and call public IAM/client/reference contracts. Read snapshots are bounded; group exports cap both base and expanded rows at 1,000. There is no N+1 member projection. New uniqueness indexes support actual command lookup/invariants; no speculative capacity/index change was introduced. Small-fixture dashboard query plans are retained locally, without a capacity claim.

Apply V27 as a forward migration with a tested backup and the matching application. Do not edit released migrations, fabricate unknown actors, repair live checksums or destructively roll back retained financial history. A rollback requiring the old schema must use a tested consistent backup/application pair. The local native restore proves fixture restoration, not an operator's production recovery procedure.

No production deployment/publication occurred. Real SMTP/scanner/provider credentials, production backup storage and sustained capacity remain deployment acceptance work; they are not missing canonical workflow implementations. The cloud environment cannot read `C:/xampp/htdocs/fcms` directly: this audit uses the repository's verified sanitized PHP ZIP.
