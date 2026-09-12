package com.skillskeeper.skillskeeper.filestorage;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class FileStorageExceptionHandler {

	@ExceptionHandler(EmptyUploadException.class)
	public ProblemDetail handleEmptyUpload(EmptyUploadException ex) {
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
}
