# FCMS Spring Migration

This is the new Spring Boot migration workspace for the FCMS PHP/XAMPP project.

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

The current project is a Spring Boot/JSP/Tailwind migration shell. The
provisional legacy-derived PostgreSQL schema has been removed.

Application tables and seed data will be introduced through new Flyway
migrations after FCMS requirements are defined during development. Hibernate is
set to `ddl-auto: none` so the application does not create or validate a schema
implicitly.

The legacy PHP project remains untouched.
