package com.skillskeeper.skillskeeper.filestorage.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;
import com.skillskeeper.skillskeeper.filestorage.exception.FileStorageException;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.model.FileStorageProperties;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRepository;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRow;

/**
 * Moves metadata written before the database existed - the JSON sidecars beside each stored file -
 * into {@code stored_file}, so those files stay in the listing.
 *
 * <p>Runs as an {@link ApplicationRunner} rather than from {@code @PostConstruct}, because it must
 * see the schema: a runner is invoked once the context is up, and therefore once Flyway has
 * finished.
 *
 * <p>Nothing on disk is deleted or renamed. Re-running is harmless because a sidecar whose id is
 * already a row is skipped. That does mean a row deleted deliberately would come back while its
 * sidecar is still there; there is no way to delete a file today, and this is the first thing to
 * revisit when there is one.
 */
@Component
class LegacyMetadataImporter implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(LegacyMetadataImporter.class);

	private final Path baseDir;
	private final FileMetadataStore metadataStore;
	private final StoredFileRepository repository;

	LegacyMetadataImporter(FileStorageProperties properties, FileMetadataStore metadataStore,
			StoredFileRepository repository) {
		this.baseDir = properties.baseDir().toAbsolutePath().normalize();
		this.metadataStore = metadataStore;
		this.repository = repository;
	}

	@Override
	public void run(ApplicationArguments args) {
		importSidecars();
	}

	void importSidecars() {
		int imported = 0;
		int skipped = 0;

		String metaGlob = "*" + FileStorageMessages.META_FILE_SUFFIX;
		try (DirectoryStream<Path> metaFiles = Files.newDirectoryStream(baseDir, metaGlob)) {
			for (Path metaPath : metaFiles) {
				if (importSidecar(metaPath)) {
					imported++;
				}
				else {
					skipped++;
				}
			}
		}
		catch (IOException e) {
			throw new FileStorageException(FileStorageMessages.DIR_SCAN_FAILED_PREFIX + baseDir, e);
		}

		if (imported > 0 || skipped > 0) {
			log.info("Imported metadata for {} stored file(s) from disk, skipped {}", imported, skipped);
		}
	}

	/**
	 * Imports one sidecar, or skips it. Anything a crash could have left behind - an unreadable
	 * file, valid JSON that does not describe a file, a sidecar whose content is gone - is logged
	 * and excluded rather than allowed to abort startup.
	 *
	 * @return whether a row was inserted
	 */
	private boolean importSidecar(Path metaPath) {
		String fileName = metaPath.getFileName().toString();
		String id = fileName.substring(0, fileName.length() - FileStorageMessages.META_FILE_SUFFIX.length());

		FileMetadata metadata;
		try {
			metadata = metadataStore.read(metaPath);
		}
		catch (RuntimeException e) {
			log.warn("Skipping unreadable metadata record {}: {}", fileName, e.getMessage());
			return false;
		}

		if (metadata.id() == null || metadata.id().isBlank() || !metadata.id().equals(id)) {
			log.warn("Skipping metadata record {}: it records id {}, which does not identify that file", fileName,
					metadata.id());
			return false;
		}

		if (!Files.isRegularFile(payloadPath(id))) {
			log.warn("Skipping metadata record {}: no stored content accompanies it", fileName);
			return false;
		}

		if (repository.existsById(id)) {
			return false;
		}

		repository.insert(StoredFileRow.forInsert(metadata));
		return true;
	}

	private Path payloadPath(String id) {
		return baseDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX);
	}
}
