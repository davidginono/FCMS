-- Match the current canonical PHP client lifecycle without discarding register history.
alter table saccos drop constraint saccos_operational_status_ck;
update saccos set operational_status='INACTIVE' where operational_status='DORMANT';
alter table saccos add constraint saccos_operational_status_ck
    check (operational_status in ('ACTIVE','INACTIVE'));

alter table amcos add column operational_status varchar(20) not null default 'ACTIVE'
    check (operational_status in ('ACTIVE','INACTIVE'));
alter table unions add column operational_status varchar(20) not null default 'ACTIVE'
    check (operational_status in ('ACTIVE','INACTIVE'));
alter table microfinance_clients add column operational_status varchar(20) not null default 'ACTIVE'
    check (operational_status in ('ACTIVE','INACTIVE'));

-- Existing AMCOS tax/commission and Other Client year values are retained for audit and
-- backwards compatibility. They are no longer exposed or writable in the client workflow.
comment on column amcos.withholding_tax is 'Historical Spring-only value; not an AMCOS register input';
comment on column amcos.commission is 'Historical Spring-only value; not an AMCOS register input';
comment on column other_clients.financial_year_id is 'Historical Spring-only assignment; Other Clients is an all-dates register';
