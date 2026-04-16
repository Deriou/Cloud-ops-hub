alter table post
    add column if not exists note_id varchar(128) null,
    add column if not exists status varchar(32) not null default 'published',
    add column if not exists source_path varchar(500) null,
    add column if not exists content_hash varchar(128) null,
    add column if not exists last_sync_time datetime null;

update post
set status = 'published'
where status is null or status = '';

create unique index if not exists uk_post_note_id on post (note_id);
create index if not exists idx_post_status on post (status);
