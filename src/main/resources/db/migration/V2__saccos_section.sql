update client_status set name = 'PAID' where id = 1;
update client_status set name = 'UNPAID' where id = 2;
update client_status set name = 'PENDING' where id = 3;
delete from client_status where id = 4;

insert into financial_year (fyear) values
    ('2020'),
    ('2021'),
    ('2022'),
    ('2023'),
    ('2024'),
    ('2025')
on conflict (fyear) do nothing;

alter table saccos
    add column date_paid date,
    add column withholding_tax numeric(14, 2) not null default 0,
    add column commission numeric(14, 2) not null default 0,
    add column programmer_cost numeric(14, 2) not null default 0,
    add column android_cost numeric(14, 2) not null default 0,
    add column system_reinstall_fee numeric(14, 2) not null default 0,
    add column bulk_sms_fee numeric(14, 2) not null default 0,
    add column hosting_fee numeric(14, 2) not null default 0,
    add column ess_fee numeric(14, 2) not null default 0,
    add column cloud_backup_fee numeric(14, 2) not null default 0,
    add column cloud_backup_cost numeric(14, 2) not null default 0,
    add column operational_status varchar(20) not null default 'ACTIVE',
    add column invoice_status varchar(20) not null default 'NO',
    add column visible boolean not null default true;

alter table saccos
    add constraint saccos_extended_amounts_ck check (
        withholding_tax >= 0
        and commission >= 0
        and programmer_cost >= 0
        and android_cost >= 0
        and system_reinstall_fee >= 0
        and bulk_sms_fee >= 0
        and hosting_fee >= 0
        and ess_fee >= 0
        and cloud_backup_fee >= 0
        and cloud_backup_cost >= 0
    ),
    add constraint saccos_operational_status_ck check (operational_status in ('ACTIVE', 'DORMANT')),
    add constraint saccos_invoice_status_ck check (invoice_status in ('YES', 'NO'));

create index saccos_visible_year_status_name_idx
    on saccos (financial_year_id, client_status_id, saccos_name)
    where visible;

create index saccos_visible_status_year_name_idx
    on saccos (client_status_id, financial_year_id, saccos_name)
    where visible;

create index saccos_visible_region_idx
    on saccos (lower(region))
    where visible;
