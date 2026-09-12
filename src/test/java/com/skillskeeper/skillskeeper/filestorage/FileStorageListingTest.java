package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class FileStorageListingTest {

	@TempDir
	static Path tempDir;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
	}

	@Test
	void listStartsEmptyThenReflectsUploadedFiles() throws Exception {
		mockMvc.perform(get("/api/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		MockMultipartFile first = new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes());
		MockMultipartFile second = new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes());
		mockMvc.perform(multipart("/api/files").file(first)).andExpect(status().isCreated());
		mockMvc.perform(multipart("/api/files").file(second)).andExpect(status().isCreated());

		String responseBody = mockMvc.perform(get("/api/files"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andReturn().getResponse().getContentAsString();

		List<String> filenames = JsonPath.read(responseBody, "$[*].originalFilename");
		assertThat(filenames).containsExactlyInAnyOrder("a.txt", "b.txt");
	}
}
