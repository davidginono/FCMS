# Cash flow, equity and statement drills

The November 15 implementation follows the frozen Microfinance calculators, presentation builder, preview/print styles and original PDF renderer. FCMS retains its own company scope, claims, approved books, posting rules and financial values.

## Financial behavior

Cash flow uses actual non-cash journal counterparts and actual COA family membership. Internal transfers between cash accounts do not inflate activities. Mixed-sign compound journals remain explicitly unallocated; their category and supporting entries remain available rather than assigning invented purposes.

The user explicitly requested income surplus in operating activities. Operating cash therefore uses an indirect reconciliation in the original statement paper: reported surplus, signed account adjustments, and operating cash. This is a deliberate content change from Microfinance's direct-method cash report, not a claim that its original direct-method rows are identical. Each adjustment is supported by posted entries, with selected dates retained. The reconciliation does not add surplus on top of cash receipts a second time. IAS 7 reference: https://www.ifrs.org/issued-standards/list-of-standards/ias-7-statement-of-cash-flows.html/

Reviewed earnings transfers are excluded from period income and expense movements. They transfer already-earned profit between equity components without creating or reducing period surplus. Posted trial balances and account-ledger activity retain the actual debit/credit entries and closing balances.

Equity uses the original dynamic component matrix, exact title case, red title, separate period date, blank zero cells, opening/closing date labels, current/prior matrices and totals. Configuration assigns actual equity accounts to named EN/SW components, with at most one retained-earnings component. Missing definitions and unclassified movements remain visible. Every component reconciles independently; offsetting component errors cannot disappear inside a correct grand total.

V25 adds reporting metadata only. Proposals require `accounting.setup`, independent decisions require `accounting.review`, and report access requires `accounting.report`. Reviewed revisions, replacements, reasons, actors and audit records are retained. Request fingerprints, target locks, versions, database guards and atomic retirement prevent duplicate or conflicting publication. Reversals inherit their original journal's movement classification. No journal, line or monetary balance is rewritten.

## Drill and export behavior

Account drills use the original seven-column trial paper. Transaction drills show the original account-ledger paper and retain the scoped contribution evidence separately. Account balances, period totals and signed statement contributions remain distinct. Account and journal links retain the selected dates and validated membership. Scoped print/PDF includes the complete matching account set, bounded at 1,000 accounts, independently of preview pagination.

Cash/equity and scoped-drill print/PDF requests have bounded per-actor admission and use the existing two-render semaphore. Snapshot reads use repeatable-read transactions. There is no new runtime dependency, executor, cache or financial mutation.

## Verification and limits

- `package-final.ps1` ran the affected offline Maven package gate: **62 tests**, zero failures, errors or skips. Coverage includes actual PostgreSQL migrations, cash reconciliation, original equity calculation, claims, CSRF, retained values, scoped/date-preserving drills, concurrent retries/review, audit rollback, JSP/print/PDF rendering, Notes/COA regressions and architecture checks.
- The independent oracle invokes the unmodified frozen Microfinance equity calculator and presentation builder; six EN/SW equity/trial/ledger documents match their columns, weights, cells, row order and summary styles.
- Earlier full-page comparisons covered 18 PDF pages with identical pixels and zero character/font/color/coordinate differences. The final original-renderer fixtures were regenerated after the earnings-transfer correction; verification files remain in the ignored `.verification/cash-equity-drills-20261115` directory.
- Full journal/line content digests are unchanged by reporting configuration and review tests.
- **Browser acceptance was stopped and then explicitly waived by the user before publication. It is not claimed as complete.** No throughput or production-capacity result is claimed.

The broader FCMS module/export goal retains its separate outstanding gates. Rollback requires a compatible earlier application artifact while retaining V25 reporting/audit history; no financial data rollback is needed.
