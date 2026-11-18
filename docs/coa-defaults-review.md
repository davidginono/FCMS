# COA completeness review — 17 November 2026

The running local chart had seven groups despite V29 being recorded as applied. `110000`, `120000` and `111000` were absent. The retained maintenance script under `.verification/retain-admin-20261117/cleanup.sql` removes records associated with deleted fixture users, including their accounts and descendants; a once-applied migration does not rerun after that cleanup. The original deployment check therefore did not establish completeness of the subsequently cleaned chart.

## Defaults checked against Microfinance

The authority is `GeneralLedgerService.initializeChartDefaults` in the user-specified Microfinance checkout. The repository's sanitized presentation snapshot supplies the table/form comparison. The complete standard structure is:

| Code | Default group | Parent | Depth |
| --- | --- | --- | --- |
| 100000 | Assets | — | 1 |
| 110000 | Current assets | 100000 | 2 |
| 111000 | Cash and Bank | 110000 | 3 |
| 120000 | Non-current assets | 100000 | 2 |
| 200000 | Liabilities | — | 1 |
| 210000 | Current liabilities | 200000 | 2 |
| 220000 | Non-current liabilities | 200000 | 2 |
| 300000 | Equity | — | 1 |
| 400000 | Expenses | — | 1 |
| 500000 | Income | — | 1 |

All ten are non-posting groups. Depth-three families accept depth-four posting accounts; the Cash and Bank group is not itself a cash posting account. Institution-specific groups and posting accounts are created through the authorized form.

V35 validates the entire structure and inserts only missing defaults. It preserves compatible IDs, names, descriptions, versions and existing records. New defaults have no fixture-user owner. Incompatible parent, depth, classification, normal balance, cash flag or active state aborts the transaction. V29 remains unchanged. There is no GET-time provisioning, automatic reparenting, renaming or reporting approval. Application rollback retains the additive migration and financial history.

## Other omissions corrected

- Cancel on an account form now returns to the owning parent collection.
- Replacing an already mounted branch during global filtering clears selection on removed rows and descendants, notifying shared details. Filter reset also emits deselection so an open details pane cannot retain a stale account.

## Other areas reviewed

| Area | Finding |
| --- | --- |
| Columns | Selection plus Code, Name, Parent group, Classification, Kind, Normal balance and Status are present. All seven data columns have bounded server sorting. |
| Hierarchy | Actual parent IDs, direct-child paging, native parent navigation and ancestry validation are present. Global filters search the chart. |
| Creation | Group/posting modes restrict eligible parents; finite code suggestions do not reserve codes. Duplicate and exhausted ranges are handled. Classification is inherited; contra normal balances are permitted only on postings. Cash is restricted to asset postings. |
| Actions | Single selection controls authorized details, children, edit, lifecycle and posting ledger commands. Active children protect group deactivation. |
| Progressive behavior | Native links/forms, CSRF, retained field failures, destination feedback, retry/abort and preference application to loaded branches are present. |
| Localization and layout | Existing EN/SW message bundles, responsive filter wrapping, bounded form sections and horizontal table scrolling are retained. Real-browser checks must be reported separately from DOM checks. |
| Financial integrity | Reporting sections and notes retain FCMS's independent proposal/review process. A structural default does not approve reporting metadata or post financial transactions. |

Deliberate adaptations remain: company-owned FCMS accounts and claims, existing identity/code conventions, FCMS page sizes, lifecycle rules and maker/checker reporting. Microfinance's tenant-specific lending controls, UUID identities, version proposals and automatic reporting-role publication are not transplanted. No additional fixed posting-account catalog exists in the compared default provisioner.

Regression coverage includes an already-V34 chart with missing asset defaults, other missing default groups, compatible customized groups and conflicting definitions; retained custom family/posting records, atomic rollback and repeat migration are checked. Existing PostgreSQL COA/reporting/configuration tests and shared interface tests remain required. Deployment evidence is retained under ignored `.verification/coa-review-20261117/`.

## Verification and local activation

- `mvn -q -Dtest=CoaTreePostgresTests,AccountReportingPostgresTests,TransactionConfigurationTests,ArchitectureTests,ResourceFeedbackTests test`: 34 tests passed, none skipped. This includes compilation, nine PostgreSQL COA tests and native JSP/CSRF/authorization/feedback checks.
- `npm run test:ui`: 79 tests passed, including eleven focused COA checks.
- `mvn -q -DskipTests package`: passed; the deployed migration, account form, register and tree script match the tested workspace payloads.
- `python scripts/verify-agent-references.py`: passed. Asset synchronization and affected-file whitespace checks passed.
- A new PostgreSQL 18 schema backup was restored into a separate database. All 51 tables and sequence values matched before rehearsal. V34's already pending retired-period permission cleanup and V35 were rehearsed and applied to the retained loopback app schema. Comparison allowed exactly that named permission cleanup and the three missing defaults; all existing account records and financial/reporting records remained unchanged.

The updated local application uses port 8093, the existing preview session-cookie name, retained avatar/contract/backup directories and disabled SMTP. Application rollback can use the retained prior runtime; retain V35 and the additive defaults. The private backup, restore proof, fingerprints, migration logs, tested WAR identity and restart script remain in the ignored evidence directory. No production deployment or capacity result is claimed.

The browser-control service timed out on `cua.getState()`. No real-browser screenshots, keyboard exercise or six-width visual certification could be completed; native HTTP and DOM tests are separate evidence.

Post-start PostgreSQL verification confirms all ten active defaults, correct asset ancestry and unchanged fingerprints for all twelve existing financial/reporting tables and every original account. Readiness is UP, and the retained administrator signs in successfully. Authenticated live COA navigation returns 403 because V30's existing role defaults separate Administration from Accounting; the current local schema retains only that administrator. Authorized COA JSP/form checks passed in the isolated PostgreSQL integration tests, but live authorized navigation remains pending the user's choice about access. No permissions or passwords were changed. Granting access requires the existing `workspace.accounting`, `accounting.read` and `accounting.setup` claims; it is an account-permission change, not a migration repair or reason to weaken route authorization.
