package com.skillskeeper.skillskeeper.filestorage;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

import tools.jackson.databind.ObjectMapper;

/**
 * A sidecar holding valid-but-empty JSON used to abort startup: the record parsed, and its null id
 * then reached the index. The unit test for the scan cannot prove startup is unaffected, so this
 * plants such a file before the application context is built.
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

	@Test
	void applicationStartsAndListsOnlyTheUsableFile() throws Exception {
		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value("usable-id"));
	}
}
