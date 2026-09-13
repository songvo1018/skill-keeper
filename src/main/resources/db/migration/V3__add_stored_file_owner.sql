-- The user whose token accompanied the upload. Nullable, because files stored before this column
-- existed have no owner and one cannot be invented for them; such a row belongs to nobody and is
-- therefore listed and served to nobody.
--
-- The username itself rather than a key into app_user: the token carries the name, so recording it
-- costs no extra lookup, and there is no account deletion or rename for a key to protect against.
-- The length matches app_user.username, which is the widest name login can accept.
alter table stored_file add column owner_username varchar(100);

-- Login is case-insensitive, so the same person can hold tokens issued for "vo" and "Vo"; matching
-- on lower(owner_username) is what keeps their listing one listing. The expression mirrors
-- app_user_username_lower_idx. created_at and id follow so the listing's order comes from the index
-- rather than from a sort.
create index stored_file_owner_idx on stored_file (lower(owner_username), created_at, id);
