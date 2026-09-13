package com.skillskeeper.skillskeeper.filestorage;

import java.nio.file.Path;

import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * Reads and writes the JSON sidecar that records a stored file's metadata.
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

	void write(Path metaPath, FileMetadata metadata) {
		try {
			objectMapper.writeValue(metaPath.toFile(), metadata);
		}
		catch (JacksonException e) {
			throw new FileStorageException(FileStorageMessages.METADATA_WRITE_FAILED_PREFIX + metadata.id(), e);
		}
	}
}
