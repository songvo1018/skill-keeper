package com.skillskeeper.skillskeeper.filestorage.service;

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

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;
import com.skillskeeper.skillskeeper.filestorage.exception.EmptyUploadException;
import com.skillskeeper.skillskeeper.filestorage.exception.FileStorageException;
import com.skillskeeper.skillskeeper.filestorage.exception.InvalidContentTypeException;
import com.skillskeeper.skillskeeper.filestorage.exception.StoredFileNotFoundException;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.model.FileStorageProperties;
import com.skillskeeper.skillskeeper.filestorage.model.StoredFile;

import com.skillskeeper.skillskeeper.support.LogCapture;

import tools.jackson.databind.ObjectMapper;

class FileStorageServiceTest {

	@TempDir
	Path tempDir;

	private FileStorageService newService() {
		FileStorageService service = new FileStorageService(new FileStorageProperties(tempDir),
				new FileMetadataStore());
		service.initialize();
		return service;
	}

	private void writeSidecar(String id, Object body) {
		new ObjectMapper().writeValue(tempDir.resolve(id + FileStorageMessages.META_FILE_SUFFIX).toFile(), body);
	}

	private void writePayload(String id) throws IOException {
		Files.writeString(tempDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX), "content");
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
		writeSidecar(preExisting.id(), preExisting);
		writePayload(preExisting.id());

		FileStorageService service = newService();

		assertThat(service.listFiles()).containsExactly(preExisting);
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

	// --- Startup scan survives unusable records (Finding 1) ---

	@Test
	void sidecarHoldingEmptyJsonObjectIsSkippedInsteadOfFailing() throws IOException {
		writeSidecar("broken", java.util.Map.of());
		FileMetadata valid = new FileMetadata("valid-id", "ok.txt", "text/plain", 7);
		writeSidecar(valid.id(), valid);
		writePayload(valid.id());

		FileStorageService service = newService();

		assertThat(service.listFiles()).containsExactly(valid);
	}

	@Test
	void corruptedSidecarIsSkippedDuringIndexLoad() throws IOException {
		Files.writeString(tempDir.resolve("broken" + FileStorageMessages.META_FILE_SUFFIX), "not valid json");

		FileStorageService service = newService();

		assertThat(service.listFiles()).isEmpty();
	}

	@Test
	void sidecarWhoseRecordedIdDisagreesWithItsFilenameIsSkipped() throws IOException {
		writeSidecar("filename-id", new FileMetadata("a-different-id", "ok.txt", "text/plain", 7));
		writePayload("filename-id");

		FileStorageService service = newService();

		assertThat(service.listFiles()).isEmpty();
	}

	@Test
	void sidecarWithoutStoredContentIsSkipped() {
		FileMetadata orphan = new FileMetadata("orphan-id", "gone.txt", "text/plain", 7);
		writeSidecar(orphan.id(), orphan);

		FileStorageService service = newService();

		assertThat(service.listFiles()).isEmpty();
		assertThatThrownBy(() -> service.load(orphan.id())).isInstanceOf(StoredFileNotFoundException.class);
	}

	@Test
	void skippedSidecarIsLogged() throws IOException {
		Files.writeString(tempDir.resolve("broken" + FileStorageMessages.META_FILE_SUFFIX), "not valid json");

		try (LogCapture logs = LogCapture.of(FileStorageService.class)) {
			newService();

			assertThat(logs.warningsAndWorse()).anySatisfy(message -> assertThat(message).contains("broken"));
		}
	}

	// --- Content type validated on write (Finding 2) ---

	@Test
	void storeAcceptsWellFormedContentType() {
		FileStorageService service = newService();

		FileMetadata stored = service.store(
				new MockMultipartFile("file", "a.json", "application/json; charset=UTF-8", "{}".getBytes()));

		assertThat(stored.contentType()).contains("application/json");
	}

	@Test
	void storeRejectsMalformedContentTypeAndWritesNothing() {
		FileStorageService service = newService();

		for (String malformed : List.of("foo", "text/", "a/b; charset=\"")) {
			assertThatThrownBy(() -> service.store(
					new MockMultipartFile("file", "a.txt", malformed, "data".getBytes())))
					.isInstanceOf(InvalidContentTypeException.class);
		}

		assertThat(tempDir).isEmptyDirectory();
		assertThat(service.listFiles()).isEmpty();
	}

	@Test
	void storeAcceptsAbsentContentType() {
		FileStorageService service = newService();

		FileMetadata stored = service.store(new MockMultipartFile("file", "a.txt", null, "data".getBytes()));

		assertThat(stored.contentType()).isNull();
	}

	// --- Upload is atomic (Finding 4) ---

	@Test
	void metadataWriteFailureLeavesNothingBehind() {
		FileMetadataStore failing = new FileMetadataStore() {
			@Override
			void write(Path metaPath, FileMetadata metadata) {
				throw new FileStorageException(FileStorageMessages.METADATA_WRITE_FAILED_PREFIX + metadata.id(),
						new IOException("disk full"));
			}
		};
		FileStorageService service = new FileStorageService(new FileStorageProperties(tempDir), failing);
		service.initialize();

		assertThatThrownBy(() -> service.store(
				new MockMultipartFile("file", "a.txt", "text/plain", "data".getBytes())))
				.isInstanceOf(FileStorageException.class);

		assertThat(tempDir).isEmptyDirectory();
		assertThat(service.listFiles()).isEmpty();
	}

	// --- Index is the single source of truth (Finding 5) ---

	@Test
	void everyListedIdCanBeLoaded() throws IOException {
		writeSidecar("orphan-sidecar", new FileMetadata("orphan-sidecar", "gone.txt", "text/plain", 1));
		Files.writeString(tempDir.resolve("payload-without-sidecar" + FileStorageMessages.BIN_FILE_SUFFIX), "x");
		Files.writeString(tempDir.resolve("garbage" + FileStorageMessages.META_FILE_SUFFIX), "not json");

		FileStorageService service = newService();
		service.store(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));
		service.store(new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes()));

		assertThat(service.listFiles()).hasSize(2);
		for (FileMetadata metadata : service.listFiles()) {
			assertThat(service.load(metadata.id()).metadata()).isEqualTo(metadata);
		}
	}

	@Test
	void loadDoesNotDependOnTheSidecarStillBeingReadable() throws IOException {
		FileStorageService service = newService();
		FileMetadata stored = service.store(
				new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));

		Files.writeString(tempDir.resolve(stored.id() + FileStorageMessages.META_FILE_SUFFIX), "corrupted later");

		assertThat(service.load(stored.id()).metadata()).isEqualTo(stored);
	}

	// --- Storage format is pinned independently of the web mapper (Finding 13) ---

	@Test
	void sidecarWrittenByAPlainMapperIsStillReadable() throws IOException {
		FileMetadata metadata = new FileMetadata("pinned-id", "pinned.txt", "text/plain", 4);
		writeSidecar(metadata.id(), metadata);
		writePayload(metadata.id());

		FileStorageService service = newService();

		assertThat(service.listFiles()).containsExactly(metadata);
	}
}
