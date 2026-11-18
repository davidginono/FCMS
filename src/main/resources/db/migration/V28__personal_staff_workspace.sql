-- Workspace access does not grant the business capabilities inside it.
insert into claims(name) values ('workspace.admin'),('workspace.staff'),('client.lookup');
insert into role_claims(role_id,claim)
 select role_id,'workspace.admin' from roles where lower(role_name)='admin';
insert into role_claims(role_id,claim)
 select role_id,'workspace.staff' from roles where lower(role_name) in ('user','users');
insert into role_claims(role_id,claim)
 select distinct role_id,'client.lookup' from role_claims where claim='client.read';
delete from role_claims where claim='client.read'
 and role_id in (select role_id from roles where lower(role_name) in ('user','users'));
