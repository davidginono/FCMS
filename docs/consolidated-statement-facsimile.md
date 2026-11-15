# Consolidated statement export

The user's November 12 correction makes the actual Microfinance income statement the visual authority. The former blue landscape FCMS design is superseded. The /reports preview, /reports/print and /reports/statement.pdf now share the monochrome income paper, five original document columns, section order, underlined totals and surplus/deficit label. The standalone print page follows Microfinance's print.jsp rather than rendering a copy of the interactive workspace.

FinancialReportService owns the read-only REPEATABLE_READ snapshot and report.read authorization. Gross received fees plus other income form income; client costs plus paid expenses form expenses. All five client categories' received fees, costs and margins remain in the appendix. Previous operational figures are explicitly Unavailable. This operational report is identified as Fee and expense summary; it does not impersonate the posted general ledger. Dates, monetary signs, exact cents, required draft disclosure and source coverage remain authoritative.

The adapter uses FCMS's installed OpenPDF renderer and actual Microfinance drawing geometry, fonts and page dimensions. There is no new dependency, schema, financial write, cache or executor. The existing two-render limit includes the page-dictionary precision pass. Application rollback uses the prior compatible WAR and needs no data rollback for this presentation change.

The current evidence for this and the other financial reports is in docs/financial-report-source-review.md. Original-renderer comparisons use synthetic figures and fixed timestamps; no legacy database entrypoint is executed. Those comparisons prove the tested drawing fidelity with the same prepared content, not the presence of missing COA reporting roles, Notes or a ledger export.

Historical blue-layout tests remain in local verification history but are not acceptance of the requested Microfinance target.

Final acceptance: both native languages and print/preview routes pass; the consolidated layouts have no page-level overflow at all six required widths. The accepted WAR/runtime identity, seven focused tests, 34 original-renderer pages and twelve native financial PDF checks are recorded in the source-review document. Samples: output/pdf/fcms-consolidated-income-statement.pdf and its -sw variant.

## Original top-field fidelity — November12

The final top-field review removes only the added availability suffix from the previous-period date header, retaining unavailable comparison amounts. Seventy direct original-CSS preview/print comparisons have zero differences in field geometry, fonts and spacing; all 34 refreshed PDF reference pages remain pixel-identical. The final ten-test package is db994e3087ee18ba51490582dbdf7b3aa92c14673c7a2746fa309b9cdb3f6078, owned preview PID17840. Complete scope, commands and evidence: docs/report-header-fidelity.md. Remaining module/report gaps are unchanged.
