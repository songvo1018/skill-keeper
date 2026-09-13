package com.skillskeeper.skillskeeper.filestorage.service;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;
import com.skillskeeper.skillskeeper.filestorage.exception.FileStorageException;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads the JSON sidecar that used to record a stored file's metadata.
 *
 * <p>Nothing writes a sidecar any more - {@code stored_file} holds metadata now - so only reading
 * remains, for {@link LegacyMetadataImporter} to pick up files stored before the table existed.
 *
 * <p>Deliberately owns its own {@link ObjectMapper} rather than the injected one Spring configures
 * for HTTP: the sidecar is an on-disk format, and sharing the web mapper would let a future
 * API-wide JSON setting silently change it and break reading files already on disk.
 */
@Component
class FileMetadataStore {

	private final ObjectMapper objectMapper = new ObjectMapper();

	FileMetadata read(Path metaPath) {
		try {
			return objectMapper.readValue(metaPath.toFile(), FileMetadata.class);
		}
		catch (JacksonException e) {
			throw new FileStorageException(FileStorageMessages.METADATA_READ_FAILED_PREFIX + metaPath.getFileName(), e);
		}
	}
}
