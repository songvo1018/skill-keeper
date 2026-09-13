package com.skillskeeper.skillskeeper.filestorage.exception;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;

public class StoredFileNotFoundException extends RuntimeException {

	public StoredFileNotFoundException(String fileId) {
		super(FileStorageMessages.FILE_NOT_FOUND_PREFIX + fileId);
	}
}
