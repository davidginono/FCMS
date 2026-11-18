-- The retired line editor has no remaining caller; captured voucher rows are evidence.
-- Shared source preparation and reversal creation still insert their new lines normally.
create function fcms_gl_captured_voucher_line_guard() returns trigger language plpgsql as $$
declare journal_key bigint;
begin
 journal_key:=case when TG_OP='DELETE' then old.journal_id else new.journal_id end;
 if exists(select 1 from gl_vouchers where journal_id=journal_key)
 or exists(select 1 from gl_configured_vouchers where journal_id=journal_key)
 then raise exception 'Captured voucher lines are retained; use a linked reversal' using errcode='23514';end if;
 if TG_OP='DELETE' then return old;end if;
 return new;
end; $$;
create trigger gl_captured_voucher_line_guard before insert or update or delete on gl_lines for each row execute function fcms_gl_captured_voucher_line_guard();
