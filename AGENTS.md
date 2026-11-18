# Project Instructions for Codex

## 1. Purpose
This repository contains a production-oriented Spring Boot, PostgreSQL, JSP, and Tailwind CSS application. Treat every change as potentially affecting concurrent users, financial or sensitive data, security, database integrity, maintainability, accessibility, and operations.

These are repository-wide, non-negotiable rules. Task-specific workflows live under `.agents/skills/` and should be selected whenever the task matches their descriptions.

## 2. Required working method
Before editing code:
1. Read the relevant module, existing tests, configuration, database migrations, and nearest `AGENTS.md` or `AGENTS.override.md`.
2. State assumptions when requirements or performance targets are not measurable.
3. For cross-cutting, risky, database, security, or multi-module work, produce a short implementation plan before editing.
4. Prefer the smallest coherent change that solves the root problem.
5. Preserve established conventions unless they are unsafe or explicitly being replaced.

After editing code:
1. Compile the affected code.
2. Run relevant unit, integration, security, and UI checks.
3. Review the diff for regressions, secrets, unsafe logging, N+1 queries, missing authorization, transaction errors, and responsive-layout breakage.
4. Report what changed, commands run, results, assumptions, and remaining risks.
5. Never claim a performance, security, or capacity result without evidence.

## 3. Architecture
- Default architecture: modular monolith with explicit business-module boundaries.
- Organize modules by business capability, not only by technical layer.
- A module owns its domain rules, application services, persistence mappings, and public API.
- Do not access another module's internal repository, entity, implementation package, or database table directly unless the architecture explicitly permits it.
- Cross-module calls must use a documented public application interface, domain event, or integration contract.
- Prevent circular dependencies. Add an architecture test when boundaries are important.
- Controllers must remain thin: request mapping, validation, authorization handoff, application-service invocation, and response/view-model selection only.
- Business logic belongs in domain or application services, not JSPs, controllers, repositories, filters, or entity callbacks.
- JSP pages render prepared view models; they must not execute business rules or database queries.
- Do not introduce microservices merely for scale. Split deployment units only when independently deployable ownership, scaling, isolation, or regulatory needs justify the operational cost.

## 4. Spring technology choices
- Use Spring MVC for normal JSP applications.
- Do not mix Spring MVC and WebFlux casually.
- Use WebFlux only for a measured need involving end-to-end non-blocking I/O, streaming, large concurrent external I/O, or a separately isolated reactive component.
- Never wrap blocking JPA/JDBC calls in reactive types and call the result reactive.
- Prefer constructor injection.
- Use configuration properties for grouped configuration.
- Avoid static service access, service locators, field injection, and hidden global state.
- Keep framework annotations out of the domain model where practical.

## 5. Database and transaction rules
- PostgreSQL is the system of record.
- Use Flyway or the repository's established migration tool for every schema change. Never rely on automatic schema mutation in production.
- Do not edit an already-released migration; create a new forward migration.
- Define transaction boundaries in application services. Keep transactions short and avoid remote calls inside them.
- Use read-only transactions for read services where appropriate.
- Prevent N+1 queries through explicit fetch plans, projections, entity graphs, or targeted queries.
- Never fetch unbounded collections. Paginate, stream carefully, or batch.
- Prefer DTO projections for read-heavy dashboards and reports rather than loading complete entity graphs.
- Add indexes based on actual query predicates, joins, ordering, uniqueness, and execution plans. Do not add speculative duplicate indexes.
- Use database constraints for invariants that must hold under concurrency: uniqueness, foreign keys, checks, and non-null constraints.
- Use optimistic locking for ordinary concurrent edits. Use pessimistic locking only when contention and correctness analysis justify it.
- For money, use `BigDecimal` with explicit scale and rounding rules. Never use floating-point types.
- Store timestamps consistently, normally as UTC instants, and convert at system boundaries.
- Use soft deletion only when the domain requires recoverability or retention. Financial and audit records must not be destructively deleted.
- Do not expose JPA entities directly to controllers or JSPs.

## 6. Performance and scalability
- “One million requests per second” is not an implementation instruction by itself. Before capacity work, define endpoint mix, read/write ratio, payload sizes, latency percentiles, consistency requirements, dataset size, infrastructure, geographic distribution, and acceptable failure rate.
- Optimize measured bottlenecks, not imagined ones.
- Design stateless web nodes where practical so horizontal scaling is possible.
- Use HikariCP or the established connection pool and size it from database capacity, instance count, transaction duration, and measured concurrency. More connections are not automatically faster.
- Use pagination, batching, query projections, caching, asynchronous jobs, backpressure, rate limiting, and idempotency where appropriate.
- Cache only data with a defined owner, key, TTL, invalidation strategy, consistency tolerance, and memory bound.
- Do not cache authorization decisions or sensitive data without explicit security analysis.
- Use background jobs or messaging for slow, retryable, non-interactive work. Do not hide critical transactional failures in async execution.
- Add load tests for performance-sensitive endpoints and record baseline, environment, dataset, and percentile results.
- Performance acceptance must include at least throughput, p50/p95/p99 latency, error rate, resource saturation, database wait time, and connection-pool behavior.

## 7. Security
- Apply deny-by-default authorization. Authentication alone is never sufficient.
- Enforce authorization server-side at controller and service boundaries. UI hiding is not security.
- Use role and permission checks that also validate ownership, tenant, branch, SACCO, or record scope.
- Prevent IDOR/BOLA by querying resources within the authenticated user's allowed scope rather than fetching globally and checking late.
- Validate all external input with allowlists, length limits, types, ranges, and business rules.
- Use parameterized queries. Never concatenate untrusted SQL, JPQL, shell commands, paths, or template fragments.
- Protect JSP output from XSS using escaped output by default. Treat HTML rendering as an explicit exception.
- Keep CSRF protection enabled for browser sessions unless an endpoint has a documented alternative threat model.
- Session cookies must use Secure, HttpOnly, and suitable SameSite settings in production.
- If JWT is used, validate signature, issuer, audience, expiry, not-before, algorithm, and key rotation. Keep access tokens short-lived. Do not place sensitive claims in tokens.
- Do not store tokens in browser local storage for a server-rendered web application unless a documented threat analysis explicitly accepts the risk.
- Never commit secrets, private keys, credentials, production data, or real personal data.
- Do not log passwords, tokens, session IDs, cryptographic material, complete financial account data, or unnecessary personal information.
- Add rate limiting and abuse controls to authentication, password reset, OTP, search, exports, and expensive endpoints.
- File uploads require content-type verification, extension allowlists, size limits, generated storage names, malware-scanning integration points, and storage outside executable web roots.
- Error responses must not expose stack traces, SQL, secrets, internal paths, or authorization details.
- Dependency changes require compatibility, maintenance, license, vulnerability, and necessity review.

## 8. Financial-system reliability
- Financial mutations must be atomic, auditable, deterministic, authorized, and idempotent when retries are possible.
- Never update balances by trusting a client-provided resulting balance. Compute from validated ledger entries or controlled domain operations.
- Prefer append-only ledger/audit records and compensating reversals over destructive edits.
- Record actor, action, entity, timestamp, correlation ID, reason, and relevant before/after values for sensitive changes, while excluding secrets.
- Use maker-checker approval for high-risk transactions where the business requires segregation of duties.
- Enforce unique idempotency keys for externally retried financial commands.
- Reconcile derived balances against source transactions and provide detection for inconsistencies.
- Never silently swallow exceptions affecting money, permissions, audit trails, notifications, or integrations.

## 9. JSP, Tailwind, responsiveness, and accessibility
- Use Cloudscape create, table-view, details, form, and modal patterns through the repository-local Microfinance UI skills and reusable JSP components. Inspect `reference/microfinance-ui/README.md` for the sanitized source snapshot, FCMS component mapping, dependencies and retained licenses. The reference is available in cloud checkouts; no external Microfinance folder is required.
- Every intended UI change must first consult [Microfinance UI governance](.agents/skills/microfinance-ui-governance/SKILL.md) and [Microfinance UI style](.agents/skills/microfinance-ui-style/SKILL.md), including small CSS fixes, shared fragments, profile/account pages, form sections, buttons and alignment. Also consult [Microfinance table standard](.agents/skills/microfinance-table-standard/SKILL.md) for any table-related change.
- Use the shared named-section pattern (`erp-resource-section`, an in-flow heading, and `fcms-section-body`). Do not use bordered fieldsets with legends embedded in their borders as page/container titles. Retain unframed fieldsets/legends for genuine input groups. Related forms on the same page must share a bounded width, aligned fields and action edges, and deliberate spacing; place page actions in the shared header/action layout.
- Keep pages clean: do not generate explanatory paragraphs, sentences, page descriptions, or subtitles. Retain visible field labels, validation/errors, required financial disclosures, and specific consequential-action confirmations.
- Exports must be exact facsimiles of the approved original receipt, voucher, invoice, register or financial-statement format: preserve page size, design, typography, field order, spacing, alignment, borders, colors, totals, continuation headers and footers. Inspect the original renderer and reusable components before implementing. Preserve exact financial values, authorization and required disclosures; document any necessary FCMS-specific additions or corrections.
- Financial-statement previews must follow the approved original display and retain authorized account and transaction-detail drilling with the selected reporting dates. Verify each export against the original renderer using identical synthetic data and timestamps, rendered page comparisons and font/character geometry checks; a generic table or high similarity percentage alone does not prove an exact facsimile.
- For financial reports, use the original Microfinance income-statement workspace and corresponding financial-position, cash-flow, equity, trial-balance and ledger designs included under `reference/microfinance-ui/source/` as the facsimile authority. Follow the financial-statement authority map in `reference/microfinance-ui/README.md` and documented FCMS-specific corrections. Do not substitute the legacy FCMS blue income/profit-and-loss layout. Audit each report's presentation builder, preview/print views and exporter; a shared font/layout helper alone does not establish the correct report structure.
- Build mobile-first layouts.
- Every changed page must be usable at approximately 320 px, 375 px, 768 px, 1024 px, 1440 px, and very wide displays.
- Constrain reading width on large screens; do not stretch forms and text indefinitely.
- Tables must have a responsive strategy: priority columns, horizontal scrolling with clear affordance, stacked cards, or alternate compact views.
- Forms must use visible labels, useful help/error text, logical grouping, sensible input types, and touch-friendly targets.
- Do not communicate state by color alone.
- Support keyboard navigation, visible focus, semantic headings, landmarks, accessible names, and meaningful error summaries.
- JSPs must use tag libraries/components/partials consistently and avoid duplicated markup.
- Avoid inline scripts and inline styles unless the repository explicitly requires them.
- Keep JavaScript progressive and minimal; core server-rendered workflows should remain understandable and resilient.
- Use Post/Redirect/Get for successful form submissions to prevent accidental resubmission.
- Activities, transaction component templates and all three voucher workspaces follow `docs/microfinance-voucher-workflows.md`. Extend the canonical activity-filtered pair/split create/preview/register flow with server-generated configuration codes. New ordinary transaction setup uses one TOTAL pair; retain existing specialised classifications. Enforce 50 transactions, 50 entries per transaction and 500 entries overall, balance each transaction, and reserve the reference for every money account. Keep financial capture atomic, preserve the displayed request-bound voucher number and live invalid forms, and retain FCMS independent review and historical report drills. Do not restore the retired single-row configured-voucher or manual journal entry pages.
- Resource creation must follow the [Cloudscape create flow](https://cloudscape.design/patterns/resource-management/create/) through the repository-local Microfinance UI skills. Audit the complete path: entry action → named form/modal → server validation and authorized transactional command → destination GET → visible outcome. A successful top-level create returns to its canonical table/register, with a persistent, dismissible localized Flashbar at the top of the main workspace. A sub-resource returns to its owning collection (including a parent details page only when that page owns the relevant table). Do not leave a completed creation form or success-only modal open, or require the user to visit details to discover success.
- Use the shared `ResourceFeedback` server flash contract after the application service returns. Its destination nonce binds the FlashMap to the intended GET so unrelated refresh/prefetch requests cannot consume the result. Match only the resource form when recovering errors; navigation, search and logout forms must never become recovery targets. Never infer success from `?saved`, `?created`, client-side optimism or a generic 2xx response; query parameters alone cannot generate outcome notifications. Keep necessary resource/filter context and authorized resource links. Do not auto-dismiss success/error notifications on a timer.
- Outcome wording must match committed state: a financial draft is created, a proposal awaits review, and a queued backup is requested, not completed. Preserve maker-checker, posting/approval, import preview/commit and idempotency rules. Never replay an uncertain POST automatically.
- Recoverable creation failures stay in the same form/modal with submitted values, selected files, CSRF and request keys retained. Put field validation and known server conflicts immediately beside the affected control or indexed row, expose its `aria-invalid`/error description, reveal the containing section/tab, and focus the first error. Put genuinely unbound/service failures in a focusable Alert immediately before the form actions. Keep primary submission available to invoke validation; disable it only for a real in-flight operation or business prerequisite. Do not substitute a generic page error for a recoverable field problem, duplicate all field messages into a top summary, or save private drafts in browser storage.
- Every creation-flow change must verify successful list redirection and top notification, one-time delivery/dismissal, forged outcome-query rejection, invalid/duplicate input at the correct fields, retained values, authorization/CSRF, native and enhanced controls, modal close/reopen, browser navigation, English/Kiswahili and the required responsive widths. Add or update the creation-flow inventory in `docs/resource-creation-flow.md` so every active create endpoint has an explicit destination and failure behavior.

## 10. API and integration rules
- Define request and response DTOs; do not expose persistence models.
- Validate inbound payloads at transport and domain boundaries.
- Use consistent errors, correlation IDs, timeouts, retry policies, and circuit breaking for remote dependencies.
- Retry only idempotent operations or operations protected with idempotency keys.
- Set bounded connect, read, and overall timeouts for every remote call.
- Verify webhook authenticity and make webhook processing idempotent.
- Keep integration adapters isolated from core domain logic.

## 11. Testing
- Add or update tests for every behavior change.
- Unit-test domain rules and edge cases.
- Use integration tests for persistence queries, transaction behavior, security rules, migrations, and module integration.
- Prefer Testcontainers PostgreSQL for database-sensitive behavior rather than relying only on H2.
- Add authorization tests for allowed and forbidden actors, including cross-tenant or cross-SACCO access.
- Add concurrency tests for balances, counters, approvals, inventory-like limits, and idempotent commands.
- Add rendering or end-to-end checks for critical JSP workflows.
- A test that merely calls a method without meaningful assertions is not sufficient.
- Do not delete or weaken tests simply to make a build pass.

## 12. Observability and operations
- Use structured, contextual logs with correlation/request IDs.
- Emit metrics for request latency, error rate, throughput, JVM, connection pool, database calls, cache behavior, queues/jobs, authentication failures, and critical business operations.
- Health checks must distinguish liveness from readiness and avoid leaking sensitive information.
- Add distributed tracing where cross-service or complex integration flows justify it.
- Alerts should be based on user impact and service-level indicators, not only CPU thresholds.
- Every scheduled/background job must have logging, metrics, failure handling, retry limits, and duplicate-execution protection.

## 13. Maintainability
- Use clear names based on domain language.
- Prefer cohesive methods and classes over generic utility dumping grounds.
- Remove dead code only when its usage has been verified.
- Do not introduce abstractions before at least one clear use and a foreseeable second use.
- Document architectural decisions that impose meaningful long-term constraints.
- Keep comments focused on why, invariants, and non-obvious trade-offs—not restating code.

### Requested feature removal
- A request to remove a feature includes removing its related dead code, including pre-existing code used only by that feature. No separate cleanup request is required. Only an explicit presentation-only request, such as hiding a menu item, limits this scope.
- Trace static, dynamic, shared and external callers. Remove exclusive routes (including direct URLs, APIs, exports and mutation endpoints), controllers, services, repositories, DTOs, JSPs, scripts/styles, imports, dependencies, configuration, jobs, message keys and obsolete tests. Remove obsolete branches from shared components and update surviving callers and documentation. Hiding navigation, redirecting entry pages or disabling only creation does not complete feature removal.
- Preserve shared workflows, required financial/audit history and released migrations. Feature removal does not authorize destructive data deletion. Use forward migrations for necessary schema or permission changes; removing security matchers must not expose routes through a broader authorization rule.
- Retain a compatibility route only for an identified caller or explicit compatibility requirement, with a minimal implementation, regression coverage and a documented reason. Do not keep the old workflow as an undocumented fallback.
- Verify reference cleanup, compilation, direct legacy URL/API behavior and surviving workflows. Replace obsolete tests with retirement/replacement coverage while preserving applicable security and financial-history assertions. Report every retained related component with its active dependency or retention reason, and disclose any unfinished removal.

## 14. Definition of done
A change is complete only when:
- Required behavior works end to end.
- Module boundaries and domain invariants are preserved.
- Authentication, authorization, validation, CSRF/XSS, sensitive logging, and ownership scope have been reviewed.
- Database access is bounded and checked for N+1 queries, missing indexes, unsafe transactions, and concurrency issues.
- Relevant tests pass.
- Responsive and accessible behavior is verified for changed JSP pages.
- Operational effects, migrations, configuration, rollback considerations, and documentation are addressed.
- Requested removals include verified cleanup of related dead code across all layers; any necessary retained component is explicitly justified.
- No unsupported performance or security claims are made.

## 15. Skill routing
Always consult [springboot-1k-rps-optimization](.agents/skills/springboot-1k-rps-optimization/SKILL.md) before planning or editing in any future Codex chat, review, change, or implementation in this repository. This repository-local copy includes its supporting references, template and audit script. Treat it as a mandatory baseline for Spring Boot MVC/JSP/PostgreSQL work, including UI routes, controllers, services, repositories, security, migrations, tests, and operational changes. All paths below are relative to the repository and work in cloud/Linux checkouts without access to the original workstation.

For every intended FCMS Spring UI change, always consult UI governance and UI style before editing. Consult the table standard whenever tables or their controls are affected:
- [microfinance-ui-governance](.agents/skills/microfinance-ui-governance/SKILL.md) — navigation, workflows, financial states, and localization.
- [microfinance-ui-style](.agents/skills/microfinance-ui-style/SKILL.md) — the existing ERP shell, forms, controls, modals, and responsiveness.
- [microfinance-table-standard](.agents/skills/microfinance-table-standard/SKILL.md) — tables, selection, scrolling, sorting, and bounded server-side pagination.

Apply these interface skills alongside the Spring Boot baseline. These sanitized adaptations reference the included source snapshot and existing FCMS JSP/CSS/JavaScript components. Original source routes, principal fields, permissions and lending workflows require deliberate adaptation; they must not be assumed to exist in FCMS. The skills do not authorize unrelated lending features, terminology changes, or replacement of established FCMS financial approval rules. Read supporting files relative to their skill file. Verify the package with `python3 scripts/verify-agent-references.py` before using the reference on a fresh checkout.

When present, use the most relevant additional skill under `.agents/skills/` for specialized work (the following optional skills are not bundled; their absence does not block using the four required skills above):
- `modular-monolith-architecture`
- `postgresql-performance`
- `spring-security-hardening`
- `financial-integrity`
- `jsp-responsive-accessibility`
- `testing-quality-gates`
- `observability-operations`
- `performance-capacity-engineering`
- `integration-resilience`

Multiple skills may be combined when a task crosses concerns.

## Repository-local Microfinance UI reference

Read `reference/microfinance-ui/README.md` for the source-to-FCMS mapping and dependency boundaries. The snapshot includes shared table/selection/split-view, forms/validation, selectors/dates/loading, modal examples, dashboards, COA/activity/transaction views and original financial-statement presentation/print/export sources. Source files are read-only reference material outside the runtime source roots. Prefer extending the FCMS adaptation; do not copy the source shell, business services, claims or loan workflows wholesale. No browser or Computer Use check is implied by adding this package; follow the current user's tool instructions and report actual checks.

## 16. Repository-local legacy PHP reference

The sanitized legacy PHP source archive is `reference/fcms-php-reference.zip`. Before comparing legacy workflows, read `reference/README.md`, verify `reference/fcms-php-reference.zip.sha256`, and extract under an ignored local reference directory such as `.verification/legacy-php`. The canonical workflow entrypoints are the archive's root `fcms/*.php`; `CODES`, `CODE2` and `images` retain older application copies. The manifest identifies credential redactions and excluded runtime data. Inspect the snapshot as read-only source, without executing legacy entrypoints against an operational database. The archive does not replace the Microfinance financial-statement authority, FCMS authorization/financial rules, or the mandatory skills above.

For client-register changes, also read [client register parity](docs/client-register-parity.md). Preserve each category's create/edit field contract, independent payment and operational statuses, all-dates Other Clients register, private contract action and financial-history deletion guard. Do not reintroduce AMCOS tax/commission inputs or a fiscal-year requirement for Other Clients. Creation must retain the created client's year in its register redirect and use the shared destination Flashbar.
