-- Additive chart defaults only. Existing identities, names and approved reporting stay intact.
lock table gl_accounts in share row exclusive mode;
do $$
declare d record; p gl_accounts; a gl_accounts;
begin
 for d in select * from (values
  ('110000','Current assets','100000',2,'ASSET','DEBIT'),
  ('120000','Non-current assets','100000',2,'ASSET','DEBIT'),
  ('210000','Current liabilities','200000',2,'LIABILITY','CREDIT'),
  ('220000','Non-current liabilities','200000',2,'LIABILITY','CREDIT'),
  ('111000','Cash and Bank','110000',3,'ASSET','DEBIT')
 ) as defaults(code,name,parent_code,depth,classification,normal_balance)
 loop
  select * into p from gl_accounts where code=d.parent_code;
  if p.id is null or p.depth<>d.depth-1 or p.classification<>d.classification
     or p.normal_balance<>d.normal_balance or not p.active then
   raise exception 'COA default conflict: parent % for % must be an active compatible group',d.parent_code,d.code using errcode='23514';
  end if;
  select * into a from gl_accounts where code=d.code;
  if a.id is not null then
   if a.parent_id is distinct from p.id or a.depth<>d.depth or a.classification<>d.classification
      or a.normal_balance<>d.normal_balance or not a.active or a.cash_account then
    raise exception 'COA default conflict: code % has incompatible parent, level, classification, balance or status',d.code using errcode='23514';
   end if;
  else
   insert into gl_accounts(code,name,classification,normal_balance,depth,parent_id)
    values(d.code,d.name,d.classification,d.normal_balance,d.depth,p.id);
  end if;
 end loop;
end; $$;

-- Existing identities cannot change. Validate new identities and their complete parent chain.
create function fcms_gl_account_code_guard() returns trigger language plpgsql as $$
declare node gl_accounts; parent gl_accounts; expected_type text; expected_normal text;
begin
 node:=new;
 loop
  expected_type:=(array['ASSET','LIABILITY','EQUITY','EXPENSE','INCOME'])[substring(node.code,1,1)::integer];
  expected_normal:=case when expected_type in ('ASSET','EXPENSE') then 'DEBIT' else 'CREDIT' end;
  if node.code !~ '^[1-5][0-9]{5}$' or node.classification is distinct from expected_type
     or (node.depth=1 and (node.code !~ '^[1-5]00000$' or node.parent_id is not null))
     or (node.depth=2 and node.code !~ '^[1-5][1-9]0000$')
     or (node.depth=3 and node.code !~ '^[1-5][1-9][1-9]000$')
     or (node.depth=4 and (node.code !~ '^[1-5][1-9][1-9][0-9]{3}$' or right(node.code,3)='000'))
     or (node.depth<4 and node.normal_balance<>expected_normal) then
   raise exception 'Invalid COA code, classification or group balance' using errcode='23514';
  end if;
  exit when node.depth=1;
  select * into parent from gl_accounts where id=node.parent_id for share;
  if parent.id is null or parent.depth<>node.depth-1 or parent.classification<>node.classification
     or left(node.code,parent.depth)<>left(parent.code,parent.depth)
     or (new.active and not parent.active) then
   raise exception 'Invalid COA parent chain' using errcode='23514';
  end if;
  node:=parent;
 end loop;
 return new;
end; $$;
create trigger gl_account_code_guard before insert on gl_accounts
 for each row execute function fcms_gl_account_code_guard();

-- Direct-child pages, code ordering and active-child lifecycle checks share this access path.
create index gl_accounts_parent_code_id on gl_accounts(parent_id,code,id);
