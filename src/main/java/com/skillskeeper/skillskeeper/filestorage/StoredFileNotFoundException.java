package com.skillskeeper.skillskeeper.filestorage;

public class StoredFileNotFoundException extends RuntimeException {

	public StoredFileNotFoundException(String fileId) {
		super("No stored file with id: " + fileId);
	}
}
