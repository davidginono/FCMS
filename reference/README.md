# Legacy FCMS PHP reference

[fcms-php-reference.zip](fcms-php-reference.zip) contains the PHP/XAMPP source snapshot used to reference legacy FCMS workflows. Snapshot date: **2026-11-16**. Its top-level directory is `fcms/`.

The snapshot comes from the current `C:/xampp/htdocs/fcms` directory, including the newer prospects and invoice workflows. The older `C:/xampp/htdocs/fcms.zip` archive is not bundled: it exceeds GitHub's normal file limit and is not the current sanitized source snapshot.

## Use from a fresh checkout

Verify the ZIP against [fcms-php-reference.zip.sha256](fcms-php-reference.zip.sha256), then extract it outside Spring's web root and build output. For example, from the repository root:

```powershell
Get-FileHash reference/fcms-php-reference.zip -Algorithm SHA256
Expand-Archive -LiteralPath reference/fcms-php-reference.zip -DestinationPath .verification/legacy-php
```

The PHP entrypoints are under `.verification/legacy-php/fcms/`. Inspect them as reference source; do not deploy this archive or run it against an operational database. Keep changes in the Spring modules unless the task explicitly requests a PHP change.

On Linux/cloud, from the repository root:

```bash
(cd reference && sha256sum --check fcms-php-reference.zip.sha256)
python3 -m zipfile -e reference/fcms-php-reference.zip .verification/legacy-php
```

The checksum check runs inside `reference/`, where its ZIP filename resolves. The [portable Microfinance UI skills and source components](microfinance-ui/README.md) are also included for agents without the original Windows checkout.

Start with the root PHP files. `CODES/`, `CODE2/` and `images/` contain historical application copies and may differ from the canonical root workflows. They are retained for source comparison, with the same credential sanitization.

## Workflow map

| Area | Starting points inside `fcms/` |
| --- | --- |
| Login, workspace separation and dashboards | `index.php`, `auth_workspace.php`, `client_lifecycle.php`, `admin.php`, `admin_sidebar.php`, `admin_navigation.php`, `user_dashboard.php`, `user_navigation.php`, `tests/user_workspace_test.php` |
| Clients and AMCOS | `saccos.php`, `amcos.php`, `union.php`, `microfinance.php`, `other_client.php`, corresponding `add_*`, `update_*` and `view_*` files |
| User administration and accounts | `users.php`, `add_user.php`, `update_user.php`, `account.php`, `user_account.php`, `change_password.php` |
| Leave, tasks and activity reports | `leaves.php`, `user_leaves.php`, `all_task.php`, `user_tasks.php`, `staff_report.php`, `user_staff_report.php` |
| Expenses and operational ledgers | `expenses.php`, `income.php`, `petty_cash.php`, `cloud_backup.php`, `bulk.php` |
| Backups | `system_backup.php`, `backup.php`, `download_backup.php` |
| Prospects, proformas and invoices | `prospects.php`, `invoices.php`, `invoice_pdf.php`, `print_invoice.php`, `print_prospect_proforma.php` |
| Original operational export layouts | Root `print_*.php` files, `fpdf_report_helpers.php`, `fpdf/`, `tcpdf/` |
| Database structure and later migrations | `database/fcms_db.schema.sql`, dated SQL scripts and database workflow documentation |
| Existing legacy checks | `tests/` |

For Spring GL income, financial-position, cash-flow, equity, trial-balance and account-ledger presentation, follow the Microfinance authority and the documented FCMS-specific changes in the root [AGENTS.md](../AGENTS.md), [financial report source review](../docs/financial-report-source-review.md) and [cash/equity/drill implementation](../docs/cash-equity-and-drills.md). The legacy PHP blue income-statement layout does not supersede those instructions. Preserve FCMS claims, financial approvals and localization rather than importing unsafe legacy implementation details.

## Snapshot contents and sanitization

PHP workflows, tests, front-end source/static assets, PDF libraries, fonts, licenses and database migration source are retained. Credentials and literal mail identities are replaced with environment-variable references, empty class defaults or `.invalid` addresses in the archived copies. The original PHP directory is unchanged.

Uploads, profile pictures, sessions, backups, logs, local configuration, nested archives, generated documents, Git history and agent-local files are omitted. Dumped database rows are excluded; `fcms_db.schema.sql` contains only structural CREATE/ALTER statements, with live auto-increment counters reset. This is a source reference, not a backup or a deployable copy.

[fcms-php-reference.manifest.json](fcms-php-reference.manifest.json) records the archive hash, included file count, exclusion counts and sanitized paths. The ZIP's `fcms/REFERENCE-MANIFEST.json` also contains every included source file's size and SHA-256 digest.

To refresh from an authorized local PHP source checkout:

```powershell
python scripts/package-legacy-reference.py --source C:/xampp/htdocs/fcms --date 2026-11-16
```

Review the resulting redactions and exclusions, verify ZIP integrity and the manifest, and lint changed PHP copies before publishing an updated snapshot. The builder reads PHP and SQL as text; it does not execute either language or connect to a database.
