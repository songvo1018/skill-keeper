package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRepository;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

import tools.jackson.databind.ObjectMapper;

/**
 * A sidecar holding valid-but-empty JSON used to abort startup: the record parsed, and its null id
 * then reached the index. The unit test for the import cannot prove startup is unaffected, so this
 * plants such a file before the application context is built.
 *
 * <p>Now that metadata lives in the database, this also covers the import end to end: the records
 * are picked up by the runner that executes once the context is up, so what the table holds is what
 * the import made of them. An imported row carries no owner - the sidecar format records none - so
 * it reaches the table but nobody's listing.
 */
class FileStorageStartupTest extends AuthenticatedApiTest {

	@BeforeAll
	static void plantUnusableSidecars() throws IOException {
		Path storageDir = storageDir();
		new ObjectMapper().writeValue(
				storageDir.resolve("empty-json" + FileStorageMessages.META_FILE_SUFFIX).toFile(),
				Map.of());
		Files.writeString(storageDir.resolve("not-json" + FileStorageMessages.META_FILE_SUFFIX), "}{");

		FileMetadata usable = new FileMetadata("usable-id", "keep.txt", "text/plain", 4);
		new ObjectMapper().writeValue(
				storageDir.resolve(usable.id() + FileStorageMessages.META_FILE_SUFFIX).toFile(), usable);
		Files.writeString(storageDir.resolve(usable.id() + FileStorageMessages.BIN_FILE_SUFFIX), "keep");
	}

	@Autowired
	private StoredFileRepository repository;

	@Test
	void applicationStartsAndImportsOnlyTheUsableFile() {
		assertThat(repository.findById("usable-id")).isPresent();
		assertThat(repository.findById("empty-json")).isEmpty();
		assertThat(repository.findById("not-json")).isEmpty();
	}

	@Test
	void importedFileHasNoOwnerAndSoAppearsInNobodysListing() throws Exception {
		assertThat(repository.findById("usable-id").orElseThrow().ownerUsername()).isNull();

		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	void importedFileCannotBeDownloadedWithoutAnOwner() throws Exception {
		mockMvc.perform(get("/api/files/{id}", "usable-id").header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isNotFound());
	}
}
