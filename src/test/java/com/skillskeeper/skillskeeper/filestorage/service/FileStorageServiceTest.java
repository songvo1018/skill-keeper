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
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRow;

import com.skillskeeper.skillskeeper.support.InMemoryStoredFileRepository;

/**
 * Covers the service against a stand-in repository. Everything about the metadata records that used
 * to live on disk moved to {@link LegacyMetadataImporter} and is tested there.
 */
class FileStorageServiceTest {

	@TempDir
	Path tempDir;

	private final InMemoryStoredFileRepository repository = new InMemoryStoredFileRepository();

	private FileStorageService newService() {
		FileStorageService service = new FileStorageService(new FileStorageProperties(tempDir), repository);
		service.initialize();
		return service;
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
	void storedFileIsImmediatelyVisibleInTheListing() {
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

	@Test
	void listFilesReturnsFilesInTheOrderTheyWereStored() {
		FileStorageService service = newService();
		FileMetadata first = service.store(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));
		FileMetadata second = service.store(new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes()));
		FileMetadata third = service.store(new MockMultipartFile("file", "c.txt", "text/plain", "c".getBytes()));

		assertThat(service.listFiles()).containsExactly(first, second, third);
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
		InMemoryStoredFileRepository failing = new InMemoryStoredFileRepository() {
			@Override
			public void insert(StoredFileRow row) {
				throw new IllegalStateException("insert rejected");
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

	// --- The table is the single source of truth (Finding 5) ---

	@Test
	void everyListedIdCanBeLoaded() throws IOException {
		repository.insert(StoredFileRow.forInsert(new FileMetadata("row-without-content", "gone.txt", "text/plain",
				1)));
		Files.writeString(tempDir.resolve("content-without-row" + FileStorageMessages.BIN_FILE_SUFFIX), "x");

		FileStorageService service = newService();
		service.store(new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));
		service.store(new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes()));

		assertThat(service.listFiles()).hasSize(2);
		for (FileMetadata metadata : service.listFiles()) {
			assertThat(service.load(metadata.id()).metadata()).isEqualTo(metadata);
		}
	}

	@Test
	void rowWhoseContentIsMissingIsNeitherListedNorLoadable() {
		FileMetadata orphan = new FileMetadata("orphan-row", "gone.txt", "text/plain", 7);
		repository.insert(StoredFileRow.forInsert(orphan));

		FileStorageService service = newService();

		assertThat(service.listFiles()).isEmpty();
		assertThatThrownBy(() -> service.load(orphan.id())).isInstanceOf(StoredFileNotFoundException.class);
	}

	@Test
	void loadDoesNotDependOnAnyMetadataRecordOnDisk() throws IOException {
		FileStorageService service = newService();
		FileMetadata stored = service.store(
				new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes()));

		Files.writeString(tempDir.resolve(stored.id() + FileStorageMessages.META_FILE_SUFFIX), "corrupted later");

		assertThat(service.load(stored.id()).metadata()).isEqualTo(stored);
	}

	@Test
	void storingWritesContentButNoMetadataRecordOnDisk() {
		FileStorageService service = newService();

		FileMetadata stored = service.store(
				new MockMultipartFile("file", "a.txt", "text/plain", "data".getBytes()));

		assertThat(tempDir.resolve(stored.id() + FileStorageMessages.BIN_FILE_SUFFIX)).isRegularFile();
		assertThat(tempDir.resolve(stored.id() + FileStorageMessages.META_FILE_SUFFIX)).doesNotExist();
	}
}
