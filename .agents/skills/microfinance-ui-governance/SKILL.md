---
name: microfinance-ui-governance
description: Design, edit, or review FCMS JSP navigation, create/edit and details workflows, accounting configuration, financial action states, and English/Kiswahili localization using the preferred Microfinance Cloudscape patterns.
---

# Microfinance UI governance for FCMS

This portable adaptation preserves the approved Microfinance interface patterns. Read the root `AGENTS.md` and [Spring baseline](../springboot-1k-rps-optimization/SKILL.md). Use the repository [component map](../../../reference/microfinance-ui/README.md) to inspect source and FCMS implementations. Resolve paths from this file or the repository root; no external Microfinance checkout is required.

## Navigation and resource workflows

- Use Cloudscape [create](https://cloudscape.design/patterns/resource-management/create/), [table view](https://cloudscape.design/patterns/resource-management/view/table-view/), [details](https://cloudscape.design/patterns/resource-management/details/) and [page edit](https://cloudscape.design/patterns/resource-management/edit/page-edit/) through the existing JSP shell. Do not install a frontend framework to reproduce them.
- The sidebar owns global navigation. Body actions affect the current resource/task; do not duplicate sidebar destinations as cards or buttons. Keep personal Settings separate from accounting/business configuration.
- Arrange authorized sidebar groups as Dashboard, Clients, Sales, Accounting Office, Reports, Operations and Administration; omit unavailable groups without empty headings. Accounting Office orders Transactions, Vouchers and Configuration. Parent menus use native `details`/`summary`, start expanded (`open`), remain user-collapsible, and share `ui:navigationCaret` filled triangles (down expanded, right collapsed). Preserve active ancestors, permissions and a shallow, consistently indented hierarchy; do not mix chevrons, Unicode arrows or browser disclosure markers.
- Keep one visible workspace title. Registers own their title/count in the table header; forms, details and dashboards retain distinct task titles. Breadcrumbs sit above titles, outside cards, with authorized blue ancestor links and a matching `aria-current` label.
- Do not generate explanatory paragraphs, sentences, page descriptions, subtitles, repeated context, or decorative KPI strips. Retain visible field labels, inline errors, required disclosures, essential empty/error recovery, and specific consequential-action confirmations.
- Focused create/edit pages use named white sections, bounded fields, expandable optional sections and bottom Cancel plus a verb-led primary action. Use a wizard only for real dependencies. Preserve the existing full-page/modal choice and source return context.
- Successful native forms use Post/Redirect/Get and a persistent dismissible destination Flashbar. Unchanged edits return with informational feedback. Do not claim an unposted request is posted or skip a required review/preview.
- Recoverable errors keep the live form, values and selected files; field errors stay at fields, unbound errors immediately above actions. An uncertain response must never trigger automatic replay of a financial POST. Preserve CSRF, native submitters, request keys and the existing submission owner.
- Dirty Cancel/navigation uses the shared discard confirmation; Continue editing preserves values. Never save private form values/files in browser storage. Reuse existing form recovery rather than introducing a second router or submit handler.
- Details show record-specific facts in named sections. A few task-based tabs switch in place, preserve live native forms, reveal validation errors and support keyboard navigation. Tabs wrap on narrow screens; no scrolling/clipped tabs or overflow menus. Persist tab IDs only when supported, scoped to the route; explicit links/errors take precedence.
- Retiring a flow requires tracing and removing its unused routes, views and exclusive assets while preserving required actions at the surviving destination. Verify dynamic/shared callers before deletion; do not migrate unrelated workflows.

## Financial states and configuration

- FCMS owns names, routes, claims, ownership scopes, source events, financial approvals, posting, period locks, idempotency and reversals. Microfinance loan/client/tenant terminology, quorum rules and account-version approvals are source context, never authority to add/change FCMS policy.
- Authorize controller/service operations independently of navigation and row selection. Missing evidence/data is unavailable, never a verified result or an invented zero. Show real draft, pending, approved, posted, rejected and reversed states with readable labels.
- Transactions Config contains Activities and Transactions. Reuse the canonical FCMS configuration pages; do not create duplicate posting-mapping configuration. Saving definitions does not post money. Activity navigation preselects the activity in the Transactions filter and retains it across search/paging. Preserve system-managed and lifecycle restrictions.
- COA follows the grouped-resource table contract in the [table skill](../microfinance-table-standard/SKILL.md). Derive classification from a valid parent, retain immutable identifiers and effective/reviewed metadata, and translate labels without inventing a second account-name binding. FCMS services remain the source of validation and approval rules.
- Use [selection-dependent Actions](../microfinance-table-standard/SKILL.md#selection-and-record-commands); direct native authorized ID links remain available. Split previews show only authorized facts and start closed; selection never opens them automatically.
- Exports and financial-statement previews follow the included original Microfinance builders, print views and stationery identified in the [component map](../../../reference/microfinance-ui/README.md#financial-statement-authority). Preserve exact fonts, geometry, header details, structure and values; generic tables/shared typography do not establish a facsimile. Retain dated account-to-transaction-to-voucher drills and the same authorization on exports. Follow existing FCMS format permissions and documented corrections, including income surplus in operating cash flow.

## Localization and privacy

- Use short, simple English and Kiswahili through existing message bundles. Keep FCMS domain terms and exact financial distinctions; do not rename entities, routes, claims or bindings as part of a style change.
- Display dates as `DD/MM/YYYY` with the application timezone for timestamps; keep ISO machine values in requests, links, persistence and sorting. Money uses exact decimals, visible currency context and consistent precision.
- Show only the authorized record's necessary facts. Store presentation preferences only, scoped to the user/workspace; never financial data, credentials, form values or permission decisions.
- Dashboard widgets use actual scoped data, missing/loading/error states and accessible alternatives. Full registries remain in their workspaces. Read [dashboard guidance](../microfinance-ui-style/references/cloudscape-dashboards.md) when implementing widgets.

## Verification

Check permitted/forbidden commands, parent/record scope, native POST/CSRF, invalid submission recovery, dirty exit, success return, selection resets, keyboard/focus and both languages. Follow the root responsive widths. Browser checks depend on the current user's authorization and available tools; report omitted checks honestly. A source reference is not evidence of a running FCMS workflow.
