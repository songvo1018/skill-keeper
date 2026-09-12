package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.util.StreamUtils;

import tools.jackson.databind.ObjectMapper;

class FileStorageServiceTest {

	@TempDir
	Path tempDir;

	private FileStorageService newService() {
		return new FileStorageService(new FileStorageProperties(tempDir), new ObjectMapper());
	}

	@Test
	void storeAndLoadRoundTripsBytesAndMetadata() throws IOException {
		FileStorageService service = newService();
		MockMultipartFile upload = new MockMultipartFile("file", "report.txt", "text/plain",
				"hello world".getBytes());

		FileMetadata stored = service.store(upload);

		assertThat(stored.originalFilename()).isEqualTo("report.txt");
		assertThat(stored.contentType()).isEqualTo("text/plain");
		assertThat(stored.size()).isEqualTo(11);

		StoredFile loaded = service.load(stored.id());

		assertThat(loaded.metadata()).isEqualTo(stored);
		assertThat(StreamUtils.copyToByteArray(loaded.resource().getInputStream()))
				.isEqualTo("hello world".getBytes());
	}

	@Test
	void storeSanitizesPathTraversalInOriginalFilename() {
		FileStorageService service = newService();
		MockMultipartFile upload = new MockMultipartFile("file", "../../etc/passwd", "text/plain",
				"data".getBytes());

		FileMetadata stored = service.store(upload);

		assertThat(stored.originalFilename()).isEqualTo("passwd");
	}

	@Test
	void loadUnknownIdThrowsNotFound() {
		FileStorageService service = newService();

		assertThatThrownBy(() -> service.load("does-not-exist"))
				.isInstanceOf(StoredFileNotFoundException.class);
	}

	@Test
	void loadPathTraversalIdThrowsNotFound() {
		FileStorageService service = newService();

		assertThatThrownBy(() -> service.load("../../etc/passwd"))
				.isInstanceOf(StoredFileNotFoundException.class);
	}

	@Test
	void storeEmptyFileThrows() {
		FileStorageService service = newService();
		MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

		assertThatThrownBy(() -> service.store(empty))
				.isInstanceOf(EmptyUploadException.class);
	}
}
