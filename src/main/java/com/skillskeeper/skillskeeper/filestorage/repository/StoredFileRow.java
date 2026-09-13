package com.skillskeeper.skillskeeper.filestorage.repository;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;

/**
 * One row of {@code stored_file}: the metadata of a stored file, as the database holds it.
 *
 * <p>Deliberately separate from {@link FileMetadata}, which is the API response body. Mapping the
 * table onto that record would tie the JSON contract to the schema, so adding a column would change
 * the API; {@code createdAt} is exactly such a column - it exists to give the listing a stable
 * order and is never sent to a client. {@code ownerUsername} is another: who owns a file decides
 * whether the caller sees it at all, and is not part of what they are told about it.
 *
 * @param ownerUsername the user whose token accompanied the upload, written as they typed it;
 * {@code null} on a row stored before files had owners, which therefore belongs to nobody
 * @param createdAt {@code null} on a row that has not been inserted yet: the column is filled by
 * the database default, so an insert never supplies it
 */
@Table("stored_file")
public record StoredFileRow(@Id String id, String originalFilename, String contentType, long sizeBytes,
		String ownerUsername, Instant createdAt) {

	public static StoredFileRow forInsert(FileMetadata metadata, String ownerUsername) {
		return new StoredFileRow(metadata.id(), metadata.originalFilename(), metadata.contentType(), metadata.size(),
				ownerUsername, null);
	}

	public FileMetadata toMetadata() {
		return new FileMetadata(id, originalFilename, contentType, sizeBytes);
	}
}
