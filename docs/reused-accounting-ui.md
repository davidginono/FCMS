# Reused accounting document and preview designs

Source project: C:/Users/USER/Desktop/Microfinance. The user explicitly authorized copying and adapting reusable components and requested the receipt/voucher export formats and financial-statement preview/drilling design.

## Source-to-FCMS mapping

| Microfinance source | FCMS adaptation |
| --- | --- |
| accounting/statements/preview.jsp, print.jsp | statement.jsp, statement-paper.jspf, statement-print.jsp and statement-drill.jsp |
| financial-statements.css, financial-statement-print.css | Copied style sheets with a small FCMS column-layout adaptation |
| financial-statements.js view-settings section | financial-statement-view.js; only font/density preferences are stored, and native authorized links own navigation |
| accounting/vouchers/document.jspf and accounting-vouchers.css document rules | voucher-document.jspf and voucher-document.css, using FCMS journal evidence |
| VoucherPdfDocument / VoucherPdfService stationery | AccountingDocumentPdfService, adapted to the existing OpenPDF dependency without adding PDFBox |
| VoucherPdfService exact decimal EN/SW amount wording | AmountInWords |
| report-fonts/NotoSans-Regular.ttf | Copied unmodified with SIL Open Font License and source notice |

## Data and behavior

Statement paper and PDF output use posted GL data with explicit draft, date, policy and source-coverage context. Drill links preserve the date range: statement line → contributing accounts → contributing posted journal lines → voucher. Opening balances, closing balances, activity and cash-flow categories each use their actual inclusion/sign rules. Search/pagination do not turn the loaded page into the reported total.

Receipt/payment previews and PDFs retain actual account snapshots, reference, evidence, narration, maker/checker identities and recorded posting time. Receipt/payment voucher amounts are the cash movement; total debits and credits remain separately labeled. Missing party addresses, branch identities, logos, payment methods and signatures are not invented. FCMS source rules and independent financial approvals remain authoritative.

No Microfinance application service, permission name, lending workflow or financial dataset is imported. Document previews retain financial labels and evidence values without explanatory page descriptions.

## Canonical Activities and Transactions configuration
The activity/transaction table selection, modal creation and editable template composition were inspected in Microfinance src/main/webapp/WEB-INF/jsp/accounting/library.jsp, its referenced fragments and src/main/resources/static/js/accounting-library.js, then adapted to FCMS JSP components. FCMS owns every route, permission, account choice, source event, independent review and server submission. Shared table preferences/pagination, remote choices, form recovery and workspace-only modal blur are reused. No lending configuration or business data was copied.

Financial-statement extension: AccountingStationery ports the original AccountingPdfDocument statement/equity/trial styles to the installed OpenPDF API. StatementDocument and StatementPresentation prepare FCMS account rows, exact comparisons, equity components and provenance for the original preview structure. Copy/adaptation provenance and original-renderer verification are in docs/financial-statement-facsimiles.md. No PDFBox dependency was added to production.

Journal export extension: JournalStationery ports VoucherPdfService.Layout journal geometry. Its input preserves independent FCMS ledger lines rather than importing paired-transaction semantics. Source/reference adaptation and complete acceptance evidence are in docs/journal-export-facsimiles.md.
