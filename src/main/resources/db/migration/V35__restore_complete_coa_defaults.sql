-- Repair charts whose institution defaults were removed after V29 was applied.
-- Preserve compatible identities/names and all financial/reporting records.
set local lock_timeout = '10s';
lock table gl_accounts in share row exclusive mode;

do $$
declare d record; p gl_accounts; a gl_accounts; expected_parent bigint;
begin
 for d in select * from (values
  ('100000','Assets',null,1,'ASSET','DEBIT'),
  ('200000','Liabilities',null,1,'LIABILITY','CREDIT'),
  ('300000','Equity',null,1,'EQUITY','CREDIT'),
  ('400000','Expenses',null,1,'EXPENSE','DEBIT'),
  ('500000','Income',null,1,'INCOME','CREDIT'),
  ('110000','Current assets','100000',2,'ASSET','DEBIT'),
  ('120000','Non-current assets','100000',2,'ASSET','DEBIT'),
  ('210000','Current liabilities','200000',2,'LIABILITY','CREDIT'),
  ('220000','Non-current liabilities','200000',2,'LIABILITY','CREDIT'),
  ('111000','Cash and Bank','110000',3,'ASSET','DEBIT')
 ) as defaults(code,name,parent_code,depth,classification,normal_balance)
 loop
  expected_parent:=null;
  if d.parent_code is not null then
   select * into p from gl_accounts where code=d.parent_code;
   if p.id is null or p.depth<>d.depth-1 or p.classification<>d.classification
      or p.normal_balance<>d.normal_balance or not p.active or p.cash_account then
    raise exception 'COA default conflict: parent % for % must be an active compatible group',d.parent_code,d.code using errcode='23514';
   end if;
   expected_parent:=p.id;
  end if;
  select * into a from gl_accounts where code=d.code;
  if a.id is not null then
   if a.parent_id is distinct from expected_parent or a.depth<>d.depth or a.classification<>d.classification
      or a.normal_balance<>d.normal_balance or not a.active or a.cash_account then
    raise exception 'COA default conflict: code % has incompatible parent, level, classification, balance or status',d.code using errcode='23514';
   end if;
  else
   insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)
    values(d.code,d.name,d.classification,d.normal_balance,d.depth,expected_parent);
  end if;
 end loop;
end; $$;
