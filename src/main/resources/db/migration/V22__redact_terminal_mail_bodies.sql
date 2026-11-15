-- Recovery tokens and OTPs are no longer needed after terminal delivery outcomes.
-- Retain the outbox identity, recipient, subject, timestamps and outcome for operations.
update mail_outbox set body='' where status in ('SENT','FAILED') and body<>'';
