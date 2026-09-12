package com.skillskeeper.skillskeeper.filestorage;

public class StoredFileNotFoundException extends RuntimeException {

	public StoredFileNotFoundException(String fileId) {
		super(FileStorageMessages.FILE_NOT_FOUND_PREFIX + fileId);
	}
}
