package com.skillskeeper.skillskeeper.filestorage.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;
import com.skillskeeper.skillskeeper.filestorage.exception.EmptyUploadException;
import com.skillskeeper.skillskeeper.filestorage.exception.FileStorageException;
import com.skillskeeper.skillskeeper.filestorage.exception.InvalidContentTypeException;
import com.skillskeeper.skillskeeper.filestorage.exception.StoredFileNotFoundException;

@RestControllerAdvice
public class FileStorageExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(FileStorageExceptionHandler.class);

	@ExceptionHandler(EmptyUploadException.class)
	public ProblemDetail handleEmptyUpload(EmptyUploadException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(InvalidContentTypeException.class)
	public ProblemDetail handleInvalidContentType(InvalidContentTypeException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

	@ExceptionHandler(MaxUploadSizeExceededException.class)
	public ProblemDetail handleTooLarge(MaxUploadSizeExceededException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONTENT_TOO_LARGE,
				"Uploaded file exceeds the maximum allowed size");
	}

	@ExceptionHandler(StoredFileNotFoundException.class)
	public ProblemDetail handleNotFound(StoredFileNotFoundException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
	}

	/**
	 * The cause is logged rather than returned: it names internal paths and failure modes the client
	 * can do nothing with, but an operator needs it to tell what happened.
	 */
	@ExceptionHandler(FileStorageException.class)
	public ProblemDetail handleStorageFailure(FileStorageException ex) {
		log.error("Storage operation failed", ex);
		return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				FileStorageMessages.STORAGE_FAILURE_DETAIL);
	}
}
