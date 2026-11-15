---
name: microfinance-table-standard
description: Create, edit, or review FCMS JSP registers, grouped chart of accounts, resource selection, scrolling, sorting, preferences, split previews, and bounded server-side pagination using the preferred Microfinance Cloudscape table patterns.
---

# Microfinance table standard for FCMS

Read [governance](../microfinance-ui-governance/SKILL.md), [style](../microfinance-ui-style/SKILL.md), and the repository [component map](../../../reference/microfinance-ui/README.md). Inspect FCMS `resource-table.js`, `resource-table.css`, `resource-split-view.js`, `grouped-resource-table.js` and localized reusable JSP components before adding controls. Source files are references, not drop-in replacements for FCMS selection controllers or routes.

## Reusable console table component

- Follow Cloudscape [table view](https://cloudscape.design/patterns/resource-management/view/table-view/). COA uses [table with grouped resources](https://cloudscape.design/patterns/resource-management/view/table-with-grouped-resources/): preserve hierarchy, accessible expansion, selected record identity and authorized parent/child navigation. Use real grouped resources, not a flat table with decorative indentation.
- Opt in with a stable `data-resource-table` key. A register has one title/count and compact header actions, then a filter/tools row, independently scrolling rows/sticky column headers, and real server paging. Do not repeat its title above the table or wrap it in another card.
- Put refresh, the selected-record Actions menu, then independent orange Create/Add in the header. Use compact neutral 32px controls for other tools. Filters are left, borderless SVG paging and Preferences right. Tools/title remain outside horizontal/vertical row scrolling. Always show shared paging, including empty/single-page results; no second boxed Previous/Next footer.
- Filters use visible labels, consistent 32px control heights and aligned bottoms. At desktop widths keep a workable single row using shrinkable fields; wrap naturally on narrower containers. Hide empty error elements rather than adding phantom flex gaps; populated errors remain visible.
- Use compact readable 13px values, 18px leading, quiet row rules, short header separators and narrow selection columns. Use official shared SVG geometry for sorting/menu/paging/refresh, not Unicode arrows. Follow the existing FCMS font/tokens; do not add an unavailable source font.
- Identifiers/dates/amounts can stay compact; long names/descriptions wrap. Workspace amounts and their headings share the same leading alignment and use tabular numerals, explicit currency context and consistent decimal precision. PDF/print stationery keeps its own approved alignment.

## Selection and record commands

- No row Actions column or command buttons. All selected-record commands, including View/Open, history, PDF/download, Print and lifecycle/review commands, belong in one header Actions dropdown. Independent Create/Add and refresh remain outside it.
- Use accessible single-record checkbox selection and a visible row highlight; selecting another replaces selection and selecting the same row again clears it. Keep the selection announcement screen-reader-only. Do not add a visible selection summary or Clear selection button.
- Native blue `resource-table-id-link` identifiers navigate directly to an existing authorized details destination, preserving normal keyboard, modified clicks and open-in-new-tab. Row-body selection ignores links, buttons, labels and form controls. Missing/unauthorized destinations remain plain text; never invent a route or substitute an edit operation.
- Commands retain their actual permission, eligibility and lifecycle restrictions; do not enable every menu item just because a row is selected. Without selection they are disabled. Use keyboard menu navigation, skip disabled items, Escape/outside dismissal and focus restoration.
- Clear selection and preview on filtering, sorting, paging, refresh, confirmed display changes and restored navigation. Preserve native CSRF/submitters and existing listeners. Move original form envelopes, never clone financial controls or move only a button out of its form.
- Legacy native row commands can use `data-table-actions-header`; custom row-context handlers need an explicit page controller before promotion. Do not apply selection tooling to summary-only reports, inline editors or account pickers whose cell controls are the task.

## Server paging, sorting and loading

- All whole-register queries are authorized, bounded and server-paginated. Whitelist sort fields/direction, order before LIMIT/OFFSET with a stable ID tie-breaker, validate size and cap it. Declare only the size set actually accepted by the FCMS endpoint; never copy Microfinance's 10/30/50 choices into an endpoint accepting different sizes.
- Every displayed data column in a resource register needs a real ascending/descending server sort. Use a native scoped link, `data-table-sort` and `aria-sort`; preserve filters/scope/size and reset page zero. Selection/command columns are not sortable. No page-only sorting or decorative carets.
- Native server pagers use `data-table-pagination`, zero-based `data-page`, stable `data-table-for`, and previous/next URLs retaining filters/sort/size. Reuse `register-pager.jspf`; do not infer page direction from translated text.
- Keep search scope honest: server search covers the authorized matching register; a local fallback can search only already-loaded rows and must say so. Never fetch a whole register to search, populate choices or compute totals. Totals come from the authorized financial source, not the visible page.
- HTML-table refresh replaces authorized server body rows and synchronizes pager/sort state in place while preserving title/tools/filter nodes. Cancel obsolete requests, reject login redirects/stale results, reset selection and restore old rows on failure with a local retry. Show a real request-bound spinner inside the canvas without premature empty states.
- JSON-backed editable grids use explicit external paging and their owning draft/command controller. The source `ResourceTable.setPage`/`resource-table:page` contract requires an FCMS implementation before use; do not use HTML row replacement on editable financial data.
- Separate an empty register from no filter matches, retain concise recovery and correct empty-state colspan. Parent-to-register navigation preselects a visible parent filter and preserves it through paging; filter context never grants authority.

## Preferences and split details

- Reuse the shared Preferences dialog, keyboard controls and Confirm/Cancel semantics. Options include supported page size, density, wrapping, stripes, sticky columns, visibility and ordering. Protect identifiers/financial editor columns, native cell identity, values/listeners and dynamic rows. Store presentation only, never selected records/data/permissions.
- A split pane starts closed at every width and on history restoration. Only Show details opens it; selection, saved size/placement and resize never auto-open it. An already open pane updates authorized selected-row facts and clears when selection resets. Keep actions in the existing menu and full details at the native ID link.
- Support bottom/side placement, narrow-screen fallback, close/reopen and pointer/keyboard resizing without shifting toolbar position. Persist size/placement only. A simple facts pane uses a heading and quiet columns, not a nested card or fake tab.
- Preferences/modal backdrops remain confined to main content using FCMS `modal-workspace.js`; top/side navigation must never be blurred.

## Verification

Check real query limits and whole-result sorting, URL/filter retention, scoped totals, empty/error recovery, stale requests, permission-aware command targets, toggling/replacement/reset, native ID navigation, menu-only View/PDF/Print, CSRF/form preservation, preferences Cancel/Confirm, keyboard ordering, dynamic rows and split close/reopen/resize. Check root responsive widths and both languages. No interactive controls belong on PDF/print stationery. Record actual runtime checks and any skipped browser validation.
