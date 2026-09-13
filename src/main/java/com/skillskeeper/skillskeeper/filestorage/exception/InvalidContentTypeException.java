package com.skillskeeper.skillskeeper.filestorage.exception;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;

public class InvalidContentTypeException extends RuntimeException {

	public InvalidContentTypeException(String contentType) {
		super(FileStorageMessages.INVALID_CONTENT_TYPE_PREFIX + contentType);
	}
}
