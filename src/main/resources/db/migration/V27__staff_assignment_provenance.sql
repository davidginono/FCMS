-- Restore native on-behalf staff workflows with claim-based assignment and retained maker facts.
-- A report transition retains both its accepted 1000-character reason and prior review.
alter table audit_events alter column detail type varchar(2500);
insert into claims(name) values ('expense.assign'),('staff-report.assign');
insert into role_claims(role_id,claim)
 select role_id,c.name from roles cross join claims c
 where lower(role_name)='admin' and c.name in ('expense.assign','staff-report.assign');

alter table expenses add column created_by bigint references users(id),
 add column prepared_by bigint references users(id),
 add column creation_hash varchar(64) check(creation_hash is null or creation_hash ~ '^[0-9a-f]{64}$');
-- Preserve every accepted category through the retained transition history.
alter table expense_events alter column category type varchar(255);
-- Recover only actors attested by retained preparation audit events. Unknown history stays unknown.
with first_save as (
 select distinct on(resource_id) resource_id,actor_id from audit_events
 where resource='expenses' and action='expense.save' order by resource_id,id
) update expenses e set created_by=a.actor_id from first_save a where a.resource_id=e.id;
with last_save as (
 select distinct on(resource_id) resource_id,actor_id from audit_events
 where resource='expenses' and action='expense.save' order by resource_id,id desc
) update expenses e set prepared_by=a.actor_id from last_save a where a.resource_id=e.id;
drop index expenses_actor_request_uq;
create unique index expense_legacy_owner_request_uq on expenses(user_id,request_key) where created_by is null and request_key is not null;
-- The maker owns request identity; changing the assigned staff cannot change its namespace.
create unique index expense_maker_request_uq on expenses(created_by,request_key) where created_by is not null;

create function fcms_expense_assignment_guard() returns trigger language plpgsql as $$
declare before_state text; after_state text;
begin
 select name into after_state from expense_status where id=new.status_id;
 if tg_op='INSERT' then
  -- Explicit provenance must be complete. All-null retained/import fixtures remain visibly unknown.
  if (new.created_by is not null or new.prepared_by is not null or new.creation_hash is not null)
   and (new.created_by is null or new.prepared_by is null or new.creation_hash is null
    or new.created_by<>new.prepared_by) then
   raise exception 'Complete expense preparation evidence is required' using errcode='23514';
  end if;
  return new;
 end if;
 select name into before_state from expense_status where id=old.status_id;
 if row(new.created_by,new.creation_hash) is distinct from row(old.created_by,old.creation_hash) then
  raise exception 'Expense creation evidence is immutable' using errcode='23514';
 end if;
 if row(new.user_id,new.prepared_by) is distinct from row(old.user_id,old.prepared_by)
  and (before_state not in ('draft','rejected') or after_state<>'draft'
   or new.prepared_by is null or new.version<>old.version+1) then
  raise exception 'Expense preparation is immutable outside an editable draft' using errcode='23514';
 end if;
 if after_state='approved' and before_state<>after_state
  and (new.approved_by is null or new.approved_by=any(array[new.user_id,new.created_by,new.prepared_by])) then
  raise exception 'Independent preparation and approval are required' using errcode='23514';
 end if;
 if after_state='reversed' and before_state<>after_state and not exists(
  select 1 from expense_reversals r where r.expense_id=new.id
   and r.actor_id is distinct from new.user_id
   and r.actor_id is distinct from new.created_by and r.actor_id is distinct from new.prepared_by
 ) then raise exception 'Independent preparation and reversal are required' using errcode='23514'; end if;
 return new;
end; $$;
create trigger expense_assignment_guard before insert or update on expenses
 for each row execute function fcms_expense_assignment_guard();

alter table work_items add column prepared_by bigint references users(id),
 add column creation_hash varchar(64) check(creation_hash is null or creation_hash ~ '^[0-9a-f]{64}$');
with last_save as (
 select distinct on(resource_id) resource_id,actor_id from audit_events
 where resource='work_items' and action in ('task.save','leave.save','staff-report.save')
 order by resource_id,id desc
) update work_items w set prepared_by=a.actor_id from last_save a where a.resource_id=w.id;
-- For unchanged native creation rows the retained creator is the only preparer.
update work_items set prepared_by=created_by where prepared_by is null and version=0;
create function fcms_work_assignment_guard() returns trigger language plpgsql as $$
begin
 if tg_op='INSERT' then
  if new.creation_hash is not null and (new.prepared_by is null or new.prepared_by<>new.created_by) then
   raise exception 'Complete work preparation evidence is required' using errcode='23514';
  end if;
  return new;
 end if;
 if row(new.created_by,new.creation_hash) is distinct from row(old.created_by,old.creation_hash) then
  raise exception 'Work creation evidence is immutable' using errcode='23514';
 end if;
 if old.kind<>'TASK' and row(new.owner_id,new.prepared_by) is distinct from row(old.owner_id,old.prepared_by)
  and (old.status not in ('DRAFT','REJECTED') or new.status not in ('DRAFT','REJECTED')
   or new.prepared_by is null or new.version<>old.version+1) then
  raise exception 'Work preparation is immutable outside an editable draft' using errcode='23514';
 end if;
 if new.kind<>'TASK' and new.status in ('APPROVED','REJECTED') and new.status is distinct from old.status
  and (new.reviewed_by is null or new.reviewed_by=any(array[new.owner_id,new.created_by,new.prepared_by])) then
  raise exception 'Independent work preparation and review are required' using errcode='23514';
 end if;
 if new.kind='REPORT' and new.status in ('APPROVED','REJECTED') and new.status is distinct from old.status
  and exists(select 1 from work_participants p where p.work_id=new.id and p.user_id=new.reviewed_by) then
  raise exception 'Report participants cannot review their own group' using errcode='23514';
 end if;
 return new;
end; $$;
create trigger work_assignment_guard before insert or update on work_items
 for each row execute function fcms_work_assignment_guard();
