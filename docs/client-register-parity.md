# Client register parity

Authority: the current canonical root files in `reference/fcms-php-reference.zip`, verified against its SHA-256 file. During the 17 November 2026 implementation these matched the local PHP FCMS source for the compared register/create/update helpers. The local MySQL server was unavailable; no PHP entrypoint or operational MySQL mutation was executed. The older schema-only dump does not contain all later category tables: current SQL statements, shared handlers and the lifecycle SQL migrations supply the remaining schema contract.

| Register | PostgreSQL table | Category fields and actions |
| --- | --- | --- |
| SACCOS | `saccos` | Annual/paid amounts, withholding tax, commission, programmer/install/training/Android/Android cost/reinstall/SMS/hosting/cloud backup fees and costs; invoice Yes/No. Fiscal year at create. Date paid and ESS at edit. Separate contract upload. |
| AMCOS | `amcos` | Annual/paid amounts, programmer/install/training/Android/reinstall/SMS/hosting fees. No tax or commission. Fiscal year at create; immutable on edit. Separate contract upload. |
| Union | `unions` | AMCOS fields plus withholding tax and commission. Fiscal year at create; immutable on edit. Separate contract upload. |
| Microfinance | `microfinance_clients` | Annual/paid amounts, install/training/Android/reinstall fees. Programmer cost at edit. Fiscal year editable. No SMS or hosting. Separate contract upload. |
| Other Clients | `other_clients` | Annual/paid amounts, programmer/install/training/Android/reinstall fees. Optional contract at create, dedicated contract replacement afterward. All-dates register, no fiscal-year input or operational status. |

All five use payment status (Paid/Unpaid/Pending). The first four also use independent Active/Inactive operational status. Contracts are private inspected PDFs, downloaded only with client access. Existing SACCOS CSV import and approved PDF stationery remain available; the Other Clients PDF retains its original year column, which is blank for unassigned records, while its CSV and workspace use the all-dates schema. Monetary values use exact PostgreSQL numeric/Java BigDecimal instead of the legacy varchar money fields and invalid zero dates are not reproduced.

The full registers use bounded server paging, an allowlisted sort for each displayed column, shared selection-dependent Actions and existing table preferences. Creation uses the shared resource flow: native validation, field errors, preserved live input, PRG to the canonical register and a persistent destination Flashbar. Edits preserve immutable fiscal years server-side. A contract update is a separate version-checked command and cannot overwrite client fees.

Lifecycle mirrors `client_delete.php`: explicit confirmation; version checking; Mark inactive leaves payment status, amounts, visibility and references unchanged. Permanent deletion is offered only for unused clients with zero financial values, no payment date, no contract and no declared foreign-key references, including future deployed additions. The native database foreign keys protect reference insertion races. Existing archive/restore remains separate from operational inactivity. Authorization uses FCMS `client.read` and `client.write`, not PHP's numeric administrator-role convention.

V36 converts SACCOS Dormant to Inactive and adds Active/Inactive constraints to AMCOS, Union and Microfinance. It deliberately retains historically entered AMCOS tax/commission and Other Client year assignments; these are not writable through the corrected forms. No rows or financial values are deleted by the migration. Rollback requires reverting the runtime together with the pre-upgrade database backup because older runtimes do not recognize Inactive.

Deliberate PHP corrections: the Other Clients edit/contract pages contain copied SACCOS/Microfinance bindings; Spring targets the actual Other Client handler/table. SACCOS/AMCOS/Union's `Entry Date` input-name typo is corrected. Unsafe SQL interpolation, unbounded lists, zero dates, GET mutations and unrestricted uploads are not reproduced.


## Verification on 17 November 2026

An isolated source snapshot was compiled and packaged with Maven. The combined relevant runs passed 82 distinct Java/PostgreSQL tests (none skipped) and `npm run test:ui` passed 84 checks. Coverage includes all five native create/edit/register flows, English/Kiswahili rendering, field/date validation, year-preserving success redirects, every declared server sort, private native multipart contract validation/downloads, stale uploads, permission/CSRF denial, competing lifecycle commands, declared-reference deletion guards, archives, CSV import and PDF export regressions. A dedicated V35-to-V36 test retained every row and monetary value while reconciling the intended statuses.

The local preview on port 8093 was backed up and upgraded to V36. Before/after hashes matched across all 50 existing tables after excluding the deliberately changed operational-status columns; both existing users were retained. The installed client payloads, all runtime classes and every library match the verified WAR; unrelated runtime presentation files were preserved. The deployed login and protected client route checks passed. Detailed artifacts are under the ignored `.verification/client-parity-20261117/` directory.

The browser connection timed out, so no visual/responsive browser verification is claimed. The local MySQL server was unavailable; PHP source and migration/schema references, rather than live MySQL rows, provided the legacy contract. No capacity or load-test claim is made.


### Table layout follow-up — 18 November 2026

Empty registers now size columns from readable header minimums, rather than relying on data cells. Headers wrap at word boundaries; amounts and dates remain intact. Horizontal overflow belongs to the row canvas, and the empty message stays visible within that canvas while scrolling. Currency context comes from the localized labels once. These changes were verified in Chromium with empty/populated data at all six required widths and with English/Kiswahili labels, including table preferences and selection-dependent Actions.
