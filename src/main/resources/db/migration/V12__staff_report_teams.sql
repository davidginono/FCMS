create table work_participants(work_id bigint not null references work_items(id),user_id bigint not null references users(id),primary key(work_id,user_id));
create index work_participant_user_idx on work_participants(user_id,work_id);
