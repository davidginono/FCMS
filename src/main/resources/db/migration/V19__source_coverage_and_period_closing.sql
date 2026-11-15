-- Finance owns the closed-date contract; accounting consumes the public read view.
create table finance_gl_period_guards (
 period_id bigint primary key, starts_on date not null, ends_on date not null check(ends_on>=starts_on),
 closed boolean not null, changed_by bigint not null references users(id), reason varchar(500) not null check(length(trim(reason))>0),
 changed_at timestamptz not null default clock_timestamp()
);
insert into finance_gl_period_guards(period_id,starts_on,ends_on,closed,changed_by,reason)
 select id,starts_on,ends_on,true,closed_by,close_reason from gl_periods where state='CLOSED';
create view finance_gl_closed_dates as select period_id,starts_on,ends_on,closed from finance_gl_period_guards;
create function fcms_finance_require_open_date(effective date) returns void language plpgsql as $$
begin
 perform pg_advisory_xact_lock_shared(hashtextextended('FCMS_FINANCE_PERIOD_CLOSE',0));
 if exists(select 1 from finance_gl_period_guards where closed and effective between starts_on and ends_on)
 then raise exception 'Operational source date is in a closed accounting period' using errcode='23514'; end if;
end; $$;
create function fcms_finance_source_date_guard() returns trigger language plpgsql as $$
begin
 if TG_TABLE_NAME='ledger_entries' then perform fcms_finance_require_open_date(new.transaction_date);
 elsif TG_TABLE_NAME='expense_reversals' then perform fcms_finance_require_open_date((new.created_at at time zone 'Africa/Dar_es_Salaam')::date);
 elsif new.paid_on is distinct from old.paid_on or new.paid_at is distinct from old.paid_at then perform fcms_finance_require_open_date(new.paid_on);
 end if;
 return new;
end; $$;
create trigger finance_source_date_guard before insert on ledger_entries for each row execute function fcms_finance_source_date_guard();
create trigger finance_source_date_guard before insert on expense_reversals for each row execute function fcms_finance_source_date_guard();
create trigger finance_source_date_guard before update on expenses for each row execute function fcms_finance_source_date_guard();

create view gl_source_coverage as
 select s.source_kind,s.source_id,s.source_date,abs(s.signed_amount) amount,
 case when b.state is distinct from 'APPROVED' or (b.opening_mode='JOURNAL' and not exists(select 1 from gl_journals where kind='OPENING' and state='POSTED')) then 'BOOKS_PENDING'
 when s.source_date is null or (s.reverses_id is not null and o.source_date is null)
  or (s.source_date<b.opening_date and (s.recorded_at is null or s.recorded_at>b.approved_at))
  or (o.source_date<b.opening_date and (o.recorded_at is null or o.recorded_at>b.approved_at)) then 'UNVERIFIED'
 when s.source_date<b.opening_date then 'OPENING_COVERED'
 when j.state='POSTED' then 'POSTED' else 'UNPOSTED' end coverage
 from finance_gl_sources s left join finance_gl_sources o on o.source_kind=s.reverses_kind and o.source_id=s.reverses_id
 left join gl_books b on true
 left join gl_source_reservations r on r.source_kind=s.source_kind and r.source_id=s.source_id
 left join gl_journals j on j.id=r.journal_id;

create function fcms_gl_period_coverage_guard() returns trigger language plpgsql as $$
begin
 if new.state='CLOSED' then
  perform pg_advisory_xact_lock(hashtextextended('FCMS_FINANCE_PERIOD_CLOSE',0));
  if not exists(select 1 from gl_books where state='APPROVED' and (opening_mode='ZERO' or exists(select 1 from gl_journals where kind='OPENING' and state='POSTED')))
   or exists(select 1 from gl_source_coverage where (source_date is null or source_date<=new.ends_on) and coverage not in ('POSTED','OPENING_COVERED'))
  then raise exception 'Unposted or unverified operational sources prevent closing' using errcode='23514'; end if;
 end if;
 if new.state is distinct from old.state and not exists(select 1 from finance_gl_closed_dates where period_id=new.id and starts_on=new.starts_on and ends_on=new.ends_on and closed=(new.state='CLOSED'))
 then raise exception 'Finance date guard must match the period state' using errcode='23514'; end if;
 return new;
end; $$;
create trigger gl_period_coverage_guard before update on gl_periods for each row execute function fcms_gl_period_coverage_guard();
