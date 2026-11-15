# Prospects, invoices and facsimile exports

The November 3 scope extension adds /sales/prospects and /sales/invoices. Prospects permit SACCOS, AMCOS and Union; invoices also permit Microfinance and Other. They retain independent document number namespaces. They neither create active-client records nor post or settle GL money. Activities/Transactions configuration remains the accounting source setup.

V23 adds an initially empty sales document register with versioned snapshots. Company-scoped sales.prospect.read/write/export and sales.invoice.read/write/export are separate permissions; the administrator bundle receives them. Each application command rechecks an active identity and effective claims. Read permission is required with write/export. Existing client or finance claims alone do not grant access. No staff user is automatically granted these claims.

The application calculates totals from 1–50 items with nonnegative decimal amounts, at most 12 whole digits and two decimal places. Total is bounded to 999,999,999,999.99; descriptions are bounded individually and in aggregate. PostgreSQL independently checks item count, amount syntax and total equality. No client-provided total is bound. Amounts use BigDecimal.

Edits require the retained version. A unique actor/request key and SHA-256 fingerprint protect creation/edit retries. An advisory transaction lock serializes identical actor/request commands; row versions arbitrate unrelated edits. A changed payload cannot reuse the same key. Revisions preserve each complete saved snapshot, actor and time; the shared security audit records the mutation without copying customer contact data into logs. There is no destructive delete route, and PostgreSQL rejects deletion of documents and changes/deletion of revisions. Queries return only size+1 records (10/25/50/100), with whitelisted stable sorting and escaped search.

The shared Cloudscape JSP shell, resource table, form sections, native date controls, selection Actions, dirty-exit confirmation and EN/SW bundles are reused. Create/edit are focused task pages. Line-item Add/Remove are explicit non-saving POSTs; validation failure preserves values. Normal saves redirect to the register. PDF download and Print retain the same read/export authorization and no-store responses.

## Original stationery and provenance

SalesPdfService ports the geometry of C:/xampp/htdocs/fcms/invoice_pdf.php, using the existing OpenPDF dependency. The user explicitly requires facsimiles. Page size is 210 × 249 mm, with the original three-stripe ribbons, customer/date boxes, cyan amount column, payment boxes, supplied bank details, office-use box, stamp and proforma disclaimer. Invoice/proforma title and number labels remain distinct. The formal stationery remains in its original English; EN/SW applies to application controls. Changed document values are drawn into the same fields. No Python/PHP process is used by the running Java application.

The original company artwork was copied unchanged under src/main/resources/sales-stationery from C:/xampp/htdocs/fcms/assets/images, under the user's explicit authorization. It is private FCMS company material, not represented as third-party open-source art. No separate license file was present in the PHP project. Artwork provenance: invoice-company-logo.png SHA-256 CC42E86AB5C502ED06D4C7CA34C5DDA807378020B463366BAEC30F5E16B2C0A8; company-stamp.png SHA-256 BFB6E6FC74FA790E5A9864E8E5721C00D29AAF2D09DFC5021C13ED38280D9C70. Helvetica reproduces the original Arial PDF metrics; the existing bundled SIL-licensed Noto face preserves characters unavailable in the original WinAnsi font.

At most two sales PDFs render concurrently per node; excess requests fail with the existing export-busy response. Documents are bounded by the item/description limits. Long descriptions continue onto pages with repeated company/customer/table headers; totals and payment/stamp details belong to the final table page, and terms receive their own continuation pages.

## Rollout

Back up and test the forward migration on a copy first. No PHP records are silently imported. Importing historical prospective clients/invoices requires a reviewed mapping, provenance and reconciliation of item totals, document numbers and revision identities. Application rollback retains all new rows and revision evidence; do not drop them to make an older binary appear compatible.
