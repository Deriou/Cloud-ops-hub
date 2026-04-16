set @db_name = database();

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.columns
            where table_schema = @db_name
              and table_name = 'post'
              and column_name = 'note_id'
        ),
        "select 'skip add column note_id' as message",
        'alter table post add column note_id varchar(128) null'
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.columns
            where table_schema = @db_name
              and table_name = 'post'
              and column_name = 'status'
        ),
        "select 'skip add column status' as message",
        "alter table post add column status varchar(32) not null default 'published'"
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.columns
            where table_schema = @db_name
              and table_name = 'post'
              and column_name = 'source_path'
        ),
        "select 'skip add column source_path' as message",
        'alter table post add column source_path varchar(500) null'
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.columns
            where table_schema = @db_name
              and table_name = 'post'
              and column_name = 'content_hash'
        ),
        "select 'skip add column content_hash' as message",
        'alter table post add column content_hash varchar(128) null'
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.columns
            where table_schema = @db_name
              and table_name = 'post'
              and column_name = 'last_sync_time'
        ),
        "select 'skip add column last_sync_time' as message",
        'alter table post add column last_sync_time datetime null'
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

update post
set status = 'published'
where status is null or status = '';

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.statistics
            where table_schema = @db_name
              and table_name = 'post'
              and index_name = 'uk_post_note_id'
        ),
        "select 'skip create index uk_post_note_id' as message",
        'create unique index uk_post_note_id on post (note_id)'
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;

set @sql = (
    select if(
        exists(
            select 1
            from information_schema.statistics
            where table_schema = @db_name
              and table_name = 'post'
              and index_name = 'idx_post_status'
        ),
        "select 'skip create index idx_post_status' as message",
        'create index idx_post_status on post (status)'
    )
);
prepare stmt from @sql;
execute stmt;
deallocate prepare stmt;
