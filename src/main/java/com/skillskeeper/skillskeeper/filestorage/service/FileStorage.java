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
 * Durable storage for uploaded files, addressed by a generated id and owned by the user who
 * uploaded them.
 *
 * <p>Implemented by {@link FileStorageService}. Only what a caller outside the domain needs is
 * declared here: the service's own startup and bookkeeping stay off this interface.
 *
 * <p>The owner is passed in rather than read from the current request: the domain has no business
 * knowing there is an HTTP request, and a caller that forgets to supply an owner then fails to
 * compile instead of quietly reading someone else's files. Names are compared without regard to
 * letter case, the way login accepts them.
 */
public interface FileStorage {

	/**
	 * Persists an uploaded file's content and metadata together, recording {@code owner} as the
	 * file's owner: on failure nothing is left behind.
	 *
	 * @return the metadata recorded for the stored file, including its generated id
	 * @throws EmptyUploadException if no file was supplied, or it holds no bytes
	 * @throws InvalidContentTypeException if the declared content type is not a valid media type
	 * @throws FileStorageException if the content or its metadata could not be persisted
	 */
	FileMetadata store(MultipartFile file, String owner);

	/**
	 * Retrieves a stored file's content together with the metadata recorded for it, provided
	 * {@code owner} owns it.
	 *
	 * @throws StoredFileNotFoundException if no retrievable file is stored under this id, if it
	 * belongs to another user, or if it has no owner at all - the three are deliberately
	 * indistinguishable, so that a caller cannot learn whether an id exists
	 */
	StoredFile load(String id, String owner);

	/**
	 * @return the metadata of every stored file owned by {@code owner}, and of no other file; every
	 * id it contains can be passed to {@link #load(String, String)} successfully by that same owner
	 */
	List<FileMetadata> listFiles(String owner);
}
