-- Preserve old paired evidence; new rows can carry explicit frozen split entries.
create sequence gl_activity_code_sequence;
create sequence gl_transaction_code_sequence;
select setval('gl_activity_code_sequence',greatest(coalesce((select max(substring(code from 5)::bigint) from gl_activities where code ~ '^ACT-[0-9]{1,18}$'),0)+1,1),false);
select setval('gl_transaction_code_sequence',greatest(coalesce((select max(substring(code from 5)::bigint) from gl_transactions where code ~ '^TXN-[0-9]{1,18}$'),0)+1,1),false);

alter table gl_lines drop constraint gl_lines_line_number_check;
alter table gl_lines add constraint gl_lines_line_number_check check(line_number between 1 and 500);
-- Keep the approved-books, period, cash-direction and independent reversal guards identical.
do $$ declare definition text; begin
 select pg_get_functiondef('fcms_gl_post_guard()'::regprocedure) into definition;
 if position('cnt not between 2 and 100' in definition)=0 then raise exception 'Unexpected journal posting guard';end if;
 definition:=replace(replace(definition,'cnt not between 2 and 100','cnt not between 2 and 500'),'2 to 100 lines','2 to 500 lines');
 execute definition;
end; $$;
alter table gl_voucher_rows alter column debit_account_id drop not null;
alter table gl_voucher_rows alter column credit_account_id drop not null;
alter table gl_voucher_rows add constraint voucher_pair_or_split check((debit_account_id is null)=(credit_account_id is null));
create table gl_voucher_entries (
 journal_id bigint not null,ordinal integer not null,entry_no integer not null check(entry_no between 1 and 50),
 line_number integer not null check(line_number between 1 and 500),
 side varchar(6) not null check(side in ('DEBIT','CREDIT')),account_id bigint not null references gl_accounts(id),
 amount numeric(14,2) not null check(amount>0),
 primary key(journal_id,ordinal,entry_no),unique(journal_id,line_number),unique(journal_id,ordinal,side,account_id),
 foreign key(journal_id,ordinal) references gl_voucher_rows(journal_id,ordinal)
);
create trigger voucher_entries_retained before update or delete on gl_voucher_entries for each row execute function fcms_gl_evidence_guard();
create function fcms_gl_voucher_entry_insert() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from gl_journals where id=new.journal_id and state='DRAFT') then raise exception 'Entries can only be captured with a draft' using errcode='23514';end if;
 return new;
end; $$;
create trigger voucher_entries_insert before insert on gl_voucher_entries for each row execute function fcms_gl_voucher_entry_insert();
create view gl_voucher_postings as
 select journal_id,ordinal,entry_no,line_number,side,account_id,amount from gl_voucher_entries
 union all
 select r.journal_id,r.ordinal,s.entry_no,2*r.ordinal-2+s.entry_no,s.side,
 case when s.entry_no=1 then r.debit_account_id else r.credit_account_id end,r.amount
 from gl_voucher_rows r cross join (values(1,'DEBIT'::varchar),(2,'CREDIT'::varchar)) s(entry_no,side)
 where r.debit_account_id is not null and not exists(select 1 from gl_voucher_entries e where e.journal_id=r.journal_id and e.ordinal=r.ordinal);

-- Each money account reserves the reference, including existing ordinary voucher evidence.
drop index gl_voucher_money_reference;
create table gl_voucher_money_references (
 journal_id bigint not null references gl_vouchers(journal_id),account_id bigint not null references gl_accounts(id),reference varchar(100) not null,
 primary key(journal_id,account_id)
);
create unique index gl_voucher_money_reference on gl_voucher_money_references(account_id,reference);
insert into gl_voucher_money_references select journal_id,money_account_id,reference from gl_vouchers where money_account_id is not null;
create trigger voucher_money_references_retained before update or delete on gl_voucher_money_references for each row execute function fcms_gl_evidence_guard();
create trigger voucher_money_references_insert before insert on gl_voucher_money_references for each row execute function fcms_gl_voucher_entry_insert();

create or replace function fcms_gl_voucher_assert() returns trigger language plpgsql as $$
declare v gl_vouchers;j gl_journals;journal_key bigint;cnt integer;
begin
 journal_key:=coalesce((to_jsonb(new)->>'journal_id')::bigint,(to_jsonb(new)->>'id')::bigint);
 select * into v from gl_vouchers where journal_id=journal_key;
 if v.journal_id is null then return new;end if;
 select * into j from gl_journals where id=journal_key;
 select count(*) into cnt from gl_voucher_postings where journal_id=journal_key;
 if v.reference<>j.reference or not exists(select 1 from gl_voucher_number_reservations n where n.number=v.number and n.journal_id=j.id and n.actor_id=j.maker_id and n.request_key=j.request_key and n.effective_date=j.effective_date and n.kind=j.kind)
 or v.row_count<>(select count(*) from gl_voucher_rows where journal_id=journal_key) or v.total<>(select sum(amount) from gl_voucher_rows where journal_id=journal_key)
 or cnt not between 2 and 500 or cnt<>(select count(*) from gl_lines where journal_id=journal_key)
 or exists(select 1 from gl_voucher_rows r left join gl_voucher_postings e on e.journal_id=r.journal_id and e.ordinal=r.ordinal where r.journal_id=journal_key group by r.ordinal,r.amount
 having count(e.entry_no) not between 2 and 50 or count(distinct e.entry_no)<>max(e.entry_no) or coalesce(sum(e.amount) filter(where e.side='DEBIT'),0)<>r.amount or coalesce(sum(e.amount) filter(where e.side='CREDIT'),0)<>r.amount)
 or exists(select 1 from gl_voucher_postings d join gl_voucher_postings c using(journal_id,ordinal,account_id) where d.journal_id=journal_key and d.side='DEBIT' and c.side='CREDIT')
 or exists((select line_number,account_id,case when side='DEBIT' then amount else 0 end,case when side='CREDIT' then amount else 0 end from gl_voucher_postings where journal_id=journal_key)
 except all(select line_number,account_id,debit,credit from gl_lines where journal_id=journal_key))
 or exists((select line_number,account_id,debit,credit from gl_lines where journal_id=journal_key)
 except all(select line_number,account_id,case when side='DEBIT' then amount else 0 end,case when side='CREDIT' then amount else 0 end from gl_voucher_postings where journal_id=journal_key))
 or exists(select 1 from gl_voucher_rows r where r.journal_id=journal_key and (
 (r.debit_account_id is null and (r.mapping_id is not null or r.component<>'TOTAL')) or
 (r.debit_account_id is not null and ((select count(*) from gl_voucher_postings e where e.journal_id=r.journal_id and e.ordinal=r.ordinal)<>2 or
 not exists(select 1 from gl_voucher_postings e where e.journal_id=r.journal_id and e.ordinal=r.ordinal and e.side='DEBIT' and e.account_id=r.debit_account_id and e.amount=r.amount) or
 not exists(select 1 from gl_voucher_postings e where e.journal_id=r.journal_id and e.ordinal=r.ordinal and e.side='CREDIT' and e.account_id=r.credit_account_id and e.amount=r.amount))) or
 (r.mapping_id is not null and not exists(select 1 from gl_posting_mappings m join gl_mapping_rules rule on rule.mapping_id=m.id join gl_transactions t on t.id=m.transaction_id
 where m.id=r.mapping_id and m.voucher_kind=j.kind and t.activity_id=v.activity_id and rule.component=r.component and rule.debit_account_id=r.debit_account_id and rule.credit_account_id=r.credit_account_id))))
 then raise exception 'Voucher envelope must match its complete balanced journal' using errcode='23514';end if;
 if j.kind in ('RECEIPT','PAYMENT') then
  if not exists(select 1 from gl_voucher_postings where journal_id=journal_key and account_id=v.money_account_id and side=case j.kind when 'RECEIPT' then 'DEBIT' else 'CREDIT' end)
  or exists(select 1 from gl_voucher_postings e join gl_accounts a on a.id=e.account_id where e.journal_id=journal_key and a.cash_account<>(e.side=case j.kind when 'RECEIPT' then 'DEBIT' else 'CREDIT' end))
  or exists((select distinct e.account_id,v.reference from gl_voucher_postings e where e.journal_id=journal_key and e.side=case j.kind when 'RECEIPT' then 'DEBIT' else 'CREDIT' end) except(select account_id,reference from gl_voucher_money_references where journal_id=journal_key))
  or exists((select account_id,reference from gl_voucher_money_references where journal_id=journal_key) except(select distinct e.account_id,v.reference from gl_voucher_postings e where e.journal_id=journal_key and e.side=case j.kind when 'RECEIPT' then 'DEBIT' else 'CREDIT' end))
  then raise exception 'Voucher money accounts and references must match' using errcode='23514';end if;
 elsif v.money_account_id is not null or exists(select 1 from gl_voucher_money_references where journal_id=journal_key) then raise exception 'Journal has no money reference reservation' using errcode='23514';end if;
 return new;
end; $$;
create constraint trigger voucher_entries_complete after insert on gl_voucher_entries deferrable initially deferred for each row execute function fcms_gl_voucher_assert();
create constraint trigger voucher_money_references_complete after insert on gl_voucher_money_references deferrable initially deferred for each row execute function fcms_gl_voucher_assert();
