---
name: microfinance-ui-style
description: Build or restyle FCMS JSP shells, sections, forms, controls, modals, notifications, dashboards, and responsive layouts with the preferred Microfinance Cloudscape patterns and existing shared components.
---

# Microfinance UI style for FCMS

Read [governance](../microfinance-ui-governance/SKILL.md), the root `AGENTS.md`, and the [component/dependency map](../../../reference/microfinance-ui/README.md). Prefer existing FCMS JSP/CSS/JavaScript. The reference snapshot is outside the application and requires deliberate adaptation; it is not an installable theme.

## Shell and surfaces

- Retain the FCMS topbar/sidebar and their permissions. Inspect `src/main/webapp/WEB-INF/jsp/reusableuicomponents/navigation/`, shared `src/main/resources/static/css/` and `src/main/resources/static/js/app.js` before page edits. Source shell selectors and role assumptions need mapping, not wholesale replacement.
- Apply Cloudscape behavior through the compact JSP ERP: light gray workspace, white named section/table surfaces, thin neutral borders, shaded section headings, compact controls, readable text, blue native links and orange independent Create/Add or final form action. Preserve established FCMS tokens and locally bundled Open Sans; the source skill's older Amazon Ember instruction is superseded by the bundled FCMS font assets.
- Page titles, breadcrumbs and bottom form actions sit outside section frames. Use named sections for related fields/facts, not a card around every field or an extra card around an already framed table. Keep text/forms bounded (roughly 960px workspace/728px field column where appropriate); data tables can use the available width.
- Reuse shared classes/tokens rather than page-specific button, card, notification or gear-dialog systems. Do not introduce gradients, oversized headings, decorative imagery or explanatory page copy.
- Facts use quiet labels, dark values and responsive label/value columns with long-value wrapping. Statuses use readable labels plus semantic icons; never communicate state by color alone. Printable stationery retains its original layout.

## Forms and controls

- Follow [validation and recovery](references/form-validation.md). Keep visible labels, field errors, required disclosures and concise consequential-action confirmations. Native constraints and server business validation remain authoritative.
- Group related controls with wrapping flex/grid, `min-width:0` children and adequate gaps. Section bodies normally use 20px padding (16px mobile); inspect the cascade before overriding dense legacy panel styles. Repeated transaction rows need a coherent fieldset, stable indexed names/IDs, accessible move/remove tools and spacing between rows.
- Put Cancel before the orange primary action at the bottom right. Back, Preview, draft and navigation actions remain neutral unless the existing workflow defines draft as its sole final action. Keep actual permission, prerequisite and in-flight restrictions; allow incomplete fields to reach validation.
- Reuse native/enhanced selects and date controls. Display names rather than code-prefixed filter options while preserving submitted IDs. Keep native bindings, ISO dates, labels, required/min/max checks, keyboard and reset behavior intact.
- Searchable dropdowns reuse select-enhancer, bounded choice adapters and debounced cancellable search. Search stays above the scrollable options; Load more below. Preserve the current selection, reject stale responses, cap retained choices and never submit a financial form on search Enter. Body-mounted menus clamp/flip to the viewport and clean up on removal.
- File picking stays in the live multipart form with per-file validation/removal and existing private preview/download authorization. Do not copy the source loan/client upload workflow or persist selected files in browser storage.
- Breadcrumb links and resource IDs remain blue in normal/visited states, with hover/focus underline and visible outline. External-link icons belong only to actual separate-window links.

## Notifications and pending work

- Use a persistent dismissible Flashbar for task results above the destination title, contextual Alert beside the affected section or above form actions, and inline errors beside fields. Use calm severity tints, complete thin borders, readable text and semantic icons. Required warnings do not auto-dismiss; no overlapping toast layers.
- Stored workflow states use labeled status indicators. Missing data is unavailable, not success or zero. Errors use an alert role; informational updates use status semantics.
- Use request-bound `AppLoading` spinners in the affected data region or initiating action. Keep header/sidebar, tools and editable controls available. Text action labels remain visible beside their spinner; data spinners have localized accessible names without visible explanatory labels.
- Clear loading on success, failure, cancellation, supersession, page exit and restored navigation; retain native nodes/values for recovery. Never fabricate delays/progress percentages, put a global loading blur over the shell, or replay uncertain mutations. Read [workflow/loading guidance](references/aws-instance-type-finder.md) when adapting workspace transitions.

## Shared modal contract

- Use FCMS `modal-workspace.js`, shared modal styles and existing form/controller ownership. The overlay/backdrop and any blur must be clipped to the **main content area**, excluding both top navigation and sidebar. A body-mounted dialog/dropdown is allowed for clipping/focus handling only when its backdrop geometry remains confined to the workspace.
- Keep dialogs within the available viewport, a white named panel, clear close control, scrollable body and visible right-aligned footer. Provide initial focus, focus containment/restoration, keyboard access and Escape where cancellation is safe. Preserve an in-flight financial command's uncertainty state.
- Restore original scrolling/interaction states on every close/removal path; do not leave the shell blurred or locked. Reopening starts clean; never persist open-modal state. Recoverable failure retains live values/files and shows a local error. Preserve workflow-specific success/X behavior.
- Nested confirmation and body-mounted selects need their own keyboard ownership. Restoring a UI submission lock must not enable a control disabled by a financial/business rule.

## Tables, details and dashboards

- Read the [table skill](../microfinance-table-standard/SKILL.md) before editing a register, preferences, selection or split pane. Title/tools stay outside the row scroller. Use existing native authorized ID links and menu-only selected-record commands.
- Details use a few in-place, keyboard-accessible tabs with wrapping labels. Preserve native forms and unsaved edits, reveal validation panels and print all relevant sections. Do not add scrolling tab bars or decorative single-tab previews.
- Dashboard work uses [the dashboard contract](references/cloudscape-dashboards.md). Copy only presentation behavior; adapt all endpoint, scope and role bindings to FCMS.

## Verification

Check approximately 320, 375, 768, 1024, 1440px and very wide screens, long labels and English/Kiswahili. Only table canvases may need horizontal scroll; page content, controls, menus, tabs and dialogs fit without clipping. Check labels, semantic landmarks/headings, focus, keyboard, reduced motion, contrast, state independent of color, invalid submission/value recovery, repeated rows and modal reopen. Report actual rendering checks and omissions; compilation alone does not validate JSP rendering.
