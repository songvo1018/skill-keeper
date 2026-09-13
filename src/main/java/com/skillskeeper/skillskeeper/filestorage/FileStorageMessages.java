package com.skillskeeper.skillskeeper.filestorage;

final class FileStorageMessages {

	static final String FILE_NOT_FOUND_PREFIX = "No stored file with id: ";
	static final String DIR_CREATE_FAILED_PREFIX = "Could not create file storage directory: ";
	static final String METADATA_WRITE_FAILED_PREFIX = "Failed to write metadata for file ";
	static final String METADATA_READ_FAILED_PREFIX = "Failed to read metadata for file ";
	static final String DIR_SCAN_FAILED_PREFIX = "Failed to scan file storage directory: ";
	static final String PAYLOAD_PUBLISH_FAILED_PREFIX = "Failed to publish stored content for file ";
	static final String INVALID_CONTENT_TYPE_PREFIX = "Uploaded file declares an invalid content type: ";

	/**
	 * Returned to the client in place of a storage failure's real cause, which is logged instead.
	 */
	static final String STORAGE_FAILURE_DETAIL = "The file could not be stored or retrieved";

	static final String BIN_FILE_SUFFIX = ".bin";
	static final String META_FILE_SUFFIX = ".meta.json";
	static final String TEMP_FILE_SUFFIX = ".tmp";

	private FileStorageMessages() {
	}
}
