# FCMS migration notes

Legacy workflow source: C:/xampp/htdocs/fcms (read only). Microfinance supplies inspected reusable JSP UI components, not FCMS business rules.

## Implemented migrations

- V1–V3: initial/reference data and client-category registers.
- V4–V6: effective claims, audit, workforce workflows and AMCOS.
- V7–V8: immutable operational ledgers and expense reversals.
- V9–V12: password recovery/outbox, backup jobs, client versions and report teams.
- V13–V14: public directory contracts and audit correlation IDs.
- V15: chart of accounts, reviewed books/opening balances, accounting periods, balanced journal review/posting and exact reversals.
- V16: retained expense payment evidence/history and the Finance-owned GL-source read contract.
- V17: immutable posting-mapping revisions with independent review and atomic replacement.
- V18: source-linked preparation, retained attempts, exact operational reversals and reviewed cutover coordination.
- V19: source coverage, atomic period closing and Finance-owned closed-date guards.
- V20: retained legacy payment candidates, independently reviewed payment evidence and explicit opening-policy coverage.

## Compatibility and financial provenance

Existing client and expense identifiers are retained. V5 copies existing tasks into work_items once. New operational ledgers do not silently copy PHP money records. Historical imports and opening balances require reconciled source evidence and explicit account treatment. The general ledger is separate from legacy fee reports; client-fee snapshots are never presumed to be cash receipts.

Use forward migrations for released schema changes. Rollbacks require a compatibility assessment and must preserve financial/audit history. Back up PostgreSQL and private contract storage before rollout.

Operational-source preparation, linked reversals and source-coverage/closing guards passed the 39-test PostgreSQL/build gate. Historical payments with missing dates remain quarantined until separately retained evidence is independently approved. V20 and its UI/service workflow passed the fresh complete 43-test regression/package gate, including the isolated legacy upgrade. The V20 database backup/restore counts and financial content digests also match. Rendered browser acceptance remains outstanding. The overall implementation and rendered acceptance are not yet complete. See docs/implementation-plan.md and docs/operations.md.

## Reporting and configuration migrations through V24

V21 adds reviewed Activities/Transactions configuration; V22 redacts terminal mail bodies; V23 adds prospective-client and standalone invoice persistence. V24 adds immutable independently reviewed COA reporting definitions, actual-parent section/note inheritance, retained replacement evidence and bounded current/pending/history indexes. It does not alter monetary records or invent account roles. Notes and grouped report routes, review/history, scoped proposal access and exact original exports are accepted for this increment. See docs/coa-reporting-and-notes.md for the 54 affected checks, 71 browser checks, 42 original-renderer pages, final runtime and remaining full-goal gaps. Retain V24 history on rollback; do not delete financial/audit evidence.
