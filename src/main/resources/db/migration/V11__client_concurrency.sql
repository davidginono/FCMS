alter table saccos add column version bigint not null default 0;
alter table amcos add column version bigint not null default 0;
alter table unions add column version bigint not null default 0;
alter table microfinance_clients add column version bigint not null default 0;
alter table other_clients add column version bigint not null default 0;
