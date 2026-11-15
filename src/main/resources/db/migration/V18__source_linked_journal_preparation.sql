-- Finance owns the cutover guard. This one-time upgrade copies only the already-reviewed
-- book cutover contract; runtime accounting calls the public Finance application service.
create table finance_gl_cutover(id boolean primary key default true check(id),starts_on date not null,
 policy_reference varchar(200) not null,approved_by bigint not null references users(id),approved_at timestamptz not null default clock_timestamp());
insert into finance_gl_cutover(id,starts_on,policy_reference,approved_by,approved_at)
 select true,opening_date,policy_reference,checker_id,approved_at from gl_books where state='APPROVED';
create trigger finance_cutover_immutable before update or delete on finance_gl_cutover for each row execute function fcms_gl_evidence_guard();
create function fcms_finance_cutover_guard() returns trigger language plpgsql as $$
begin
 perform pg_advisory_xact_lock_shared(hashtextextended('FCMS_FINANCE_CUTOVER',0));
 if new.reversal_of is null and exists(select 1 from finance_gl_cutover where new.transaction_date<starts_on)
 then raise exception 'New operational entries cannot predate the reviewed accounting cutover' using errcode='23514'; end if;
 return new;
end; $$;
create trigger finance_cutover_guard before insert on ledger_entries for each row execute function fcms_finance_cutover_guard();
create or replace view finance_gl_sources as
 select 'LEDGER_'||e.kind source_kind,e.id source_id,e.kind||case when e.amount_delta>0 then '_IN' else '_OUT' end source_event,
 e.transaction_date source_date,e.reference,e.description,e.amount_delta signed_amount,e.actor_id,
 case when exists(select 1 from ledger_entries r where r.reversal_of=e.id) then 'REVERSED' else 'ACTIVE' end source_state,
 case when e.reversal_of is null then null else 'LEDGER_'||e.kind end reverses_kind,e.reversal_of reverses_id,e.created_at recorded_at
 from ledger_entries e where e.amount_delta<>0
 union all
 select 'EXPENSE',e.id,'EXPENSE_PAYMENT',e.paid_on,e.payment_reference,e.description,e.amount,coalesce(e.paid_by,e.user_id),
 case when e.paid_on is null then 'UNVERIFIED' when s.name='reversed' then 'REVERSED' else 'ACTIVE' end,null::text,null::bigint,e.paid_at
 from expenses e join expense_status s on s.id=e.status_id where s.name in ('paid','reversed')
 union all
 select 'EXPENSE_REVERSAL',r.id,'EXPENSE_REVERSAL',(r.created_at at time zone 'Africa/Dar_es_Salaam')::date,
 'EXP-REV-'||e.id,r.reason,-e.amount,r.actor_id,'ACTIVE','EXPENSE',e.id,r.created_at
 from expense_reversals r join expenses e on e.id=r.expense_id;

-- Keep every preparation attempt; one reservation identifies the current attempt for a source.
alter table gl_source_links drop constraint gl_source_links_pkey;
alter table gl_source_links drop constraint gl_source_links_journal_id_key;
alter table gl_source_links add primary key(journal_id);
alter table gl_source_links add column mapping_id bigint references gl_posting_mappings(id),
 add column source_date date,add column signed_amount numeric;
alter table gl_source_links disable trigger gl_source_evidence;
update gl_source_links l set source_date=s.source_date,signed_amount=s.signed_amount
 from finance_gl_sources s where s.source_kind=l.source_kind and s.source_id=l.source_id;
alter table gl_source_links enable trigger gl_source_evidence;
alter table gl_source_links alter column source_date set not null,alter column signed_amount set not null;
alter table gl_source_links add check(signed_amount<>0 and scale(signed_amount)<=2),
 add unique(source_kind,source_id,journal_id);
create table gl_source_reservations (
 source_kind varchar(30) not null,source_id bigint not null,journal_id bigint not null unique,
 primary key(source_kind,source_id),
 foreign key(source_kind,source_id,journal_id) references gl_source_links(source_kind,source_id,journal_id)
);
insert into gl_source_reservations select source_kind,source_id,journal_id from gl_source_links;

-- Opening-covered originals have no transaction-level GL journal to reverse. Their later
-- corrections require separate, explicit accounting treatment rather than inferred revenue.
alter table gl_posting_mappings drop constraint gl_posting_mappings_source_event_check;
alter table gl_posting_mappings add check(source_event in
 ('INCOME_IN','PETTY_IN','PETTY_OUT','CLOUD_IN','CLOUD_OUT','BULK_IN','BULK_OUT','EXPENSE_PAYMENT',
 'INCOME_OPENING_REVERSAL','PETTY_OPENING_REVERSAL','CLOUD_OPENING_REVERSAL','BULK_OPENING_REVERSAL','EXPENSE_OPENING_REVERSAL'));

create function fcms_gl_source_reservation_guard() returns trigger language plpgsql as $$
begin
 if TG_OP='DELETE' then raise exception 'Source reservations are retained' using errcode='23514'; end if;
 if TG_OP='UPDATE' then
  if row(new.source_kind,new.source_id) is distinct from row(old.source_kind,old.source_id) or
   not exists(select 1 from gl_journals where id=old.journal_id and state='CANCELLED')
  then raise exception 'Only a cancelled preparation may be replaced' using errcode='23514'; end if;
 end if;
 if not exists(select 1 from gl_journals where id=new.journal_id and state='DRAFT')
 then raise exception 'A source reservation must begin with a draft' using errcode='23514'; end if;
 return new;
end; $$;
create trigger gl_source_reservation_guard before insert or update or delete on gl_source_reservations for each row execute function fcms_gl_source_reservation_guard();

create function fcms_gl_source_assert(journal bigint,posting boolean,checker bigint) returns void language plpgsql as $$
declare l gl_source_links; s finance_gl_sources; o finance_gl_sources; j gl_journals;
 b gl_books; m gl_posting_mappings; original_journal bigint; event_name text; line_count integer;
begin
 select * into l from gl_source_links where journal_id=journal;
 if not found then return; end if;
 select * into j from gl_journals where id=journal;
 select * into s from finance_gl_sources where source_kind=l.source_kind and source_id=l.source_id;
 select * into b from gl_books where id=true;
 if s.source_id is null or s.source_date is null or s.source_state not in ('ACTIVE','REVERSED') or
  row(s.source_date,s.signed_amount) is distinct from row(l.source_date,l.signed_amount) or
  j.effective_date<>s.source_date or b.state is distinct from 'APPROVED' or s.source_date<b.opening_date or
  l.linked_by<>j.maker_id or l.evidence_reference<>j.evidence_reference
 then raise exception 'Verified source evidence does not match the voucher' using errcode='23514'; end if;
 if posting and (checker=s.actor_id or not exists(select 1 from gl_source_reservations r where r.source_kind=l.source_kind and r.source_id=l.source_id and r.journal_id=j.id))
 then raise exception 'Current preparation and independent source review are required' using errcode='23514'; end if;
 if (select coalesce(sum(debit),0) from gl_lines where journal_id=j.id)<>abs(s.signed_amount)
 then raise exception 'Voucher amount must equal the immutable source amount' using errcode='23514'; end if;
 event_name:=s.source_event;
 if s.reverses_id is not null then
  select * into o from finance_gl_sources where source_kind=s.reverses_kind and source_id=s.reverses_id;
  if o.source_date is null or o.signed_amount<>-s.signed_amount or o.source_state<>'REVERSED' or s.source_date<o.source_date or (posting and checker=o.actor_id)
  then raise exception 'Actual independent reversal evidence is required' using errcode='23514'; end if;
  if o.source_date<b.opening_date and (o.recorded_at is null or o.recorded_at>b.approved_at)
  then raise exception 'Opening-covered source evidence requires verification' using errcode='23514'; end if;
  if o.source_date>=b.opening_date then
   select r.journal_id into original_journal from gl_source_reservations r join gl_journals v on v.id=r.journal_id
    where r.source_kind=o.source_kind and r.source_id=o.source_id and v.state='POSTED';
   if original_journal is null or j.kind<>'REVERSAL' or j.reverses_id is distinct from original_journal
   then raise exception 'Post the original source before preparing its exact linked reversal' using errcode='23514'; end if;
   if l.mapping_id is distinct from (select mapping_id from gl_source_links where journal_id=original_journal)
   then raise exception 'Reversal retains the original mapping revision' using errcode='23514'; end if;
   return;
  end if;
  event_name:=(case when o.source_kind='EXPENSE' then 'EXPENSE' else substring(o.source_kind from 8) end)||'_OPENING_REVERSAL';
 end if;
 select * into m from gl_posting_mappings where id=l.mapping_id for share;
 if m.id is null or m.source_event<>event_name or m.state not in ('APPROVED','RETIRED') or
  (not posting and m.state<>'APPROVED') or j.kind<>m.voucher_kind or j.cash_flow<>m.cash_flow or j.reverses_id is not null
 then raise exception 'An explicitly approved matching revision is required' using errcode='23514'; end if;
 select count(*) into line_count from gl_lines where journal_id=j.id;
 if line_count<>2 or not exists(select 1 from gl_lines where journal_id=j.id and account_id=m.debit_account_id and debit=abs(s.signed_amount) and credit=0) or
  not exists(select 1 from gl_lines where journal_id=j.id and account_id=m.credit_account_id and credit=abs(s.signed_amount) and debit=0)
 then raise exception 'Prepared lines must match the reviewed account pair and source amount' using errcode='23514'; end if;
end; $$;
create function fcms_gl_source_link_guard() returns trigger language plpgsql as $$
begin
 if not exists(select 1 from gl_journals where id=new.journal_id and state='DRAFT')
 then raise exception 'Only new drafts can be bound to sources' using errcode='23514'; end if;
 return new;
end; $$;
create trigger gl_source_link_guard before insert on gl_source_links for each row execute function fcms_gl_source_link_guard();
create function fcms_gl_source_link_assert() returns trigger language plpgsql as $$
begin perform fcms_gl_source_assert(new.journal_id,false,null);return new;end; $$;
create trigger gl_source_link_assert after insert on gl_source_links for each row execute function fcms_gl_source_link_assert();

create function fcms_gl_source_journal_guard() returns trigger language plpgsql as $$
begin
 if new.state='POSTED' and new.reverses_id is not null and exists(select 1 from gl_source_links where journal_id=new.reverses_id) and not exists(select 1 from gl_source_links where journal_id=new.id)
 then raise exception 'Source-bound originals require actual linked source reversals' using errcode='23514'; end if;
 if exists(select 1 from gl_source_links where journal_id=old.id) then
  if row(new.reference,new.description,new.evidence_reference,new.effective_date,new.period_id,new.cash_flow,new.payload_hash)
   is distinct from row(old.reference,old.description,old.evidence_reference,old.effective_date,old.period_id,old.cash_flow,old.payload_hash)
  then raise exception 'Source-bound voucher payload is immutable; cancel and prepare a new attempt' using errcode='23514'; end if;
  if new.state='POSTED' then perform fcms_gl_source_assert(new.id,true,new.checker_id); end if;
 end if;
 return new;
end; $$;
create trigger gl_source_journal_guard before update on gl_journals for each row execute function fcms_gl_source_journal_guard();
create function fcms_gl_source_line_guard() returns trigger language plpgsql as $$
declare journal bigint;
begin
 journal:=case when TG_OP='DELETE' then old.journal_id else new.journal_id end;
 if exists(select 1 from gl_source_links where journal_id=journal)
 then raise exception 'Source-bound voucher lines are immutable' using errcode='23514'; end if;
 if TG_OP='DELETE' then return old; end if;
 return new;
end; $$;
create trigger gl_source_line_guard before insert or update or delete on gl_lines for each row execute function fcms_gl_source_line_guard();
