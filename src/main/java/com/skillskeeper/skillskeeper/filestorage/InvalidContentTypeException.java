package com.skillskeeper.skillskeeper.filestorage;

public class InvalidContentTypeException extends RuntimeException {

	public InvalidContentTypeException(String contentType) {
		super(FileStorageMessages.INVALID_CONTENT_TYPE_PREFIX + contentType);
	}
}
