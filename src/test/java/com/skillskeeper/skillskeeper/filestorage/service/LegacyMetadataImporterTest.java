package com.skillskeeper.skillskeeper.filestorage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.model.FileStorageProperties;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRow;

import com.skillskeeper.skillskeeper.support.InMemoryStoredFileRepository;
import com.skillskeeper.skillskeeper.support.LogCapture;

import tools.jackson.databind.ObjectMapper;

/**
 * The metadata records left on disk by the sidecar format are read exactly once, into the table.
 * These are the cases the startup scan used to cover, now that the scan has become an import.
 *
 * <p>Assertions are made on the rows rather than on a listing: a sidecar records no owner, so an
 * imported file belongs to nobody and appears in no user's listing until an owner is set on it.
 */
class LegacyMetadataImporterTest {

	@TempDir
	Path tempDir;

	private final InMemoryStoredFileRepository repository = new InMemoryStoredFileRepository();

	private LegacyMetadataImporter newImporter() {
		return new LegacyMetadataImporter(new FileStorageProperties(tempDir), new FileMetadataStore(), repository);
	}

	private void writeSidecar(String id, Object body) {
		new ObjectMapper().writeValue(tempDir.resolve(id + FileStorageMessages.META_FILE_SUFFIX).toFile(), body);
	}

	private void writePayload(String id) throws IOException {
		Files.writeString(tempDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX), "content");
	}

	private StoredFileRow row(String id) {
		return repository.findById(id).orElseThrow();
	}

	@Test
	void metadataLeftOnDiskIsImportedWithoutAnOwner() throws IOException {
		FileMetadata preExisting = new FileMetadata("pre-existing-id", "old.txt", "text/plain", 3);
		writeSidecar(preExisting.id(), preExisting);
		writePayload(preExisting.id());

		newImporter().importSidecars();

		assertThat(row(preExisting.id()).toMetadata()).isEqualTo(preExisting);
		assertThat(row(preExisting.id()).ownerUsername()).isNull();
	}

	@Test
	void importingTwiceDoesNotDuplicateOrChangeARow() throws IOException {
		FileMetadata preExisting = new FileMetadata("pre-existing-id", "old.txt", "text/plain", 3);
		writeSidecar(preExisting.id(), preExisting);
		writePayload(preExisting.id());

		LegacyMetadataImporter importer = newImporter();
		importer.importSidecars();
		importer.importSidecars();

		assertThat(repository.count()).isEqualTo(1);
		assertThat(row(preExisting.id()).toMetadata()).isEqualTo(preExisting);
	}

	@Test
	void aRowThatAlreadyExistsIsLeftAsItIs() throws IOException {
		FileMetadata onDisk = new FileMetadata("known-id", "from-disk.txt", "text/plain", 3);
		writeSidecar(onDisk.id(), onDisk);
		writePayload(onDisk.id());
		FileMetadata alreadyStored = new FileMetadata("known-id", "already-in-table.txt", "text/plain", 9);
		repository.insert(StoredFileRow.forInsert(alreadyStored, null));

		newImporter().importSidecars();

		assertThat(repository.count()).isEqualTo(1);
		assertThat(row(alreadyStored.id()).toMetadata()).isEqualTo(alreadyStored);
	}

	@Test
	void sidecarHoldingEmptyJsonObjectIsSkippedInsteadOfFailing() throws IOException {
		writeSidecar("broken", Map.of());
		FileMetadata valid = new FileMetadata("valid-id", "ok.txt", "text/plain", 7);
		writeSidecar(valid.id(), valid);
		writePayload(valid.id());

		newImporter().importSidecars();

		assertThat(repository.count()).isEqualTo(1);
		assertThat(row(valid.id()).toMetadata()).isEqualTo(valid);
	}

	@Test
	void corruptedSidecarIsSkipped() throws IOException {
		Files.writeString(tempDir.resolve("broken" + FileStorageMessages.META_FILE_SUFFIX), "not valid json");

		newImporter().importSidecars();

		assertThat(repository.count()).isZero();
	}

	@Test
	void sidecarWhoseRecordedIdDisagreesWithItsFilenameIsSkipped() throws IOException {
		writeSidecar("filename-id", new FileMetadata("a-different-id", "ok.txt", "text/plain", 7));
		writePayload("filename-id");

		newImporter().importSidecars();

		assertThat(repository.count()).isZero();
	}

	@Test
	void sidecarWithoutStoredContentIsSkipped() {
		FileMetadata orphan = new FileMetadata("orphan-id", "gone.txt", "text/plain", 7);
		writeSidecar(orphan.id(), orphan);

		newImporter().importSidecars();

		assertThat(repository.count()).isZero();
	}

	@Test
	void skippedSidecarIsLogged() throws IOException {
		Files.writeString(tempDir.resolve("broken" + FileStorageMessages.META_FILE_SUFFIX), "not valid json");

		try (LogCapture logs = LogCapture.of(LegacyMetadataImporter.class)) {
			newImporter().importSidecars();

			assertThat(logs.warningsAndWorse()).anySatisfy(message -> assertThat(message).contains("broken"));
		}
	}

	@Test
	void sidecarWrittenByAPlainMapperIsStillReadable() throws IOException {
		FileMetadata metadata = new FileMetadata("pinned-id", "pinned.txt", "text/plain", 4);
		writeSidecar(metadata.id(), metadata);
		writePayload(metadata.id());

		newImporter().importSidecars();

		assertThat(row(metadata.id()).toMetadata()).isEqualTo(metadata);
	}

	@Test
	void nothingOnDiskIsDeletedOrRenamedByTheImport() throws IOException {
		FileMetadata preExisting = new FileMetadata("kept-id", "old.txt", "text/plain", 3);
		writeSidecar(preExisting.id(), preExisting);
		writePayload(preExisting.id());

		newImporter().importSidecars();

		assertThat(tempDir.resolve(preExisting.id() + FileStorageMessages.META_FILE_SUFFIX)).isRegularFile();
		assertThat(tempDir.resolve(preExisting.id() + FileStorageMessages.BIN_FILE_SUFFIX)).isRegularFile();
	}
}
