# Accounting receipt and payment export facsimiles

The RECEIPT and PAYMENT PDF route retains JournalService authorization and uses a repeatable-read, read-only snapshot. No posting, approval, schema, permission, balance or audit mutation is introduced.

## Source and adaptation

Layout source: Microfinance/src/main/java/com/sacco/mvp/accounting/reports/VoucherPdfDocument.java, copied and adapted with the user’s explicit authorization. VoucherStationery ports its geometry to the existing OpenPDF dependency; PDFBox is used only by the isolated reference runner under .verification. Both projects contain the identical NotoSans-Regular.ttf (SHA-256 b85c38ecea8a7cfb39c24e395a4007474fa5a4fc864f6ee33309eb4948d232d5), covered by the retained bundled font license.

Preserved: A4 dimensions (OpenPDF serializes bounds to 0.01 pt), 22/573 pt horizontal boundaries, institution/title/metadata positions, party and payment-detail field slots, payment-method checkboxes, table widths, writing space, wrapping, multi-page continuation, amount words, totals, narration and posting blocks, page N / total footer.

FCMS has no voucher payer/payee contact, branch, stationery logo or payment-channel field. Those slots are unfilled; a cash-account flag does not prove a payment channel. Header slots show the actual voucher ID, date and state. FCMS provenance includes maker, checker, posted time, review, evidence, cash-flow classification and both reversal links. No signature or approval is invented.

Receipt rows show signed non-cash counter-entries (credit minus debit), so adjustments reconcile to cash movement. Payment rows show every debit/credit line; total debit/credit remain distinct from the voucher’s cash amount. The bounded journal-line limit also bounds the additional cash-account ID query. Account cash classification is immutable through AccountingService.saveAccount.

## Verification

Focused tests: AccountingVoucherPdfTests and ArchitectureTests. They cover adjusted receipt allocation, exact cents, both locales, original A4/header geometry, provenance, long content, complete final text and page-number totals. PostgresModuleTests verifies actual document allocations and money-account metadata, the real export service, statement workflow and forbidden actor.

Reference runner: .verification/voucher-facsimile/Reference.java compiles the unmodified source renderer outside production. source-*.json captures precisely the FCMS-prepared display values; reference-*.pdf and spring-*.pdf use the same values and font. compare.py renders every page at 1100 px width and records page counts and identical-pixel percentages in comparison.json. Visual inspection is required in addition to text assertions.

Acceptance evidence will be recorded after the final package, rendered comparison and local HTTP/browser checks.

Journal/opening/reversal exports now use original journal stationery with separate acceptance evidence in docs/journal-export-facsimiles.md. Financial-statement and trial exports now use the original stationery with separate acceptance evidence in docs/financial-statement-facsimiles.md. This receipt/payment slice alone does not certify other exports.

## Operations

No migration, dependency, external call, cache or executor change. The existing two-render semaphore bounds rendering. Rollback uses the prior WAR; no data rollback is necessary. Fixture preview upgrades must verify the owned process and retain data counts before/after. No throughput or production-capacity assertion is made.

Regression baseline: mvn -o package passed all 86 tests with no failures/errors/skips on fcms_codex_verify_voucher_20261103_a at 2026-11-03 22:07:51 +03. Log: .verification/voucher-facsimile-package.log. This precedes the final payment header currency-label correction, which is covered by the final affected package check.

Final display refinements: payment headers reuse the already currency-qualified gl.debit/gl.credit labels once; posted timestamps use dd/MM/uuuu HH:mm:ss z in Africa/Dar_es_Salaam, matching the source stationery independently of the host timezone. Focused assertions cover both.

Accepted affected package: mvn -o -Dtest=AccountingVoucherPdfTests,ArchitectureTests package passed five checks with no failures/errors/skips at 2026-11-03 22:21:19 +03 (.verification/voucher-facsimile-accepted-package.log). The accepted reference comparison covers fifteen pages across EN/SW short documents, four-page long receipts and seven-page long payment vouchers; all page counts match, minimum identical pixels 99.89811% at 1100 px width. Final source-prepared timestamps include seconds and EAT.

Preview WAR SHA-256: 280FD2FCFF880C0547CBACF0C8A0AB9018C8CF87F9897AD558C8F23EB2F75485; owned PID25188, loopback8093, retained fcms_codex_verify_config_20261103_d database. Before/after journal, line, user, client and sales-document counts match (.verification/voucher-facsimile/preview-{before,after}-counts.txt).

Running-WAR HTTP acceptance: authenticated native detail pages and receipt1/payment2 PDF attachments return200; Cache-Control:no-store and attachment headers are verified. Actual downloads: .verification/voucher-facsimile/http-receipt.pdf and http-payment.pdf. Browser attachment recovered through the documented tabs API, but interaction and screenshot commands repeatedly timed out; native menu clicks/downloads are unverified. No browser permission/security setting was changed or financial command submitted. The owned temporary tab is unmarked for automatic cleanup.

Final visual inspection: the actual packaged HTTP receipt and payment voucher both render as one unclipped A4 page with correct money, provenance and EAT timestamps. Payment currency appears once per header. Health remains200; git diff --check passes. Evidence: http-checks.json, http-receipt.png and http-payment.png. The overall FCMS goal remains active for the separately listed completion gates.
