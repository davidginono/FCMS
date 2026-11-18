# Microfinance COA tree

FCMS uses actual account parents instead of classification-only sections. The reference is the Microfinance `accounting/accounts.jsp`, `account-rows.jsp`, `accounting-accounts.js`, and chart onboarding rules inspected on 16 November 2026. FCMS retains its company-owned ledger, claims, account IDs, financial approvals and reviewed reporting definitions.

## Behavior and interfaces

- An unfiltered register starts with the five root accounts. Expanding a group loads bounded direct children. Native parent links and breadcrumbs provide the same navigation without JavaScript.
- Columns are Code, Name, Parent group, Classification, Kind, Normal balance and Status, plus single-record selection. Levels 1–3 are Group; level 4 is Posting.
- Selected commands use the shared Actions menu. Create changes between Add group and New posting account based on the selected parent. Active children prevent group deactivation; posting choices exclude inactive accounts and headings.
- `GET /accounting/accounts` accepts `parentId`, `classification`, `kind=GROUP|POSTING`, and the existing search/status/page/size/sort/direction parameters. With no parent, filters search the chart; a supplied parent restricts results to direct children. Default ordering is code ascending with an ID tie-breaker. Sizes remain 10/25/50/100.
- `GET /accounting/accounts/children` renders escaped row fragments with the same query contract, `X-COA-Children: true` and `Cache-Control: no-store`. Group batch parameters are superseded by parent/page navigation.
- Account creation accepts `mode=GROUP|POSTING`; absent mode is inferred from the selected parent. Existing programmatic `AccountForm` callers retain parent-derived behavior. `/choices?posting=false&parentMode=GROUP|POSTING` narrows creation parents; existing posting selectors retain their contract.
- `GET /accounting/accounts/next-code?parentId=…&mode=…` requires `accounting.setup` and returns `{code}` with no-store caching. An empty code means the range is exhausted. Suggestions reserve nothing; the unique constraint resolves concurrent saves. Duplicate saves retain input and display a conflict.
- Groups inherit classification and normal balance. Posting accounts inherit classification and may use Debit or Credit. Cash remains restricted to asset posting accounts. Actual ancestry and code prefixes are validated server-side; identifiers and classification remain immutable on edits.

## Migration and rollback

V29 adds missing `110000` Current assets, `120000` Non-current assets, `210000` Current liabilities, `220000` Non-current liabilities, and `111000` Cash and Bank. Compatible records retain IDs and names. Conflicting parent, depth, classification, normal balance or status aborts the migration transaction with the code identified. Resolve conflicts through a separately reviewed mapping; do not overwrite existing identities to make deployment pass.

The migration adds an insertion guard for code/ancestor integrity and a `(parent_id, code, id)` index supporting direct-child pages and child-existence checks. It does not update journal/line tables, move existing accounts, change account names, or publish reporting classifications/notes. Reporting sections still require FCMS's existing proposal and independent review process.

Application rollback retains the additive defaults, guard and index. Previously released migrations and financial history must not be destructively rolled back. Before deploying, run the migration against a restored copy of the target database and inspect any reported conflicts. No production deployment is included in this change.

## Validation

Focused PostgreSQL tests cover hierarchy, default migration/rollback, bounded paging and sorting, code exhaustion, concurrent duplicate creation, lifecycle and permission restrictions, native JSP/fragments, input recovery and CSRF. Existing reporting, transaction configuration and architecture checks remain part of the affected gate. Node tests cover selection, branch expansion/paging/retry, stale response cancellation, preference application and native state forms.

Evidence is retained under ignored `.verification/coa-tree-20261116/`. Query plans use an isolated synthetic PostgreSQL database; these are query-level checks, not a 1,000-RPS capacity result. No application cache, dependency or executor was added.

The in-app browser reached the isolated preview's login page, then its control service repeatedly timed out. Actual screenshots, keyboard operation in a real browser and the six responsive viewport checks are not certified by this run. HTTP-rendered JSP and DOM/component checks do not replace that visual acceptance gate.

### Accepted automated checks — 16 November 2026

- 30 distinct affected Java tests passed: COA tree 8, reporting 13, transaction configuration 5, existing COA workflows 2, architecture 2. The reporting assertion now expects the new Cash and Bank default while retaining the same financial values and grouping assertions.
- A final focused native HTTP test passed against the latest controller annotations, including successful creation, duplicate-input recovery, parent-context redirect, CSRF denial and rejection of invalid read-only submissions. `mvn -q -Dtest=CoaTreePostgresTests#nativeJspAndFragmentContractsRetainLanguageFiltersAndCsrf package` completed successfully.
- `npm run test:ui` passed 30 tests. The final COA-only rerun passed all 10 checks after adding keyboard focus restoration for child paging. `npm run build:css`, asset synchronization, affected-file `git diff --check`, and `python scripts/verify-agent-references.py` passed.
- The synthetic 1,040-account child-page example returned 26 rows using the parent/code index; measured execution was 0.453 ms versus 0.625 ms without that index in the comparison transaction. Posting rows skip child-existence probes. These single-query measurements do not establish endpoint throughput, latency percentiles or capacity.
- The final WAR's relevant JSP, JavaScript, CSS, compiled classes and V29 migration match the built workspace payloads. WAR SHA-256: `d236b86deff6d9741975f4f684b6e642e89daf573f2cb4d61bd7295981557343`. Detailed results and query plans are in `accepted-summary.json`, `query-plans-final.txt` and the retained test logs under the evidence directory.

The primary changes are the accounting controller/service and COA view DTOs; the register, row fragment, filters and form; the shared table's dynamic-row/context hooks; grouped-table/form assets; EN/SW labels; V29; and the affected Java/Node tests. During implementation validation, the isolated preview process was stopped and existing application services/databases were not restarted or migrated.

### Local activation — 16 November 2026

At the user's subsequent request, V29 was applied to the retained local app schema `cash_equity_cbf2018b1c594822b0e4c22c2cc095e0` in `fcms_codex_verify_reporting_20261113_a`. A PostgreSQL 18 custom-format schema backup was restored into a separate database; all 49 tables and sequences matched before a successful migration rehearsal. The target was checked against the backup again immediately before migration.

Only missing defaults `210000` and `220000` were inserted. Existing account identities/names, other table data, and reviewed reporting were unchanged. After app startup and authenticated HTTP checks, all 11 existing financial/reporting tables and protected account records still matched their before-migration fingerprints.

The verified `d236b86d…` WAR now runs on loopback port **8093**, using the same retained database, avatar/contract directories and backup directory. PostgreSQL 18 backup tooling and ten-second queue polling remain configured; SMTP remains disabled for this local app. Readiness is UP. Authenticated English/Kiswahili COA pages, all eight columns, five roots, parent/child fragments, and family-to-posting navigation passed native HTTP checks. A request to open the new COA URL in the app panel was queued; browser automation itself remains unavailable.

Deployment identity, private backup, restore proof, migration logs, live checks and the restart script are retained under ignored `.verification/coa-deploy-20261116/`. The previous runtime remains available for an application rollback; retain V29 and financial history. This activation affects the local verification application, not a production deployment.

### Completeness follow-up — 17 November 2026

The later cleaned local chart was missing the two asset subgroups and Cash and Bank despite V29 already being applied. See [the completeness review](coa-defaults-review.md) for the complete ten-group inventory, V35 repair, additional UI corrections and verification evidence. The earlier deployment fingerprints above describe the 16 November state only.
