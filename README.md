# FCMS Spring Migration

This is the new Spring Boot migration workspace for the FCMS PHP/XAMPP project.

Cloud agents can use the [repository-local skills and Microfinance UI reference](reference/microfinance-ui/README.md) without the original Windows folders. Read [AGENTS.md](AGENTS.md), then run `python3 scripts/verify-agent-references.py` to verify the bundled source, licenses and portable skill links. The [legacy PHP reference](reference/README.md) includes Linux/cloud extraction commands.

The sanitized [legacy PHP source ZIP and workflow guide](reference/README.md) are included for agents and developers working from a fresh checkout.

## Stack

- Spring Boot 4.1.0
- Java 25
- JSP views packaged as a WAR
- Tailwind CSS 4
- PostgreSQL with Flyway-managed migrations

## Local Setup

Create a PostgreSQL database and user:

```sql
create database fcms;
create user fcms with encrypted password 'fcms';
grant all privileges on database fcms to fcms;
```

Build CSS:

```powershell
npm install
npm run build:css
```

Run:

```powershell
$env:FCMS_DB_URL='jdbc:postgresql://localhost:5432/fcms'
$env:FCMS_DB_USERNAME='fcms'
$env:FCMS_DB_PASSWORD='fcms'
mvn spring-boot:run
```

The application starts at http://localhost:8080.

Or start an isolated PostgreSQL container on port 55432:

```powershell
docker compose up -d
$env:FCMS_DB_URL='jdbc:postgresql://localhost:55432/fcms'
$env:FCMS_DB_USERNAME='fcms'
$env:FCMS_DB_PASSWORD='fcms'
mvn spring-boot:run
```

## Migration Status

FCMS now includes company client registers (SACCOS, AMCOS, Union, Microfinance and
Other Clients), staff tasks/reports/leave, expense review and payment workflows,
income/petty-cash/cloud/bulk-SMS ledgers, user administration, account recovery,
financial statements and database backup workflows.

`V1__initial_schema.sql` creates the first operational slice: users, roles,
account statuses, financial years, SACCO clients, tasks, expenses, and reference
statuses. Hibernate is set to `ddl-auto: validate` so model/schema mismatches
fail during startup instead of hiding until runtime.

Development login (non-production profiles only):

```text
Email: admin@fcms.local
Password: ChangeMe123!
```

Production startup rejects the enabled development bootstrap credential. Prepare a unique
administrator password or another authorized administrator before disabling the development
account; see the production bootstrap procedure in operations. The prod profile also rejects
the published development password during authentication and password changes.

The legacy PHP project remains untouched. See [operations](docs/operations.md) for
mail, backups, claim scopes, ledger semantics, verification and rollout requirements.
Implementation acceptance is tracked in [the completion plan](docs/implementation-plan.md).

Reviewed COA reporting setup and numbered Notes are available from Accounting. Setup proposals retain independent review, approved inheritance and replacement history; statements retain dated account/transaction drills and original Microfinance print/PDF formats. See [scope and verification](docs/coa-reporting-and-notes.md). The broader module/export goal remains in progress.
