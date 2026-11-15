# Microfinance UI reference for cloud agents

This repository includes the approved UI guidance and a selected Microfinance presentation-source snapshot dated **2026-11-15**. A fresh Linux/cloud checkout has everything listed here; no Windows folder, Microfinance clone, computer-control tool or account connection is needed to read it.

From the FCMS repository root:

```bash
python3 scripts/verify-agent-references.py
```

Read the root [agent instructions](../../AGENTS.md), then the task-relevant local skills:

| Skill | Use |
| --- | --- |
| [Spring baseline](../../.agents/skills/springboot-1k-rps-optimization/SKILL.md) | Mandatory Spring MVC/JSP/PostgreSQL architecture, security, financial integrity and measured performance |
| [UI governance](../../.agents/skills/microfinance-ui-governance/SKILL.md) | Navigation, workflows, financial states, localization |
| [UI style](../../.agents/skills/microfinance-ui-style/SKILL.md) | Shell, sections, forms, controls, feedback, modal geometry, responsive behavior |
| [Table standard](../../.agents/skills/microfinance-table-standard/SKILL.md) | Grouped COA, selection, commands, scrolling, sort/paging, preferences and split details |

The three UI skills are FCMS adaptations, not verbatim instructions from another application. They preserve the approved Cloudscape JSP patterns and remove local workstation dependencies, unavailable screenshots, unrelated lending requirements and conflicting financial-approval assumptions. The Spring baseline and its supporting files are copied with their provenance in [the manifest](manifest.json). Its heuristic audit excludes the reference snapshot, agent skills and ignored verification artifacts so those files are not reported as FCMS runtime code; invoke the helper with Bash from a cloud checkout.

## Components and FCMS adaptations

Original paths below are relative to [source/](source/); FCMS paths are relative to the repository root. Start with the FCMS implementation, then inspect the source when a required pattern is missing. The manifest lists every exact source path, original and stored SHA-256, byte length and any normalization/adaptation. Text is UTF-8 with LF endings for consistent Linux/Windows checkouts and executable Bash helpers. The snapshot is outside Maven's `src/` and the runtime web root. Tailwind explicitly excludes reference material and skills from candidate discovery.

| Pattern | Included Microfinance source | Current FCMS implementation |
| --- | --- | --- |
| Shell integration and shared surfaces | `src/main/resources/static/css/{shell,console-components,workspace-resources}.css`, `js/{shell,workspace-navigation}.js` | `src/main/webapp/WEB-INF/jsp/reusableuicomponents/navigation/`, `src/main/resources/static/css/tailwind.css`, `js/app.js` |
| Resource tables, preferences, commands, paging | `static/{css,js}/resource-table.*`, `fragments/resource-table-labels.jspf` | [resource-table.js](../../src/main/resources/static/js/resource-table.js), [resource-table.css](../../src/main/resources/static/css/resource-table.css), `reusableuicomponents/{resource-table-labels,register-pager,register-filters}.jspf` |
| Selection and split previews | `static/js/resource-split-view.js`, `accounting-accounts.js`, `accounting-library.js` | `static/js/{resource-split-view,grouped-resource-table,transaction-config}.js` with FCMS controllers |
| Grouped COA | `jsp/accounting/{accounts,account-rows}.jsp`, `static/css/accounting-ledger.css` | [accounts.jsp](../../src/main/webapp/WEB-INF/jsp/accounting/accounts.jsp), `grouped-resource-table.js` and `grouped-resource-table.css` |
| Activities and transaction templates | `jsp/accounting/library*.jsp*`, `accounting-form-support.jspf`, `static/js/accounting-library.js` | `jsp/accounting/config/`, `static/js/transaction-config.js`; FCMS owns events, claims and review |
| Form validation and dirty-exit/recovery references | `static/{css,js}/form-validation.*`, `accounting-form-ux.js`, `workspace-navigation.js` | `reusableuicomponents/form-support.jspf`, `static/js/{form-ux,app}.js` |
| Modals and confirmations | `fragments/confirm-modal.jspf`, activity/transaction modal fragments, `resource-table.js` preferences | [modal-workspace.js](../../src/main/resources/static/js/modal-workspace.js), FCMS shared modal styles and `config/create-modal.jspf` |
| Select/search/date/loading/amount controls | `select-enhancer.*`, `live-search.js`, `date-controls.*`, `ui-loading.*`, `amount-inputs.*`, selector fragment | FCMS `select-enhancer.*`, `choice-loader.js`, `live-search.js`, `date-controls.*`, `ui-loading.*`, native financial bindings |
| Resource facts, tabs, statuses and notices | `console-resource-details.*`, `fragments/alerts.jspf` | FCMS `app.js`, shared section/status/feedback markup; reuse source APIs only after deliberate integration |
| Configurable dashboards and charts | `dashboard-board.css`, `dashboard-{board,charts,data,work}.js`, dashboard fragments and five SVGs | Reference implementation; FCMS dashboard routes/view models need explicit integration before these APIs can be used |
| Statement preview, print and exports | See the authority map below | FCMS statement paper/print/drill, presentation and stationery implementations |

Paths abbreviated as `static/` and `jsp/` above expand to `src/main/resources/static/` and `src/main/webapp/WEB-INF/jsp/`. These are dependency groups, not a promise that every source API already exists in FCMS.

## Integration boundaries

- **Shell:** Original `header.jspf`, `sidebar.jspf` and `footer.jspf` are intentionally excluded. Their include sites in source examples map to FCMS's existing layout/navigation; do not copy missing shell files from an unavailable checkout. `shell.js` includes source workflow behaviors and global helpers: inspect individual features, not a wholesale script replacement.
- **JSP:** The examples use Jakarta JSTL, Spring form/message/security tags, source `@access` claims, source principal fields and prepared view models. Preserve FCMS server authorization, escaped output, CSRF, IDs/bindings, and controller/service ownership. Merge selected EN/SW labels from [labels/](labels/), then supply any dynamic keys from FCMS's own bundles; do not replace whole bundles. Literal source defaults and dynamic message-code expressions still require review/localization.
- **Tables:** Source `resource-table.js` can use `AppLoading`, `AppSearch`, `NeoSelect`, `ResourceSplitView`, details and workspace hooks. Included files show those contracts. FCMS already has adapted table/split/search components; do not mix incompatible source and FCMS versions or register duplicate handlers. Real bounded server queries, sorting and endpoints must exist before exposing controls.
- **Forms/modals:** Original fragments may contain inline scripts, older focus/scroll behavior, explanatory markup or full-viewport overlays. They are comparison material. Use the adapted local skills and FCMS modal manager so blur stays within main content and top/side navigation remains clear. Never transplant source admin form opt-ins, business approvals or a second POST owner.
- **Dashboards:** Adapt `principal.saccoId`, station/role scope, endpoints and layout keys to FCMS. Optional `SaccosFilePickers`/`SaccosUiState` hooks in the source shell belong to omitted source features; they are not required FCMS globals. Source chart assumptions (including signed values) require review before use for financial data.
- **Assets:** Shared CSS URLs resolve to the included eight Open Sans font faces. Dashboard SVGs are included. SVG paths embedded in shared code retain the Cloudscape license/notice. Tailwind utility classes require the FCMS build pipeline; the snapshot is not a prebuilt standalone site. Amazon Ember is not needed or bundled.
- **Java/export:** The original statement models, calculators, presentation/export and stationery are source evidence, not a compilable module. They refer to Lombok, Spring MessageSource, PDFBox, POI and omitted application infrastructure (clock, export limiter, authorization/data services, timestamp helpers). FCMS uses its existing OpenPDF exporter. Do not add dependencies or source business services just to compile a reference. The included `application-time.tld` documents source function signatures; FCMS owns its date/time adapter.

## Financial-statement authority

Inspect the entire original rendering path, not only a shared font helper:

| Responsibility | Included original source |
| --- | --- |
| Workspace, top details, paper display and dated drill links | [preview.jsp](source/src/main/webapp/WEB-INF/jsp/accounting/statements/preview.jsp), [print.jsp](source/src/main/webapp/WEB-INF/jsp/accounting/statements/print.jsp), `movements.jsp`, `support.jspf` and `feedback.jspf` |
| Income, position, cash flow, equity, trial and account-ledger display rows/columns | [StatementPresentation.java](source/src/main/java/com/sacco/mvp/accounting/statements/service/StatementPresentation.java), `StatementModels.java`, `StatementCalculator.java`, `EquityStatementCalculator.java` |
| PDF/Excel header context and export structure | [StatementExportService.java](source/src/main/java/com/sacco/mvp/accounting/statements/service/StatementExportService.java) |
| Page geometry, typography, totals and continuation layout | [AccountingPdfDocument.java](source/src/main/java/com/sacco/mvp/accounting/reports/AccountingPdfDocument.java), `financial-statements.css`, `financial-statement-print.css`, `print-footer.css`, Noto Sans font and notices |
| Receipt/payment/journal stationery | `accounting/vouchers/document.jspf`, `accounting-vouchers.css`, `VoucherPdfDocument.java`, `VoucherPdfService.java`, `reportMoney.tag`, `reportExport.tag` |

Use the actual Microfinance income-statement design, not the old FCMS blue profit-and-loss design. Match top details' positioning, spacing and typography as well as the body, page size, borders, totals and footer. Retain selected reporting dates through authorized account/transaction/voucher drills. Verification requires identical synthetic inputs/timestamps and rendered page/font-character comparisons, not a similarity score alone.

FCMS adaptations and deliberate corrections are documented in [source reuse](../../docs/reused-accounting-ui.md), [financial-statement facsimiles](../../docs/financial-statement-facsimiles.md), [cash/equity/drills](../../docs/cash-equity-and-drills.md) and [financial report source review](../../docs/financial-report-source-review.md). The user's indirect cash-flow requirement injects income surplus in operating activities; do not overwrite that correction with the original direct-method rows. The [legacy PHP archive](../README.md) governs its own operational layouts, not these GL statement designs.

## Sanitization, provenance and licenses

The package is an allowlisted presentation snapshot. It excludes application configuration/environment files, keys/credentials, database contents/migrations, logs, backups, uploads, actual institution identity/contact values and real user/financial records. Unchanged source still contains template field names, source domains/routes and permission identifiers, which document dependencies without granting FCMS access. Only component-related labels were extracted from the source bundles, with email/URL values excluded.

The manifest distinguishes unchanged or line-ending-normalized originals, filtered labels and adapted skills. It contains repository-relative paths and hashes, without workstation paths. Do not infer a general license for the source application: the repository owner's authorization covers this reuse; no new upstream license is invented. Retain third-party notices when adapting:

- `source/src/main/resources/static/js/vendor/cloudscape-icons.LICENSE` and `.NOTICE` (Cloudscape-derived SVG paths).
- `source/src/main/resources/static/fonts/open-sans/CLOUDSCAPE-GLOBAL-STYLES-LICENSE.txt` and notice (Open Sans package assets).
- `source/src/main/resources/report-fonts/LICENSE.txt` and `NOTICE.md` (Noto Sans).

The verification script checks hashes, portable Markdown links, skill metadata, local JSP/CSS dependencies and prohibited private artifacts. It does not prove application behavior, financial correctness or live JSP rendering. Browser and Computer Use checks were not performed for this reference package.
