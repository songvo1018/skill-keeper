package com.skillskeeper.skillskeeper.filestorage.repository;

import java.util.List;

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
 */
@Repository
public interface StoredFileRepository extends CrudRepository<StoredFileRow, String> {

	/**
	 * @return every row, oldest first, with the id breaking ties so the order is total
	 */
	@Query("select * from stored_file order by created_at, id")
	List<StoredFileRow> findAllOrdered();

	@Modifying
	@Query("""
			insert into stored_file (id, original_filename, content_type, size_bytes)
			values (:id, :originalFilename, :contentType, :sizeBytes)
			""")
	void insert(@Param("id") String id, @Param("originalFilename") String originalFilename,
			@Param("contentType") String contentType, @Param("sizeBytes") long sizeBytes);

	default void insert(StoredFileRow row) {
		insert(row.id(), row.originalFilename(), row.contentType(), row.sizeBytes());
	}
}
