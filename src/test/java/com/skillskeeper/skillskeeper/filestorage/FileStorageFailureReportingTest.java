package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import com.skillskeeper.skillskeeper.support.LogCapture;

/**
 * A storage failure is reduced to a generic 500 for the client, so the cause has to reach the log
 * instead — otherwise the failure leaves no trace anywhere.
 */
class FileStorageFailureReportingTest {

	private final FileStorageExceptionHandler handler = new FileStorageExceptionHandler();

	@Test
	void storageFailureIsLoggedAndNotDisclosedToTheClient() {
		FileStorageException failure = new FileStorageException("Failed to write metadata for file secret-id",
				new IOException("D:\\internal\\path is full"));

		try (LogCapture logs = LogCapture.of(FileStorageExceptionHandler.class)) {
			ProblemDetail problem = handler.handleStorageFailure(failure);

			assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
			assertThat(problem.getDetail())
					.isEqualTo(FileStorageMessages.STORAGE_FAILURE_DETAIL)
					.doesNotContain("secret-id")
					.doesNotContain("internal");

			assertThat(logs.warningsAndWorse())
					.anySatisfy(message -> assertThat(message).contains("Storage operation failed"));
		}
	}

	@Test
	void invalidContentTypeIsReportedAsBadRequest() {
		ProblemDetail problem = handler.handleInvalidContentType(new InvalidContentTypeException("not a media type"));

		assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
		assertThat(problem.getDetail()).contains("not a media type");
	}
}
