package com.skillskeeper.skillskeeper.filestorage.service;

import java.util.List;

import org.springframework.web.multipart.MultipartFile;

import com.skillskeeper.skillskeeper.filestorage.exception.EmptyUploadException;
import com.skillskeeper.skillskeeper.filestorage.exception.FileStorageException;
import com.skillskeeper.skillskeeper.filestorage.exception.InvalidContentTypeException;
import com.skillskeeper.skillskeeper.filestorage.exception.StoredFileNotFoundException;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.model.StoredFile;

/**
 * Durable storage for uploaded files, addressed by a generated id.
 *
 * <p>Implemented by {@link FileStorageService}. Only what a caller outside the domain needs is
 * declared here: the service's own startup and bookkeeping stay off this interface.
 */
public interface FileStorage {

	/**
	 * Persists an uploaded file's content and metadata together: on failure nothing is left behind.
	 *
	 * @return the metadata recorded for the stored file, including its generated id
	 * @throws EmptyUploadException if no file was supplied, or it holds no bytes
	 * @throws InvalidContentTypeException if the declared content type is not a valid media type
	 * @throws FileStorageException if the content or its metadata could not be persisted
	 */
	FileMetadata store(MultipartFile file);

	/**
	 * Retrieves a stored file's content together with the metadata recorded for it.
	 *
	 * @throws StoredFileNotFoundException if no retrievable file is stored under this id
	 */
	StoredFile load(String id);

	/**
	 * @return the metadata of every stored file; every id it contains can be passed to
	 * {@link #load(String)} successfully
	 */
	List<FileMetadata> listFiles();
}
