# Journal, opening and reversal export facsimiles

## Source and financial mapping

The user authorized Microfinance reuse and requires original export formats. JournalStationery ports the journal Layout from Microfinance/accounting/service/VoucherPdfService.java to the installed OpenPDF renderer. It preserves the A4 geometry, NotoSans font, title/identity metadata positions, date/reference pairing, 523×38pt gray heading, original five column positions, writing rules, wrapping/continuation, words/totals/narration/posting sections and footer with voucher number/state and page N / total.

The source Microfinance model is paired transactions. FCMS stores independently balanced ledger lines. Every FCMS retained line is therefore presented once, with its exact account snapshot, description and signed-side debit or credit. Blank opposite-side cells do not imply a fabricated pair. The same journal stationery covers JOURNAL, OPENING and REVERSAL; FCMS kind labels remain distinct.

Maker/checker IDs and names, evidence, cash-flow classification, review comment, timestamp to seconds in EAT, state and both reversal links remain visible. Missing checker/posting time stays blank; no approval, party, branch or signature is invented. Exact amounts and amount words use the existing BigDecimal projection. Totals retain their separate debit, credit and voucher-amount labels.

JournalDocumentService authorization and repeatable-read projection are unchanged. Rendering remains limited by the existing two-permit semaphore. No schema, query, permission, financial-write, executor or production dependency change. Removing the obsolete generic table/footer helpers leaves each original stationery responsible for its own footer and layout.

## Reference comparison method

The original source is compiled under .verification/journal-facsimile with its existing PDFBox3.0.3 dependency and original drawing/page/field/table/total/wrap methods. Only the Lombok-generated constructor is replaced with the equivalent explicit constructor for isolated compilation. The reference driver invokes those unmodified private drawing methods. Its input driver adapts independent FCMS lines and added provenance; it does not claim that the source paired financial model is the FCMS model.

Prepared source-*.json and spring-*.pdf files come from the application renderer. JournalReference.java feeds the same prepared values into original geometry methods; reference-*.pdf and raster comparison.json record page counts and identical-pixel measurements. Long cases contain50 actual lines with permitted description/account lengths, not one fabricated transaction pair.

## Verification

JournalFacsimileTests checks unmatched independent lines and exact totals; all three kinds in EN/SW; original page/header geometry and every page count; evidence, maker/checker, EAT time, review and linked reversals; missing review metadata;50 long lines and complete last account/amount/provenance. Receipt/payment and statement regression checks remain included.

PostgresModuleTests checks native authenticated PDF responses in both locales for journal/opening/reversal, no-store and attachment headers, real retained account snapshots and provenance, and forbidden staff actors. A full package with FCMS_TEST_OPENING_MODE=JOURNAL exercises real approved opening journal data in an isolated fixture database. The existing ZERO-mode baseline remains separate evidence.

Accepted package: mvn -o package with FCMS_TEST_OPENING_MODE=JOURNAL passed101 tests, no failures/errors/skips, at2026-11-04 03:31:08 +03 on fresh fcms_codex_verify_journal_20261104_a. Log: .verification/journal-facsimile-package.log. All32 main module checks pass with actual journal-based opening balances, native EN/SW PDF routes and forbidden-staff assertions. The separate previous97-test ZERO-mode baseline remains valid for its recorded revision.

Accepted reference comparison:40 pages across eight EN/SW/long fixtures have identical page counts and at least99.85067% identical pixels at1100px width. Both50-line long cases occupy17 pages and retain the last account/description/amount and posting record. reference-provenance.json proves the copied private Layout body equals the original source; comparison-accepted.log and comparison.json record the final measurements.

Packaged HTTP acceptance: eight native cases from the retained main fixture (journal, reversal, receipt, payment) and six from a temporary journal-opening fixture (journal, opening, reversal) pass detail/PDF/no-store/attachment checks in both languages. All14 actual PDFs have correct page numbers, EAT timestamps and text inside page bounds. Evidence: retained-http-checks.json, http-checks.json, http-pdf-checks.json and http-*.pdf under .verification/journal-facsimile. Actual sample journal/opening/reversal downloads are copied to output/pdf.

Manual PNG review confirms the three actual English downloads and the long Kiswahili final page are unclipped, preserve exact amounts and show retained provenance/reversal links.

Retained preview: PID11864, loopback8093, WAR SHA-25676736FE2F4E806A7F6598976A83C8A890BCC101FE17014802E381F6A01D2F321, database fcms_codex_verify_config_20261103_d. Before/after journal, GL line, user, client and sales-document counts match. Temporary opening fixture PID31176 was stopped after verifying its database and8094 port identity. Tested source hashes match frozen-source-manifest.json. No production data or process was changed.

## Operations and remaining scope

No data migration or data rollback is necessary. Rollback uses the prior WAR. Stop only the owned preview process before packaging; retain fixture database record counts. All verification writes stay in explicitly named fcms_codex_verify_* databases. No performance-capacity claim is made.

Live browser checks remain pending while local browser use is disabled in Work settings. Legacy operational/client report format audits and the wider whole-goal completion gates remain separate work.
