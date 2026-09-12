package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

	@Test
	void indexIsPopulatedFromExistingSidecarsAtConstruction() throws IOException {
		FileMetadata preExisting = new FileMetadata("pre-existing-id", "old.txt", "text/plain", 3);
		new ObjectMapper().writeValue(
				tempDir.resolve(preExisting.id() + FileStorageMessages.META_FILE_SUFFIX).toFile(), preExisting);

		FileStorageService service = newService();

		assertThat(service.listFiles()).containsExactly(preExisting);
	}

	@Test
	void corruptedSidecarIsSkippedDuringIndexLoad() throws IOException {
		Files.writeString(tempDir.resolve("broken" + FileStorageMessages.META_FILE_SUFFIX), "not valid json");

		FileStorageService service = newService();

		assertThat(service.listFiles()).isEmpty();
	}

	@Test
	void storedFileIsImmediatelyVisibleInIndex() {
		FileStorageService service = newService();
		MockMultipartFile upload = new MockMultipartFile("file", "report.txt", "text/plain",
				"hello world".getBytes());

		FileMetadata stored = service.store(upload);

		assertThat(service.listFiles()).containsExactly(stored);
	}

	@Test
	void listFilesReturnsAllStoredFiles() {
		FileStorageService service = newService();
		FileMetadata first = service.store(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));
		FileMetadata second = service.store(new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes()));

		assertThat(service.listFiles()).containsExactlyInAnyOrder(first, second);
	}

	@Test
	void listFilesReturnsEmptyListWhenNoneStored() {
		FileStorageService service = newService();

		assertThat(service.listFiles()).isEqualTo(List.of());
	}
}
