# SACCOS CSV import implementation contract

The overall legacy FCMS completion goal remains active. Report/export acceptance is recorded in `cloud-completion-20261115.md`. Next implement the intended workflow in legacy `upload_tamplate.php`: its form sends `pfile` while its handler reads `csv_file`, and its insert targets otherwise-unused `saccos2`. Do not execute that entrypoint or reproduce those defects.

## Authorized scope

- Add a native SACCOS CSV upload, template download, private preview and explicit import command. Use the existing Cloudscape JSP shell/components, EN/SW labels, required financial facts and concise validation; no explanatory paragraphs/subtitles.
- Preserve all existing client/financial invariants. New records only; no CSV-selected existing record IDs, uploaded contract paths, balances, actor IDs, approval state or direct GL/ledger writes.
- Add an atomic `client.import` claim in a new forward V26 migration and seed it into the default admin claim bundle. Runtime checks remain claim based. Require `client.read`, `client.write` and `client.import`; preview/commit must also query by the authenticated batch owner.

## Parsing and staging

- Inspect the original 17 legacy column mappings and current SaccosForm/Saccos.apply before implementing. Support the legacy header names with explicit allowlisted modern optional fee/date columns where useful. Supply a valid downloadable template. Reject ambiguous duplicate/unknown fields and any attempt to import contract paths or existing IDs.
- Bound uploads to1MiB and100 rows, with strict UTF-8/RFC4180 parsing, column/field bounds, explicit ISO dates, nonnegative exact two-decimal money and valid reference IDs. Do not infer payment status, receipt dates, opening coverage or financial year from amounts.
- Reuse UploadInspectionService outside every database transaction. Use bounded admission and validate all rows before any client write. Do not log file contents/names/financial row data.
- Store validated rows privately in a bounded batch payload with owner, request UUID, SHA256, state, creation/expiry timestamps and row count. Identical upload retries return the same batch; changed content under the same request key fails. Expired/discarded drafts lose their raw payload when cleaned through a mutation, retaining audit facts.
- Preview exact rows/fees/costs and whole-batch totals with bounded paging. Native commit forms carry only the owned batch token and CSRF; never accept a browser-supplied replacement payload or resulting totals.

## Atomic creation and provenance

- Commit locks the owned batch row. Recheck claims, expiry, row validation and referenced years/statuses before writing. One transaction creates every client and records their ordered batch associations plus audit evidence. Any failure rolls back all clients/audits/batch state; concurrent retries return the one retained result.
- Reuse SaccosService's canonical applyForm/entity/audit path through a bounded public batch creation use case. Avoid per-row authorization/reference lookup loops; resolve/validate bounded reference sets once and retain canonical JPA/domain checks.
- Important: SaccosStorageService.storeContract rejects an active transaction even for a null file. Do not call the existing createSaccos(form,null) inside an outer batch transaction. Introduce a canonical no-contract batch use case in SaccosService, sharing record creation logic, without weakening file/scanner transaction guards or copying direct financial table mutations into the importer.
- Use proper batch/result foreign keys and state/payload invariants in the migration. Preserve committed evidence and native authorized details links. Batches have no authority to bypass source/cutover/period/financial database protections.

## Required evidence

Unit tests: strict quoting/UTF8/limits, invalid headers/rows/dates/money, no fabricated fields, precise aggregate values. PostgreSQL/native tests: claims and owner denial, no client writes on invalid input/preview, atomic rollback, upload/commit idempotency, concurrent one-result commit, reference changes, expiry, immutable provenance and retained audit/source effects. Browser: actual upload/preview/commit, invalid recovery with live file inputs, native CSRF, EN/SW, six required widths, and correct destination clients/export values. Run meaningful affected checks and a fresh full regression before accepting the implementation. Do not mark the overall goal complete while CSV/remaining legacy inventory or long/empty export gates are unresolved.

## Export compatibility follow-through

Before sending CSV headers or opening a response writer, resolve the bounded result and reject the1001st sentinel row with the existing localized narrow-report error. Preserve filters, scopes and sorting. Reuse the native import screen for the legacy upload GET and the year-filtered data export for the legacy download route; no mutation bypass or CSV-supplied record/contract IDs. Verify oversized register/revenue requests return a normal error response without a partial attachment.

## Spreadsheet text encoding

The final export review found that quoted arbitrary client names can still be interpreted as spreadsheet formulas. Centralize CSV text quoting for both client controllers, prefix formula-leading text (including leading whitespace) with an apostrophe, and retain every original stored value and numeric amount. Cover ordinary comma/quote/newline/Unicode text, formula prefixes and native register/revenue responses. This changes exported text encoding only; it does not reject or mutate client names.
