package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import com.skillskeeper.skillskeeper.filestorage.controller.FileStorageController;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.model.StoredFile;
import com.skillskeeper.skillskeeper.filestorage.service.FileStorage;

/**
 * Covers the download response's headers directly, because what they must contain depends only on the
 * stored metadata — including metadata a running service can no longer produce but may still hold
 * from before the content type was validated on upload.
 */
class FileStorageDownloadHeadersTest {

	private final FileStorage fileStorage = mock(FileStorage.class);
	private final FileStorageController controller = new FileStorageController(fileStorage);

	private ResponseEntity<Resource> downloadWith(String originalFilename, String contentType) {
		FileMetadata metadata = new FileMetadata("an-id", originalFilename, contentType, 4);
		when(fileStorage.load(anyString()))
				.thenReturn(new StoredFile(new ByteArrayResource("data".getBytes()), metadata));
		return controller.download("an-id");
	}

	private static String contentDisposition(ResponseEntity<Resource> response) {
		return response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
	}

	@Test
	void plainAsciiFilenameKeepsTheSimpleQuotedForm() {
		ResponseEntity<Resource> response = downloadWith("report.txt", "text/plain");

		assertThat(contentDisposition(response)).isEqualTo("attachment; filename=\"report.txt\"");
	}

	@Test
	void quoteInFilenameIsEscapedRatherThanBreakingTheHeader() {
		ResponseEntity<Resource> response = downloadWith("my\"file.txt", "text/plain");

		assertThat(contentDisposition(response)).isEqualTo("attachment; filename=\"my\\\"file.txt\"");
	}

	@Test
	void nonAsciiFilenameIsEncodedSoItSurvivesTheHeader() {
		ResponseEntity<Resource> response = downloadWith("отчёт.pdf", "application/pdf");

		String header = contentDisposition(response);
		assertThat(header).startsWith("attachment;").contains("filename*=UTF-8''");
		assertThat(header.chars()).allMatch(c -> c < 0x80);
	}

	@Test
	void controlCharactersInFilenameCannotReachTheHeaderLiterally() {
		ResponseEntity<Resource> response = downloadWith("a\r\nInjected: yes.txt", "text/plain");

		String header = contentDisposition(response);
		assertThat(header).doesNotContain("\r").doesNotContain("\n");
	}

	@Test
	void unusableStoredContentTypeFallsBackToBinaryInsteadOfFailing() {
		ResponseEntity<Resource> response = downloadWith("legacy.bin", "not a media type");

		assertThat(response.getStatusCode().value()).isEqualTo(200);
		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
	}

	@Test
	void absentStoredContentTypeFallsBackToBinary() {
		ResponseEntity<Resource> response = downloadWith("legacy.bin", null);

		assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_OCTET_STREAM);
	}

	@Test
	void responseForbidsContentSniffing() {
		ResponseEntity<Resource> response = downloadWith("report.txt", "text/plain");

		assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
	}
}
