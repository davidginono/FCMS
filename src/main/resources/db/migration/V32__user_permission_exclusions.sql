-- Role bundles remain defaults; an administrator may exclude individual defaults per user.
-- Existing users retain all current role and per-user grants.
create table user_claim_denials (
 user_id bigint not null references users(id),
 claim varchar(80) not null references claims(name),
 primary key(user_id,claim)
);
