package com.skillskeeper.skillskeeper.auth.repository;

import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Access to the {@code app_user} table.
 *
 * <p>Rows are inserted through the explicit statement below rather than {@code save}: ids are
 * assigned by the application, and Spring Data JDBC treats an entity carrying a non-null id as one
 * that already exists, so {@code save} would issue an update, match no row and fail. Writing the
 * insert out also lets {@code created_at} fall to its database default.
 *
 * <p>There is deliberately no "is this username taken" method. Checking before inserting is a race
 * two simultaneous registrations both win; the unique index is what actually decides, so the insert
 * is attempted and its failure is what reports the conflict.
 */
@Repository
public interface AppUserRepository extends CrudRepository<AppUserRow, String> {

	@Query("select * from app_user where lower(username) = lower(:username)")
	Optional<AppUserRow> findByUsernameIgnoringCase(@Param("username") String username);

	@Modifying
	@Query("""
			insert into app_user (id, username, password_hash)
			values (:id, :username, :passwordHash)
			""")
	void insert(@Param("id") String id, @Param("username") String username,
			@Param("passwordHash") String passwordHash);

	default void insert(AppUserRow row) {
		insert(row.id(), row.username(), row.passwordHash());
	}
}
