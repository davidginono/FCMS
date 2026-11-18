create table gl_configured_vouchers (
 journal_id bigint primary key references gl_journals(id),
 mapping_id bigint not null references gl_posting_mappings(id),
 amount numeric(14,2) not null check(amount>0),
 created_at timestamptz not null default current_timestamp
);
create function protect_configured_voucher() returns trigger language plpgsql as $$
 begin raise exception 'Configured voucher evidence is immutable'; end $$;
create trigger configured_voucher_immutable before update or delete on gl_configured_vouchers
 for each row execute function protect_configured_voucher();
