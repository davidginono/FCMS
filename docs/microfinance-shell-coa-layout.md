# Microfinance navigation and COA layout adaptation

The source authority for this change is the actual `C:/Users/USER/Desktop/Microfinance` project: `fragments/header.jspf`, `fragments/sidebar.jspf`, `accounting/accounts.jsp`, `accounting/account-rows.jsp`, `shell.css`, `accounting-ledger.css`, `resource-table.*` and the UI/table skills. The final classic navigation block in `shell.css` defines the navy identity bar and white text-led service tree; earlier generic FCMS shell styling did not reproduce it.

## Runtime mapping

| Microfinance design | FCMS adaptation |
| --- | --- |
| Navy `#232f3e` topbar, navigation search, profile control and logout | Existing topbar JSP plus `console-shell.css` and `shell-navigation.js`; retains FCMS branding and authenticated identity |
| White 236px service navigation, 48px service header, 14px/20px links, 24px inset and 28px closed rail | Existing authorized sidebar JSP, shared source-based shell styles and one navigation owner |
| Source sidebar/current resource search | Bounded search of the server-rendered authorized FCMS sidebar, with keyboard highlighting, native destinations and no database lookup |
| Profile/language controls | Existing self-account permission, language links with allowlisted report filters, native logout CSRF form; no imported branch or institution data |
| COA resource workspace, title/actions, search-first filters and table canvas | `accounting/accounts.jsp`, reusable `account-filters.jspf`, shared resource-table component and grouped-resource styles |

FCMS retains grouped classifications, real account IDs, prepared names, existing account approval/lifecycle permissions, bounded group loading, server sorting and 10/25/50/100 page-size validation. Microfinance's account kinds, source account-code rules, claims, version workflows, lending data and 10/30/50 page sizes are not transplanted.

COA selection/group controls and permitted commands retain their existing owners. Group headings now have a consistent neutral surface across the selection and spanning cells; the account-code hierarchy indent wins against shared cell padding. Fixed column hints and a table-only scroll canvas keep the resource surface bounded. The filter form uses the same actual `search`, `parentId`, `status`, `sort`, `direction` and `size` bindings.

The superseded FCMS 72px/flyout sidebar code and overlapping navigation event handlers were removed. Shell initialization follows the workspace lifecycle, preserving live topbar/sidebar nodes during in-place navigation. The spinner-only transition mask remains confined to main content. New static assets are synchronized into both resource roots.

## Checks and limits

UI checks exercise the source-style close/expand rail, mobile overlay/Escape, authorized search and highlighted Enter navigation, profile dismissal, native logout/CSRF, and consistent COA group surfaces/indentation. Existing workspace tests cover repeated navigation, no content flash, dirty recovery and native financial requests. Integration checks retain the existing language-filter and financial-ledger context assertions.

No browser or Computer Use check is authorized by the current instruction. DOM/HTTP checks and source geometry do not certify rendered pixel fidelity or every viewport. Local acceptance artifacts are under ignored `.verification/microfinance-shell-coa-20261116/`.

Acceptance on 16 November 2026: 19 UI/component tests and 44 Spring/PostgreSQL/architecture tests passed. Twelve native HTTP checks and four read-only actor checks retained form actions, field names and CSRF. Eleven actual JSP transitions checked the navy topbar, visible navigation search, white sidebar, two language links, single-handler rail toggling, COA column/filter structure, group selection, spinner-only loading and persistent shell identity without script errors. The rebuilt app runs on port 8093; PostgreSQL 18 backup tooling and ten-second polling remain configured. Refresh an already-open page once to load asset version `20261116-shell-coa1`.
