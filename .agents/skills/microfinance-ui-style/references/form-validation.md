# Validation and recovery

Adapted from the Microfinance Cloudscape validation contract. Inspect the original `form-validation.css` and `form-validation.js` under `reference/microfinance-ui/source/src/main/resources/static/`, then FCMS `form-support.jspf` and `form-ux.js`. Source `platform-form-ux.js` is deliberately excluded: its admin-route opt-in would bind different FCMS operations.

- Pristine fields stay quiet. Validate on blur and while correcting an error. Keep native constraints, custom validity, server validation and existing draft bypasses authoritative; do not call `form.submit()` to bypass them or disable Submit merely for missing values.
- Invalid fields use a thin red border and a 16px outlined error triangle with regular 14px red text immediately below the control, before essential hint text. Do not tint entire field groups or turn every field error into an Alert.
- Retain visible labels, `aria-invalid`, unique error IDs and existing `aria-describedby` hint associations. Native and enhanced select/date controls need the same accessible error state. Dynamic indexed rows must not duplicate IDs or retain stale error associations.
- Field-specific Spring errors stay beside bound controls. A corrected browser constraint does not prove a server conflict is resolved. Errors without a field use a focusable Alert just above actions; do not duplicate all field messages in a top summary.
- Reveal collapsed sections/tabs and focus the first error after failed submission. Preserve all native form nodes, values, selected uploads, CSRF, submitters and request keys. Live secret inputs may remain in memory only; redirect recovery may retain explicitly allowlisted non-secret fields.
- Never automatically resend an uncertain financial POST. Restore only controls disabled by the request lifecycle, leaving business-disabled controls unchanged.

Verify pristine/blur/correction/submit, required consent and groups, server conflicts, repeated rows, reset/reopen, optional-section errors, selects/date bounds, saved draft behavior, first-error focus and both languages. Component-only checks do not establish authenticated workflow correctness.
