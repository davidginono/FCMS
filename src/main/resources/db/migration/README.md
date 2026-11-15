# Database Migrations

Flyway migrations for the PostgreSQL FCMS schema live here.

`V1__initial_schema.sql` is a clean Spring-era baseline. Future migrations
should evolve from confirmed FCMS requirements instead of copying the old
PHP/MySQL schema directly.

V24 introduces additive COA reporting metadata and the approved inheritance view. Its partial uniqueness, immutable state transitions and deferred replacement constraint preserve independently reviewed history. It seeds no financial treatment and modifies no balances. See docs/coa-reporting-and-notes.md for migration/workflow checks and rollback limits.
# V25 equity reporting

V25 adds retained, independently reviewed equity report setup and journal movement classifications. It does not rewrite journals, ledger lines or balances. Keep its metadata and audit history when rolling back an application artifact. See `docs/cash-equity-and-drills.md` for behavior and verification scope.
