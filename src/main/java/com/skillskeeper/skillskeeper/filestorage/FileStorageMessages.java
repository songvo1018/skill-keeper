package com.skillskeeper.skillskeeper.filestorage;

final class FileStorageMessages {

	static final String FILE_NOT_FOUND_PREFIX = "No stored file with id: ";
	static final String DIR_CREATE_FAILED_PREFIX = "Could not create file storage directory: ";
	static final String METADATA_WRITE_FAILED_PREFIX = "Failed to write metadata for file ";
	static final String METADATA_READ_FAILED_PREFIX = "Failed to read metadata for file ";
	static final String DIR_SCAN_FAILED_PREFIX = "Failed to scan file storage directory: ";

	static final String BIN_FILE_SUFFIX = ".bin";
	static final String META_FILE_SUFFIX = ".meta.json";

	static final String CONTENT_DISPOSITION_ATTACHMENT_TEMPLATE = "attachment; filename=\"%s\"";

	private FileStorageMessages() {
	}
}
