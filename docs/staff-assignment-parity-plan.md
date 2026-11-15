# Staff assignment parity implementation

This slice implements open findingsD2/D3 in the active legacy parity goal. Sourceadd_expenses.php accepts selecteduser_id and financial-year metadata; admin_add_report.php accepts individual/group owners and report facts. Current Spring expense/report creation hard-codes owner to actor. Preserve canonical financial mutations and scope while restoring authorized assignment.

## Authorization and ownership

Add atomicexpense.assign andstaff-report.assign claims in a forwardV27 migration, seeded into the default admin bundle. Runtime guards use effective claims only. Existing owner/team/global review scopes remain. An assigner with read access can also read the records they created; assignment alone does not grant unrelated records. Staff cannot forge another owner. Validate selected recipients through the public IAM directory and reject inactive/nonexistent users. Keep lookups bounded and expose only minimal identities.

An authorized assigner can create/prepare another staff member's eligible draft and perform its submit/edit steps. Owners retain their current draft rights. Global reviewers need assignment permission to prepare someone else's draft. REPORT group membership includes the selected owner and selected participants, with the same bounded active-user validation; the creator is recorded separately and is not silently made a participant merely because they entered the report. Owner/creator review exclusions remain; add the missing participant exclusion at the service and database boundaries.

## Provenance, money and retries

Expenses need immutablecreated_by and latest-preparationprepared_by evidence plus a retained creation hash. Backfill only retainedexpense.save audit actors: first event for creator, latest event for preparer. Leave absent evidence unknown; never fabricate a historical actor. Every new native creation explicitly records both actors. Edits retain initial creator/creation hash and set the actual preparer. Expense ownership/preparation becomes immutable when the request leaves an editable draft/rejected state. Retain existing paid-source, date/cutover, payment/reversal and audit/event guards.

Independent expense/report review must exclude the owner, creator and actual preparer; REPORT group participants must also be excluded. Do not allow an administrator who prepared an on-behalf request to approve their own work. Record reviewer/creator/preparer facts in native authorized details and relevant retained history. Creator/preparer unknown is shown as unavailable. Old reviewed/posted financial history is never rewritten to fabricate evidence.

Create requests serialize by authenticated maker/request UUID, retain a deterministic hash of the authorized owner and original exact form/year facts, and return the one result for identical retries. Changed payload under the same key fails; concurrent retries cannot create two expenses/reports. Ownership-selected request keys must not change the retry namespace. Updates continue optimistic version checking. Reuse the canonical save/event/audit paths; do not post to a GL/ledger directly.

## Expense financial year

Offer the configured financial-year choice already present in the legacy admin form, storing the selected reference's label without changing the actual expense/payment date. Preserve the existing date-year default when no explicit reference is submitted, so existing API callers remain compatible. Validate explicit reference IDs; no arbitrary free-form financial year from the browser. Draft edits may change year metadata, while reviewed/paid source rules retain immutability. Calendar/GL reports continue to use their existing actual date/payment-date policies. Document the distinction and verify both category/year filtering and actual-date financial snapshots.

## UI, schema and validation

Use current JSP forms, bounded staff lookups, selected-value recovery, readonly facts, shared action menus, native CSRF and PRG. Add no explanatory UI paragraphs/subtitles. EN/SW labels and eligibility must match server rules; hide commands that would self-review or escape record scope. Preserve live files/inputs and native submitters on errors. Keep module boundaries: controllers delegate; finance/workforce own their data and call public IAM services; shared references remain allowed through the reference repository.

V27 must preserve existing rows, add foreign-key and uniqueness/state/provenance guards, and update immutable review/reversal safeguards. Do not editV1–V26. Review existing DB guards/views/triggers before replacing any function. Update fixtures only for meaningful new actor evidence; never weaken financial assertions to pass.

## Evidence

PostgreSQL/native checks: custom claims, denied forged recipients, inactive users, creator/owner/unrelated scopes, explicit/default year, canonical stored values, exact retry conflict/one-result concurrency, stale edits, immutable maker history, owner/creator/preparer/group self-review denial, independent approval/payment/reversal, report member/owner transitions, source/cutover/GL invariants and migration backfill/unknown facts. Render actual create/edit/submit/review/return/history workflows in EN/SW at all six required widths. Run affected regressions and retain the overall fresh full-suite/browser/push gate for the end of the active goal.

D2 follow-through: the request UUID namespace moves from selected owner to actual creator; retain a separate legacy unknown-owner key guard and reject uncertain collisions without disclosing another record. Explicit year metadata is independent of actual dates; missing year on existing edits preserves the retained label, including labels with no current reference. New UI choices show a concise Keep-current option. Expense history category width must also match the255-character accepted form/schema field, with a full transition regression for a long category (findingD4).

Audit detail must retain both the accepted1,000-character command reason and prior1,000-character review; widen its bounded field to2,500 in unreleasedV27 and verify full content without truncation. Language links must retain selected report year/category/date basis while excluding credentials and mutation request keys.
