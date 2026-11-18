-- Retire permissions for the removed expense/evidence and consolidated revenue workspaces.
-- Preserve all financial source rows and their audit history.
delete from user_claim_denials where claim in
 ('expense.read','expense.write','expense.approve','expense.assign',
  'expense.evidence.write','expense.evidence.review','finance.approve','report.read');
delete from user_claims where claim in
 ('expense.read','expense.write','expense.approve','expense.assign',
  'expense.evidence.write','expense.evidence.review','finance.approve','report.read');
delete from role_claims where claim in
 ('expense.read','expense.write','expense.approve','expense.assign',
  'expense.evidence.write','expense.evidence.review','finance.approve','report.read');
delete from claims where name in
 ('expense.read','expense.write','expense.approve','expense.assign',
  'expense.evidence.write','expense.evidence.review','finance.approve','report.read');
