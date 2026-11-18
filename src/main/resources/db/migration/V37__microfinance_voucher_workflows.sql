-- Microfinance component templates and multi-transaction voucher envelopes.
-- Historical journals, mappings and evidence remain unchanged.
alter table gl_transactions drop constraint gl_transactions_source_event_check;
alter table gl_transactions add constraint gl_transactions_source_event_check check(source_event in ('MANUAL_JOURNAL','INCOME_IN','PETTY_IN','PETTY_OUT','CLOUD_IN','CLOUD_OUT','BULK_IN','BULK_OUT','EXPENSE_PAYMENT','INCOME_OPENING_REVERSAL','PETTY_OPENING_REVERSAL','CLOUD_OPENING_REVERSAL','BULK_OPENING_REVERSAL','EXPENSE_OPENING_REVERSAL'));
alter table gl_posting_mappings drop constraint gl_posting_mappings_source_event_check;
alter table gl_posting_mappings add constraint gl_posting_mappings_source_event_check check(source_event in ('MANUAL_JOURNAL','INCOME_IN','PETTY_IN','PETTY_OUT','CLOUD_IN','CLOUD_OUT','BULK_IN','BULK_OUT','EXPENSE_PAYMENT','INCOME_OPENING_REVERSAL','PETTY_OPENING_REVERSAL','CLOUD_OPENING_REVERSAL','BULK_OPENING_REVERSAL','EXPENSE_OPENING_REVERSAL'));
create table gl_mapping_rules (
 mapping_id bigint not null references gl_posting_mappings(id),
 component varchar(12) not null check(component in ('TOTAL','PRINCIPAL','INTEREST','FEES','TAX')),
 debit_account_id bigint not null references gl_accounts(id), credit_account_id bigint not null references gl_accounts(id),
 primary key(mapping_id,component), check(debit_account_id<>credit_account_id)
);
insert into gl_mapping_rules select id,'TOTAL',debit_account_id,credit_account_id from gl_posting_mappings;
create trigger mapping_rules_retained before update or delete on gl_mapping_rules for each row execute function fcms_gl_evidence_guard();
create function fcms_gl_mapping_rules_guard() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from gl_posting_mappings where id=new.mapping_id and state='PENDING') then raise exception 'Only pending template rules can be created' using errcode='23514'; end if;
 return new;
end; $$;
create trigger mapping_rules_insert before insert on gl_mapping_rules for each row execute function fcms_gl_mapping_rules_guard();
alter table gl_lines drop constraint gl_lines_line_number_check;
alter table gl_lines add constraint gl_lines_line_number_check check(line_number between 1 and 100);
create or replace function fcms_gl_post_guard() returns trigger language plpgsql as $$
declare p gl_periods; b gl_books; cnt integer; difference numeric; cash_delta numeric; has_cash boolean; o gl_journals;
begin
 if new.state<>'POSTED' then return new; end if;
 select * into p from gl_periods where id=new.period_id for share;
 select * into b from gl_books where id=true;
 if p.state<>'OPEN' or new.effective_date not between p.starts_on and p.ends_on or b.state is distinct from 'APPROVED' or new.effective_date<b.opening_date
 then raise exception 'Approved books and open period are required' using errcode='23514'; end if;
 if new.kind='OPENING' then
  if b.opening_mode<>'JOURNAL' or new.effective_date<>b.opening_date or exists(select 1 from gl_lines l join gl_accounts a on a.id=l.account_id where l.journal_id=new.id and a.classification in ('INCOME','EXPENSE'))
  then raise exception 'Invalid opening balance journal' using errcode='23514'; end if;
 elsif b.opening_mode='JOURNAL' and not exists(select 1 from gl_journals where kind='OPENING' and state='POSTED')
 then raise exception 'Reviewed opening journal is required' using errcode='23514'; end if;
 perform a.id from gl_accounts a where a.id in (select account_id from gl_lines where journal_id=new.id) order by a.id for share;
 if exists(select 1 from gl_lines l join gl_accounts a on a.id=l.account_id where l.journal_id=new.id and (not a.active or a.depth<>4))
 then raise exception 'Active posting accounts are required' using errcode='23514'; end if;
 select count(*),sum(l.debit-l.credit),coalesce(sum(case when a.cash_account then l.debit-l.credit else 0 end),0),bool_or(a.cash_account)
 into cnt,difference,cash_delta,has_cash from gl_lines l join gl_accounts a on a.id=l.account_id where l.journal_id=new.id;
 if cnt not between 2 and 100 or difference<>0 then raise exception 'Balanced journal with 2 to 100 lines is required' using errcode='23514'; end if;
 if new.kind='RECEIPT' and cash_delta<=0 or new.kind='PAYMENT' and cash_delta>=0
 then raise exception 'Receipt/payment cash direction is invalid' using errcode='23514'; end if;
 if new.kind<>'OPENING' and ((cash_delta<>0 and new.cash_flow not in ('OPERATING','INVESTING','FINANCING')) or (cash_delta=0 and new.cash_flow<>case when has_cash then 'INTERNAL' else 'NONE' end))
 then raise exception 'Explicit cash-flow classification is required' using errcode='23514'; end if;
 if new.reverses_id is not null then
  select * into o from gl_journals where id=new.reverses_id for update;
  if o.state<>'POSTED' or o.kind in ('OPENING','REVERSAL') or new.maker_id=o.maker_id or new.checker_id=o.maker_id or new.effective_date<o.effective_date
  then raise exception 'Independent linked reversal is required' using errcode='23514'; end if;
  if exists((select account_id,debit,credit from gl_lines where journal_id=new.id) except all(select account_id,credit,debit from gl_lines where journal_id=o.id))
   or exists((select account_id,credit,debit from gl_lines where journal_id=o.id) except all(select account_id,debit,credit from gl_lines where journal_id=new.id))
  then raise exception 'Reversal must exactly swap original lines' using errcode='23514'; end if;
 end if;
 return new;
end; $$;

create sequence gl_voucher_number_sequence;
create table gl_voucher_number_reservations (
 actor_id bigint not null references users(id), request_key uuid not null,
 kind varchar(10) not null check(kind in ('RECEIPT','PAYMENT','JOURNAL')),effective_date date not null,
 number varchar(48) not null unique,journal_id bigint unique references gl_journals(id),
 created_at timestamptz not null default current_timestamp,primary key(actor_id,request_key)
);
create function fcms_gl_reservation_guard() returns trigger language plpgsql as $$
begin
 if TG_OP='DELETE' or old.journal_id is not null or row(new.actor_id,new.request_key,new.kind,new.created_at) is distinct from row(old.actor_id,old.request_key,old.kind,old.created_at)
 then raise exception 'Consumed voucher numbers are retained' using errcode='23514';end if;
 return new;
end; $$;
create trigger voucher_numbers_retained before update or delete on gl_voucher_number_reservations for each row execute function fcms_gl_reservation_guard();
create table gl_vouchers (
 journal_id bigint primary key references gl_journals(id),
 number varchar(48) not null unique references gl_voucher_number_reservations(number),
 client_key varchar(60) not null default '', client_name varchar(255) not null default '',
 reference varchar(100) not null check(length(btrim(reference))>0),
 party varchar(160) not null default '', activity_id bigint references gl_activities(id),
 money_account_id bigint references gl_accounts(id),
 payload_hash varchar(64) not null check(payload_hash ~ '^[0-9a-f]{64}$'),
 row_count integer not null check(row_count between 1 and 50), total numeric(14,2) not null check(total>0),
 created_at timestamptz not null default current_timestamp
);
create unique index gl_voucher_money_reference on gl_vouchers(money_account_id,reference) where money_account_id is not null;
create table gl_voucher_rows (
 journal_id bigint not null references gl_vouchers(journal_id), ordinal integer not null check(ordinal between 1 and 50),
 mapping_id bigint references gl_posting_mappings(id),component varchar(12) not null check(component in ('TOTAL','PRINCIPAL','INTEREST','FEES','TAX')),
 transaction_name varchar(160) not null default '', description varchar(500) not null check(length(btrim(description))>0),
 amount numeric(14,2) not null check(amount>0),
 debit_account_id bigint not null references gl_accounts(id), credit_account_id bigint not null references gl_accounts(id),
 primary key(journal_id,ordinal),check(debit_account_id<>credit_account_id)
);
create trigger vouchers_retained before update or delete on gl_vouchers for each row execute function fcms_gl_evidence_guard();
create trigger voucher_rows_retained before update or delete on gl_voucher_rows for each row execute function fcms_gl_evidence_guard();
create function fcms_gl_voucher_assert() returns trigger language plpgsql as $$
declare v gl_vouchers;j gl_journals;journal_key bigint;
begin
 journal_key:=coalesce((to_jsonb(new)->>'journal_id')::bigint,(to_jsonb(new)->>'id')::bigint);
 select * into v from gl_vouchers where journal_id=journal_key;
 if v.journal_id is null then return new;end if;
 select * into j from gl_journals where id=journal_key;
 if v.reference<>j.reference or not exists(select 1 from gl_voucher_number_reservations n where n.number=v.number and n.journal_id=j.id and n.actor_id=j.maker_id and n.request_key=j.request_key and n.effective_date=j.effective_date and n.kind=j.kind) or v.row_count<>(select count(*) from gl_voucher_rows where journal_id=v.journal_id) or v.total<>(select sum(amount) from gl_voucher_rows where journal_id=v.journal_id)
 or (select count(*) from gl_lines where journal_id=v.journal_id)<>2*v.row_count
 or exists(select 1 from gl_voucher_rows r where r.journal_id=v.journal_id and
 (not exists(select 1 from gl_lines l where l.journal_id=r.journal_id and l.line_number=2*r.ordinal-1 and l.account_id=r.debit_account_id and l.debit=r.amount and l.credit=0)
 or not exists(select 1 from gl_lines l where l.journal_id=r.journal_id and l.line_number=2*r.ordinal and l.account_id=r.credit_account_id and l.credit=r.amount and l.debit=0)
 or (r.mapping_id is not null and not exists(select 1 from gl_posting_mappings m join gl_mapping_rules rule on rule.mapping_id=m.id join gl_transactions t on t.id=m.transaction_id
 where m.id=r.mapping_id and m.voucher_kind=j.kind and t.activity_id=v.activity_id and rule.component=r.component and rule.debit_account_id=r.debit_account_id and rule.credit_account_id=r.credit_account_id))))
 or (j.kind='RECEIPT' and exists(select 1 from gl_voucher_rows where journal_id=v.journal_id and debit_account_id is distinct from v.money_account_id))
 or (j.kind='PAYMENT' and exists(select 1 from gl_voucher_rows where journal_id=v.journal_id and credit_account_id is distinct from v.money_account_id))
 then raise exception 'Voucher envelope must match its complete journal' using errcode='23514';end if;
 return new;
end; $$;
create constraint trigger vouchers_complete after insert on gl_vouchers deferrable initially deferred for each row execute function fcms_gl_voucher_assert();
create constraint trigger voucher_rows_complete after insert on gl_voucher_rows deferrable initially deferred for each row execute function fcms_gl_voucher_assert();

-- Recheck envelope/ledger agreement on later submission and posting, too.
create constraint trigger voucher_journal_complete after update on gl_journals deferrable initially deferred for each row execute function fcms_gl_voucher_assert();
create function fcms_gl_rules_assert() returns trigger language plpgsql as $$
declare mapping_key bigint;m gl_posting_mappings;rule_count integer;
begin
 mapping_key:=case when TG_TABLE_NAME='gl_mapping_rules' then (to_jsonb(new)->>'mapping_id')::bigint else (to_jsonb(new)->>'id')::bigint end;
 select * into m from gl_posting_mappings where id=mapping_key;
 select count(*) into rule_count from gl_mapping_rules where mapping_id=mapping_key;
 if rule_count not between 1 and 5 or
 (rule_count>1 and exists(select 1 from gl_mapping_rules where mapping_id=mapping_key and component='TOTAL')) or
 (m.source_event<>'MANUAL_JOURNAL' and (rule_count<>1 or not exists(select 1 from gl_mapping_rules where mapping_id=mapping_key and component='TOTAL'))) or
 not exists(select 1 from gl_mapping_rules where mapping_id=mapping_key and debit_account_id=m.debit_account_id and credit_account_id=m.credit_account_id)
 then raise exception 'Invalid component template' using errcode='23514';end if;
 if m.state in ('PENDING','APPROVED') and exists(select 1 from gl_mapping_rules r join gl_accounts d on d.id=r.debit_account_id join gl_accounts c on c.id=r.credit_account_id
 where r.mapping_id=mapping_key and (not d.active or not c.active or d.depth<>4 or c.depth<>4 or
 (m.voucher_kind='RECEIPT' and (not d.cash_account or c.cash_account)) or
 (m.voucher_kind='PAYMENT' and (d.cash_account or not c.cash_account)) or
 (d.cash_account<>c.cash_account and m.cash_flow not in ('OPERATING','INVESTING','FINANCING')) or
 (d.cash_account=c.cash_account and m.cash_flow<>case when d.cash_account then 'INTERNAL' else 'NONE' end)))
 then raise exception 'Invalid component accounts or cash flow' using errcode='23514';end if;
 return new;
end; $$;
create constraint trigger mapping_components_complete after insert or update on gl_posting_mappings deferrable initially deferred for each row execute function fcms_gl_rules_assert();
create constraint trigger mapping_rules_complete after insert on gl_mapping_rules deferrable initially deferred for each row execute function fcms_gl_rules_assert();
