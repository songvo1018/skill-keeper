-- Metadata of every file the service has stored. The file's bytes stay on local disk as
-- {id}.bin; this table replaces the JSON sidecar and the in-memory index that preceded it.
create table stored_file (
    -- Not uuid: ids written before this table existed were never validated as UUIDs, and the
    -- one-time import must not reject them.
    id                varchar(64)  primary key,
    original_filename text         not null,
    -- Null when the client declared no content type; such a file is served as octet-stream.
    content_type      varchar(255),
    size_bytes        bigint       not null check (size_bytes >= 0),
    -- Not exposed by the API. It exists to give the listing a stable order.
    -- clock_timestamp(), not now(): now() is the transaction's timestamp, so rows written by one
    -- transaction - every row the one-time import inserts, for instance - would share it and the
    -- order would collapse to the id tie-break.
    created_at        timestamptz  not null default clock_timestamp()
);

create index stored_file_created_at_idx on stored_file (created_at, id);
