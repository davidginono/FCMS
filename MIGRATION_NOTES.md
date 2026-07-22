# FCMS Migration Notes

## Current Scope

- Project foundation: Spring Boot 4.1.0, Java 25, Maven WAR packaging.
- JSP/Tailwind UI shell.
- Flyway is configured, but no application schema migrations are currently
  defined.
- Initial MVC package structure is present and will be reshaped as requirements
  are confirmed.

## Legacy Source Scope

Cleanup candidates from the PHP tree are intentionally ignored for this pass.
The current PHP application is treated as read-only source material.

## Schema Direction

The provisional legacy-derived PostgreSQL schema has been removed. New tables,
relationships, constraints, indexes, and seed data will be generated from the
requirements provided during development.

## Next Migration Slices

1. Capture a bounded requirement slice and acceptance rules.
2. Design the PostgreSQL tables and relationships for that slice.
3. Add a Flyway migration, matching JPA models, repositories, DTOs, and MVC
   controllers.
4. Add focused tests for schema assumptions and business behavior.
5. Repeat until the PHP routes can be retired.
