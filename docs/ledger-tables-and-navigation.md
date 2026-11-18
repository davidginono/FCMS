# Ledger tables and sidebar

The Cloud Backup, Bulk SMS and Petty Cash registers share `ledger/index.jsp` and the existing resource-table component. Their totals now use compact labeled `resource-table-totals` instead of large fact cards. Amount headings identify TZS; reference links remain protected identifier columns. Existing server filters, bounded pagination, sorting, exports, creation operations, reconciliation and selected-record View commands retain their owners and authorization.

All workspace ERP table empty states use the same `resource-table-empty-message` wrapper, including existing client/voucher markers and locally generated no-match rows. A container-sized, horizontally sticky wrapper centers copy in the visible row canvas at every scroll position. The shared initializer retains original nodes and recovery controls, applies again on row refresh and leaves real financial totals and loading rows alone. Financial-statement presentation is excluded. The Microfinance table/style skills document this rule.

Authorized sidebar groups follow Dashboard, Clients, Sales, Accounting Office, Reports, Operations and Administration. Accounting Office orders Transactions, Vouchers and Configuration. Parent groups use native expanded-by-default details/summary controls and the shared filled-triangle tag, with consistent indentation, focus treatment and native collapse/reopen. Permissions and staff workspace destinations are unchanged. The governance skill now records this contract. Reference-manifest hashes were updated for the three adapted skills; the source snapshot was not modified.

## Verification on 18 November 2026

- `npm run test:ui`: 90 tests passed. An additional no-match regression was then added; the final focused table/navigation run passed all 25 tests.
- Headless Edge fixture rendering: 108 centering/overflow checks across the three registers, English/Kiswahili, widths 320, 375, 768, 1024, 1440 and 1920, and left/middle/right horizontal scrolling. Screenshots were inspected at mobile and desktop sizes. These are component fixtures using the actual shared assets, not authenticated JSP route evidence.
- Sidebar source-markup fixtures: 12 browser checks across the same widths and languages verified expanded defaults, keyboard collapse/reopen, filled triangle rotation and unclipped navigation.
- `python scripts/verify-agent-references.py` and focused `git diff --check`: passed. Final source, WAR and preview UI bytes match in both asset roots.
- Application compilation and a test-skipped WAR build passed. The attempted Java test run was blocked during test compilation by pre-existing `AccountingVoucherPdfTests` constructor arguments and `BusinessWorkspacePostgresTests` calls to `LedgerForm.getRows()`. No Java source or test was changed for this UI task.
- The local loopback preview on port 8093 received the eight changed UI files, including both static-asset roots. Previous UI files are retained under the ignored verification directory for rollback. Authenticated finance-route rendering was not completed: the retained synthetic finance account was unavailable, and the administrator account correctly received 403. No permission or account was modified to bypass that result.

Evidence and scripts are under ignored `.verification/ledger-tables-20261118/`; Maven/UI logs are under `.verification/ledger-table-*.log`. No schema, query, transaction, authorization, thread, financial-data or production configuration change is included. There is no capacity benchmark or performance claim.
