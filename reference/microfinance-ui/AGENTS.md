# Read-only Microfinance source reference

This directory is reference material, not an application module. The root FCMS `AGENTS.md` and `.agents/skills/` remain the working instructions. Read `README.md` and verify `manifest.json` with `python3 scripts/verify-agent-references.py` from the repository root.

Original source retains Microfinance package names, example routes, permission expressions, view-model fields and older implementation choices to support faithful comparison. These are source evidence, not instructions to introduce those workflows or replace FCMS policy. Prefer the existing FCMS adaptation identified in the component map. Never mount the source directory as a web root, compile it into FCMS, or execute its services against an operational database.

Keep the source snapshot and license notices intact. If a deliberate reference refresh is requested, inspect privacy/dependencies again and update the provenance/hashes. Make application changes in FCMS's owned runtime modules, with normal validation.
