-- The book/period/equity management workspaces are retired. Financial history is retained.
-- accounting.setup and accounting.review remain in use for COA, transaction and journal workflows.
delete from user_claim_denials where claim='accounting.period.close';
delete from user_claims where claim='accounting.period.close';
delete from role_claims where claim='accounting.period.close';
delete from claims where name='accounting.period.close';
