# Workspace navigation and contextual loading

FCMS adapts the bundled Microfinance `workspace-navigation.js`, `ui-loading.js`, shell and form-recovery patterns. The existing FCMS topbar, sidebar, permissions and native server-rendered destinations remain authoritative. Sidebar sections and their nested groups use one reusable filled-triangle disclosure tag, with native keyboard-operable `details`/`summary` controls.

The later [actual Microfinance shell and COA layout adaptation](microfinance-shell-coa-layout.md) supplies the navy topbar, authorized navigation search/profile controls, white service sidebar and collapsed rail. Its `shell-navigation.js` owns shell initialization through this same lifecycle; overlapping legacy handlers were removed.

Ordinary same-origin HTML GET links and unmanaged GET filters update the workspace without reloading the document. Shared shell and stylesheet nodes remain connected; current navigation, reporting dates, language, page title, focus and history update from the authorized response. Workspace switches, including financial-statement tabs, show only the workspace spinner until authorized content and initialization are ready. Table paging/refresh retains its existing request owner. Modified clicks, fragment-only links, downloads, print/export and native POSTs retain native behavior.

The lifecycle adapter scopes each JSP's event listeners, observers, timers and GET requests, disposing them before initializing the next response's scripts. This follows Microfinance's existing script/onReady contract and prevents duplicate handlers and stale requests. The app script is isolated in a closure so it can initialize again safely. Fetched script/style resources must be same-origin; script resources are server-provided files. A changed navigation asset version triggers native navigation. No financial data or HTML is cached in browser storage; the history scroll-position map is bounded to 32 entries.

Unsaved forms use the existing discard dialog through `workspace:before-navigate`. Failure before replacement retains the original nodes and values and offers a GET retry. Initialization failure offers a native reload. Existing mutation fetches keep their own controller and timeout; workspace navigation waits while a mutation or busy form is pending. No financial command is replayed by the navigation layer.

The topbar's native `lang` select switches English/Kiswahili through the existing session locale interceptor. `shell-navigation.js` sends the current route, query and fragment through workspace navigation; a cancelled or failed switch keeps the selector on the displayed language. The server renders the selected session language on subsequent pages. Without JavaScript, the GET form's Apply action retains the allowlisted page filters. Language links no longer duplicate this control inside the profile menu. Narrow topbars wrap their existing flex groups instead of clipping controls.

## Spinner source and adaptation

The [Cloudscape feedback guidance](https://cloudscape.design/patterns/general/user-feedback/#spinner) and [Spinner component](https://cloudscape.design/components/spinner/) govern contextual indeterminate feedback. The two clipped-circle arc geometry, 0.7-second rotation and 1.5-second expanding/contracting sweep adapt the Apache-2.0 [spinner implementation](https://github.com/cloudscape-design/components/tree/main/src/spinner). The existing Amazon copyright, license and notice are retained. FCMS uses Microfinance's context sizes (16px inline, 20px section/table, 32px workspace), current-color contrast and transparent track. Reduced-motion users receive a static visible arc, following the repository accessibility contract.

`AppLoading` supports region loading, retained native content, failure/retry and action labels/icons. Status names are localized and accessible without visible explanatory text. Loading never blurs the topbar or sidebar, and cleanup covers completion, failure, supersession and page exit.

## Verification and dependencies

`npm run test:ui` runs DOM interaction tests with pinned, development-only MIT-licensed jsdom 29.1.1. Its supported Node versions are `^20.19.0 || ^22.13.0 || >=24.0.0`; verification used Node 24.13.1. The existing development dependency `source-map-js` was updated to 1.2.2 to resolve its audit finding. No frontend framework or production dependency was added. `npm audit` reports no vulnerabilities at verification time.

The interaction tests exercise persistent shell identity, request races, contextual loading, failure recovery, dirty keep/discard, Back/Forward history, active mutation ownership, native downloads/POSTs, GET submitters, language changes and repeated initialization. The Spring checks use a fresh PostgreSQL 18 database. Runtime acceptance evidence and any remaining limitations are recorded under ignored `.verification/workspace-navigation-20261116/`.

The initial 16 November verification did not include browser or Computer Use checks. DOM tests alone cannot certify rendered pixel fidelity, viewport geometry, animation appearance or visual facsimile equivalence.

Acceptance on 16 November 2026: 13 DOM interaction tests and 44 targeted Spring/PostgreSQL/architecture tests passed. Native HTTP checks covered 134 module/create/detail/archive pages plus English/Kiswahili and the existing read-only accounting actor, retaining form actions, named fields and CSRF. Nine actual JSP snapshots exercised nine in-place navigation transitions with no script errors, persistent topbar/sidebar identity and no duplicate table/select controls; COA disclosure and selection remained functional. The local application was rebuilt and restarted on port 8093 with PostgreSQL 18 backup tooling and ten-second backup polling retained.

## Spinner-only transition cleanup

The transition mask now covers every main-content child, including the outgoing title, breadcrumbs and actions. Its state lives on the document root so replacing the main element cannot expose incoming content. Incoming markup carries its initialization mask before insertion. The same spinner position is used during fetch and component initialization; content is revealed after ready/load handlers, pageshow and their initialization frame complete. Cancellation/failure restores the original native content. Topbar and sidebar remain outside the mask.

The regression test holds both the network response and an incoming script, checks outgoing/incoming visibility and opacity, and confirms that frame-based initialization finishes while masked. It also verifies recovery and the final reveal without a timer-based minimum loading duration. Browser pixel/animation verification remains outside the current tool instructions.

Follow-up acceptance: 14 DOM tests, two architecture tests and the CSS/WAR build passed. Twelve native HTTP checks and four read-only-account checks retained form/CSRF integrity. Eleven actual JSP transitions, including dashboard, receipts, payments, configuration and financial reports, retained shell identity, exposed no outgoing content during the pending state and completed without script errors. Evidence is under ignored `.verification/workspace-transition-cleanup-20261116/`. Navigation/loading asset version `20261116-workspaces2` requires one refresh for an already-open older document.

## Navbar language verification â€” 17 November 2026

`npm run test:ui` passed all 46 tests. The focused native PostgreSQL user-form workflow passed server-rendered language selection, filter retention and session persistence, alongside existing validation/CSRF and role checks. The Maven WAR package and diff whitespace checks passed; no Java runtime class, schema or financial-statement presentation payload changed.

Browser checks verified English/Kiswahili switching, keyboard selection, dirty-form Continue editing/Discard, register filter retention and language persistence on another page. Both languages fit all six widths (320, 375, 768, 1024, 1440 and 2560px) without page overflow or a clipped/out-of-navbar language control. The local preview uses an isolated `FCMS_PREVIEW_SESSION` cookie to avoid collisions with other local applications; this is a verification launcher option, not a production configuration change. Evidence and the screenshot are under ignored `.verification/navbar-language-20261117/`.


## Cloudscape service navigation — 18 November 2026

The sidebar follows the [Cloudscape side-navigation pattern](https://cloudscape.design/patterns/general/service-navigation/side-navigation/) through the existing JSP shell. Service identity links to the authorized home route. White navigation, 18px/22px service/group headings, 14px/20px links, 28px leading alignment, neutral dividers and a blue bold current link follow the inspected live example. FCMS destinations and permission conditions remain authoritative.

Accounting office contains one level of disclosures for Transactions, Configuration and Vouchers. Configuration exposes chart of accounts, Activities and Transactions directly. Reports, Clients, Sales, Operations and Administration use the existing authorized section groups. Active ancestors expand automatically. Dashboard/register pages open normally; focused create/edit pages close the navigation and restore the previous state when returning. Explicit manual choices remain effective. Mobile drawer masking starts below the measured top bar, with Escape/outside dismissal and restored focus.

Verification used Chromium browser checks only: 60 empty/populated client-table cases across 320, 375, 768, 1024, 1440 and 2560px; 10 Kiswahili cases; seven preference, selection, keyboard and navigation-state checks; Accounts Clerk, Staff and Administrator navigation/home checks. No page errors were observed. Browser results and screenshots are retained under the ignored `.verification/browser-layout-20261118/` directory. The main preview served the new cache-versioned assets and matching navigation typography after installation. No Java classes, database migrations or financial-statement renderers were changed.
