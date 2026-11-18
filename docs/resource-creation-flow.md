# Resource creation and feedback

FCMS adapts the [Cloudscape create flow](https://cloudscape.design/patterns/resource-management/create/) through its shared JSP shell and Microfinance UI skills. This contract applies to native forms and progressive workspace navigation.

## Destinations

| Resource or command | Successful destination | Notice |
| --- | --- | --- |
| User | `/admin/users` | User created; link opens Edit directly |
| SACCOS, AMCOS, union, microfinance or other client | The category register | Client created |
| Task, leave or staff report | Its workspace register | Created; existing workflow state retained |
| Petty cash, cloud backup or bulk SMS ledger entry | Its ledger register | Entry recorded |
| Proforma or invoice | Its sales register | Document created; this does not post money |
| Account | COA register with selected parent; Cancel returns to the same parent | Account created |
| Activity or transaction definition | Its configuration register, preserving activity filter | Definition created; create dialog closes |
| Receipt, payment, journal or source preparation | Voucher register, with voucher kind where applicable | Draft created; independent review still required |
| Account reporting proposal | Owning account's reporting history | Proposal submitted for review |
| CSV upload | Batch preview | Preview ready; nothing imported yet |
| CSV commit | SACCOS register | Clients imported; batch remains available |
| System backup request | Backup register | Queued, not completed; immediate queue failure is an error |

Retired accounting-book, accounting-period and equity-configuration pages remain unavailable.

Profile/settings edits and other existing save actions use the same destination notice component. Authentication/password recovery keeps its dedicated security flow.

## Server contract

Controllers call `ResourceFeedback` only after the application service returns successfully. The service transaction, authorization, ownership, period locks, maker/checker rules and idempotency keys remain authoritative. Browser forms use Post/Redirect/Get. A Spring flash attribute owns the severity, localized message key and authorized resource link; an opaque `_notice` query value matches the flash to the intended GET and prevents another concurrent refresh from consuming it. `saved` or `created` parameters alone cannot claim success.

The shared header renders a persistent dismissible notice above breadcrumbs, titles and table controls. A draft, pending proposal, import preview or queued backup must use wording that reflects that state. No success-only modal or intermediate user details page is introduced.

## Validation and recovery

Bean validation renders next to its bound field. `FieldValidationException` carries specific domain field paths, including indexed rows and collections. `FormErrors` maps only known uniqueness constraints to controls; database messages and submitted sensitive values never become user-facing text. Hidden integrity-field and unbound failures appear immediately above form actions. A forbidden operation remains forbidden.

`form-ux.js` remains the single submission owner. Opted-in final create commands call `resource-creation.js`: verify the current actor and refresh CSRF, perform one POST, then pass the already-followed destination HTML to `WorkspaceNavigation.present`. There is no second GET to lose the flash and no POST retry. Row-add/remove commands retain their existing draft flow.

Recoverable failures match only the resource form, ignoring navigation, search and logout forms, and copy only error text and CSRF into the live form. Native input nodes, passwords, selected files, chosen permissions and request keys remain intact. Errors update `aria-invalid` and `aria-describedby`, reveal optional sections and focus the first affected control. Pristine fields stay quiet; browser constraints validate on blur and while correcting. Incomplete input does not disable Create. Only request-owned disabled controls are restored.

Session verification failure allows an explicit retry; changed actors cannot submit another actor's draft. A transport timeout or ambiguous result after POST starts retains the form, blocks resubmission and offers the collection for reconciliation. Private drafts never enter browser storage. Without JavaScript, server validation and PRG remain available; browsers clear password/file inputs on a full response, as expected for native navigation.

## Verification

Verify actual successful command → redirect → register → notice; notice ordering, dismissal and one-time server delivery; unrelated GET and forged query parameters; duplicate and invalid input; native CSRF/authorization; financial draft/approval and import-preview boundaries; retained password/upload nodes, indexed and optional errors; double clicks, expired/changed sessions and uncertain responses; English/Kiswahili and the responsive widths in `AGENTS.md`. Approved financial preview, print and export renderers are outside this change.

The Accounting books, Accounting periods, Equity reporting and Equity movements creation/management flows are retired. See [accounting workspace retirement](accounting-workspace-retirement.md) for retained financial dependencies and direct URL behavior.

Client creation follows the [category contracts](client-register-parity.md). SACCOS, AMCOS, Union and Microfinance return to the register with the created record's fiscal-year filter and destination Flashbar. Other Clients returns to its all-dates register. Contract replacement and Delete/Mark inactive are independent version-checked commands; no fee form is replayed to change a contract or client lifecycle state.

## Microfinance voucher replacement

Receipts, payments and journals use `/accounting/vouchers/{receipts|payments|journals}/new`. Preview stays in the live form; successful draft/submission returns to that register with the shared Flashbar. The actor/request-bound number is reserved through a CSRF-protected POST and consumed only with the complete voucher. See [the full workflow and retention map](microfinance-voucher-workflows.md).

The 18 November Microfinance update generates ACT/TXN codes server-side and removes event/component inputs from ordinary transaction creation. The canonical voucher forms accept pair/split rows under the same destination and field-error contract; each transaction balances independently and the total counts it once. Retired posting-mapping redirects and journal editor APIs no longer exist; only the identified history/read/review/source/reversal paths remain. See `microfinance-voucher-workflows.md`.
