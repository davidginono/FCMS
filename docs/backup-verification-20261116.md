# PostgreSQL 18 backup verification — 16 November 2026

## Fix

The local PostgreSQL server is 18.3, while the prior PATH resolved `pg_dump` 17.9. The old preview also overrode the backup worker's ten-second polling interval with 24 hours.

`application.yml` now exposes `FCMS_BACKUP_PG_DUMP` and `FCMS_BACKUP_INTERVAL`, with a portable executable fallback and the existing 10,000-ms worker default. README and operations document selecting PostgreSQL 18 explicitly and removing stale command-line overrides. The owned local launcher now selects the installed PostgreSQL 18 executable and 10-second polling; its former entrypoint delegates to the corrected launcher. SMTP configuration is unchanged.

This changes configuration, not financial services, permissions, migrations or scheduler implementation. Polling checks for requested jobs; it does not create a backup every ten seconds. Restores remain operator-controlled and target a fresh database.

## Build and deployment

- Offline Maven package passed the affected `PostgresModuleTests#backupHistoryUsesServerFiltersAndDeterministicPages` and `ArchitectureTests`: **3 checks, zero failures/errors/skips**. A fresh isolated verification database was used.
- WAR SHA-256: `d069a16d571cbad5fa2fec752b087cbe384b0cf3a900895064964122c7cd62d6`; 743 safely extracted file payloads matched the archive.
- Only the identified former FCMS process was stopped after checking artifact, database and port ownership. The corrected immutable runtime serves loopback port 8093 and retains the existing source database/schema. No schema migration was required by this fix.

## Native request, download and restore

`scripts/verify-backup-restore.py` ran against the existing synthetic local fixture. It submitted the native CSRF-protected form once per verification run, allowed the actual scheduled worker to execute `pg_dump`, downloaded through the application, and restored with PostgreSQL 18 into a newly created database. No browser/Computer Use automation or manual production-data restore was used.

Final acceptance:

| Check | Result |
| --- | --- |
| Server and dump/restore tools | PostgreSQL **18.3** |
| Worker lifecycle | **QUEUED → RUNNING → READY** |
| Measured request-to-worker delay | **7.12583 seconds** with 10-second polling |
| Native archive | **5,788,968 bytes**, custom `PGDMP` format |
| Download SHA-256 | `0aeed94e052bae62e9e06492d676dde401a42f9986a3b470653f1f769a88e601` |
| Download integrity | Download size matches catalog; SHA-256 matches the private worker file |
| Response controls | Attachment, application/octet-stream, no-store |
| Request without CSRF | 403; no backup record admitted |
| Anonymous download | Redirected to login; archive not exposed |
| Authenticated reader without backup.manage | Backup page and actual archive download both 403 |
| Restore target | Fresh `fcms_codex_verify_backup_restore_c02b55a0284b`; source was never overwritten |
| Row data | **888 tables** compared using counts and SHA-256 row digests |
| Sequence states | **602** matched |
| Schema | Columns, constraints, indexes, triggers, views and functions matched; 26,603 definitions |
| Current application schema | Restored Flyway version **27** |

The backup's own job row is RUNNING inside its consistent archive and READY after the worker finishes. Its UUID, requester and creation/start timestamps match; its expected later completion fields are checked separately. All other retained rows match exactly.

The first schema comparison flagged PostgreSQL's equivalent representation of literal varchar-array casts: an array-wide cast to text[] was reparsed into individual literal casts to text. No data or sequence differed. The verifier canonicalizes only this precise representation in constraints/indexes/views. Names, allowed values, operators, ordering and types are retained. Six Python regression tests cover equal representations and ensure changed values/operators/order/types, length-constrained casts, quoted SQL and comments are not masked. The final native request/download/restore was rerun with this comparator and passed. The earlier successful archive and isolated restore remain retained, without presenting the initial comparison failure as a pass.

Evidence remains private under `.verification/backup-fix-20261116/`: build/startup logs, runtime identity, native/results.json, authorization.json, source/restore snapshots, downloaded archive and restore log. The final native backup UUID is `6fff410e-7850-4813-b083-d5f2a1fc932b`.

## Limits and rollback

These are database archives: uploaded contracts/avatars, old dump files, PostgreSQL cluster roles and deployment secrets need separate storage/configuration recovery. Full production deployment, disaster-recovery procedures and capacity are not certified by this local fixture run.

No released migration was changed. To roll back configuration, retain the matching prior artifact/launcher and settings; do not delete backup/audit evidence or restore a dump over the running database. The original 24-hour polling and PostgreSQL 17 executable selection are historical defects, not recommended rollback settings.
