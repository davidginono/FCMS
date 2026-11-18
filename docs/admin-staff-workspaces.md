# Admin and staff workspaces

This records the V28 implementation. Current role defaults, landing pages and navigation are superseded by [Business workspaces](business-workspaces.md) and V30/V31.

Migration `V28__personal_staff_workspace.sql` separates workspace access from business capabilities. The administrator role bundle receives `workspace.admin`; the user role bundle receives `workspace.staff` and loses its former company-wide `client.read` grant. Authorization remains claim-based, with no role-name checks in Java or JSPs.

| Workspace | Login destination | Records and actions |
| --- | --- | --- |
| Admin | `/admin/dashboard` | Existing modules, limited by their individual read/write/review/export claims and existing approval rules |
| Staff | `/staff/dashboard` | Own tasks, activity reports, leave requests and expenses; personal account settings remain in the profile menu |

`/dashboard` remains a compatibility entrypoint and renders the current workspace. The opposite workspace dashboard returns 403. Successful login clears any saved request and authentication error, preserves Spring's session-fixation protection and always opens the assigned workspace.

`WorkspaceClaims.effective` is shared by login authorities and the database-backed `AccessService.Actor`. Without an explicit `workspace.admin` grant, effective capabilities are limited to personal dashboard/account access, the four workflows' read/write permissions and bounded client-name lookup. Legacy or accidental company-wide read, assignment, review, financial, export and administration grants cannot enlarge staff scope. A custom administrative account needs **both** `workspace.admin` and each intended capability; the workspace claim grants no module permissions itself. Account/permission changes continue to revoke sessions after commit; service guards also re-read active status and effective claims. The last-administrator safeguard counts only active accounts with both workspace access and user-management permission.

Staff workforce queries require `owner_id` for list, detail, counts and exports. Being a group participant or the creator of someone else's report does not grant visibility in the staff workspace. A staff-owned group report still retains its participant facts, while its activity export includes only the owner's row. Administrative participant, assignment and reviewer rules remain available with their specific claims. Expenses use the same owner scope for registers, details, category totals and PDFs. Submitted owner filters cannot widen access. Commands preserve optimistic versions, request-key idempotency, auditing, CSRF and independent approvals.

The personal dashboard uses four database counts across all dates and does not query global client, revenue, ledger or financial-year summaries. Its navigation uses the existing Microfinance shell, search, profile controls, responsive rail and spinner-only workspace transitions. English and Kiswahili labels are provided. Staff task/report forms retain the bounded public client ID/name selector through `client.lookup`; it grants no client-register, contract, fee or financial-report access. Existing group-report staff-name selection remains bounded.

The PHP reference snapshot was refreshed from the authorized local checkout on 2026-11-16. It includes `auth_workspace.php`, `user_navigation.php`, the owner-scoped personal dashboard and `tests/user_workspace_test.php`. Archive CRC, all 1,408 manifest hashes, checksum and syntax of 19 changed PHP copies were checked without executing legacy workflows or SQL. The source checkout was not modified.

Apply V28 through normal Flyway startup. No financial data is rewritten, and existing owner indexes serve the scoped queries. Review any custom accounts that previously depended on administrative capability grants without workspace access; deliberately assign the workspace claim only when administrative access is intended. Keep V28 and roll forward for fixes rather than restoring the previous broad staff-access behavior.

Verification uses PostgreSQL 18 integration tests, native HTTP login/form/export requests and jsdom shell tests. No browser or Computer Use checks are used.

## Verification on 2026-11-16

- `mvn -o -B -Dspring.test.context.cache.maxSize=2 -Dspring.datasource.hikari.maximum-pool-size=6 -Dspring.datasource.hikari.minimum-idle=0 package`: 264 tests passed, zero failures/errors/skips, against a fresh isolated PostgreSQL 18 database. This includes eight workspace integration tests and the existing accounting, financial approval, export, authentication and architecture checks.
- `npm run test:ui`: 20 tests passed.
- `python scripts/verify-agent-references.py`: passed for four skills and 110 reference files.
- Native HTTP checks against the restarted app: 14 authenticated pages passed, nine administrative routes returned 403 for staff, and the staff dashboard returned 403 for the admin account. Both languages, the bounded client selector and no-store responses were checked.
- jsdom checks against captured server HTML and the deployed assets: 60 workspace transitions passed with zero script errors, spinner-only pending contents, preserved shell nodes and removal of administrative navigation after an authorization downgrade. Shell behavior was exercised at simulated widths of 320, 375, 768, 1024, 1440 and 2560 px. Visual browser layout checks were omitted as requested.
- PHP archive checksum/CRC, every manifest hash and syntax of all 19 changed PHP copies passed. Archive SHA-256: `4831435618e6fa9c0b49be54831cb0a32c4194a7d85f0afcde8190d5503ada58`.

The verified WAR was restarted at `http://127.0.0.1:8093` with the retained preview database and V28 applied. All 755 extracted artifact payloads were verified. PostgreSQL 18 backup tooling and the ten-second backup worker interval were retained. Detailed local evidence is under ignored `.verification/staff-workspaces-20261116/`.
