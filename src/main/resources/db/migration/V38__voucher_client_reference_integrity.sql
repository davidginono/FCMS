-- Retained client snapshots also declare real references. The client-owned lifecycle
-- guard discovers these FKs, so financially linked clients cannot be erased.
alter table gl_vouchers add constraint gl_vouchers_client_key_check
 check(client_key='' or client_key~'^(saccos|amcos|unions|microfinance|others):[1-9][0-9]{0,17}$');
alter table gl_vouchers
 add column saccos_id bigint generated always as (case when split_part(client_key,':',1)='saccos' then split_part(client_key,':',2)::bigint end) stored references saccos(id),
 add column amcos_id bigint generated always as (case when split_part(client_key,':',1)='amcos' then split_part(client_key,':',2)::bigint end) stored references amcos(id),
 add column union_id bigint generated always as (case when split_part(client_key,':',1)='unions' then split_part(client_key,':',2)::bigint end) stored references unions(id),
 add column microfinance_id bigint generated always as (case when split_part(client_key,':',1)='microfinance' then split_part(client_key,':',2)::bigint end) stored references microfinance_clients(id),
 add column other_client_id bigint generated always as (case when split_part(client_key,':',1)='others' then split_part(client_key,':',2)::bigint end) stored references other_clients(id);
create index gl_voucher_saccos_reference on gl_vouchers(saccos_id) where saccos_id is not null;
create index gl_voucher_amcos_reference on gl_vouchers(amcos_id) where amcos_id is not null;
create index gl_voucher_union_reference on gl_vouchers(union_id) where union_id is not null;
create index gl_voucher_microfinance_reference on gl_vouchers(microfinance_id) where microfinance_id is not null;
create index gl_voucher_other_reference on gl_vouchers(other_client_id) where other_client_id is not null;
