package com.skillskeeper.skillskeeper.filestorage.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Access to the {@code stored_file} table.
 *
 * <p>Rows are inserted through the explicit statement below rather than {@code save}: ids are
 * assigned by the application, and Spring Data JDBC treats an entity carrying a non-null id as one
 * that already exists, so {@code save} would issue an update, match no row and fail. Writing the
 * insert out also lets {@code created_at} fall to its database default.
 *
 * <p>Reads are scoped to an owner rather than filtered afterwards, so another user's row never
 * reaches the process. Both read queries compare on {@code lower(owner_username)}, the expression
 * {@code stored_file_owner_idx} is built on: login accepts a name in any letter case, and one
 * person's files must stay one listing however they typed their name. A row with no owner matches
 * no name, because a comparison against {@code null} is never true - that is what keeps files
 * stored before owners existed out of everyone's listing.
 */
@Repository
public interface StoredFileRepository extends CrudRepository<StoredFileRow, String> {

	/**
	 * @return every row owned by this user, oldest first, with the id breaking ties so the order is
	 * total
	 */
	@Query("""
			select * from stored_file
			where lower(owner_username) = lower(:owner)
			order by created_at, id
			""")
	List<StoredFileRow> findAllOrderedByOwner(@Param("owner") String owner);

	/**
	 * @return the row with this id if this user owns it, empty otherwise - including when the row
	 * exists but belongs to someone else, so that a caller cannot tell the two apart
	 */
	@Query("select * from stored_file where id = :id and lower(owner_username) = lower(:owner)")
	Optional<StoredFileRow> findByIdAndOwner(@Param("id") String id, @Param("owner") String owner);

	@Modifying
	@Query("""
			insert into stored_file (id, original_filename, content_type, size_bytes, owner_username)
			values (:id, :originalFilename, :contentType, :sizeBytes, :ownerUsername)
			""")
	void insert(@Param("id") String id, @Param("originalFilename") String originalFilename,
			@Param("contentType") String contentType, @Param("sizeBytes") long sizeBytes,
			@Param("ownerUsername") String ownerUsername);

	default void insert(StoredFileRow row) {
		insert(row.id(), row.originalFilename(), row.contentType(), row.sizeBytes(), row.ownerUsername());
	}
}
