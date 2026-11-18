-- Role bundles are defaults. Existing per-user claim assignments remain explicit overrides.
insert into claims(name) values ('workspace.accounting'),('workspace.director');
update roles set role_name='Administrator' where lower(role_name)='admin';
update roles set role_name='Staff' where lower(role_name) in ('user','users');
insert into roles(role_name) select 'Accounts Clerk' where not exists(select 1 from roles where lower(role_name)='accounts clerk');
insert into roles(role_name) select 'Director' where not exists(select 1 from roles where lower(role_name)='director');

delete from role_claims where role_id in
 (select role_id from roles where role_name in ('Administrator','Staff','Accounts Clerk','Director'));
insert into role_claims(role_id,claim)
 select r.role_id,c.name from roles r cross join claims c
 where r.role_name='Administrator' and c.name in
 ('workspace.admin','iam.user.read','iam.user.manage','account.write','backup.manage');
insert into role_claims(role_id,claim)
 select r.role_id,c.name from roles r cross join claims c
 where r.role_name='Staff' and c.name in
 ('workspace.staff','dashboard.read','account.write','client.lookup','leave.read','leave.write','staff-report.read','staff-report.write');
insert into role_claims(role_id,claim)
 select r.role_id,c.name from roles r cross join claims c
 where r.role_name='Accounts Clerk' and
 (c.name in ('workspace.accounting','dashboard.read','account.write','client.lookup','finance.read','finance.write','finance.reverse','finance.gl-source.read','report.read')
  or c.name like 'accounting.%' or c.name like 'client.%' or c.name like 'sales.%');
insert into role_claims(role_id,claim)
 select r.role_id,c.name from roles r cross join claims c
 where r.role_name='Director' and
 (c.name in ('workspace.director','dashboard.read','account.write','client.lookup','report.read','accounting.read','accounting.report',
             'task.read','task.read.all','task.write','task.manage','leave.read','leave.read.all','leave.write','leave.review',
             'staff-report.read','staff-report.read.all','staff-report.write','staff-report.assign','staff-report.review')
  or c.name like 'client.%' or c.name like 'sales.%');
