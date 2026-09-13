-- A registered user. The password is never stored: password_hash holds a one-way hash whose
-- per-record salt lives inside the hash itself, so no separate salt column is needed.
create table app_user (
    -- Assigned by the application, like stored_file.id. Not a sequence: a sequential id would
    -- publish how many users exist.
    id            varchar(64)  primary key,
    -- Same bound the login endpoint already enforces, so registration cannot accept a name that
    -- could never be used to log in.
    username      varchar(100) not null,
    -- 60 characters for BCrypt today; the slack is there so replacing the algorithm does not
    -- require migrating the column.
    password_hash varchar(255) not null,
    created_at    timestamptz  not null default clock_timestamp()
);

-- Usernames are unique regardless of case. An expression index rather than a second lowercase
-- column: it enforces the same rule without a copy of the value to keep in step. The name is
-- stored as the user typed it, and that is what is returned to them.
create unique index app_user_username_lower_idx on app_user (lower(username));
