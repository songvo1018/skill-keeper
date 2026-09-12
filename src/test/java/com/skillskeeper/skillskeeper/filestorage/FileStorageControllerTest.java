package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class FileStorageControllerTest {

	@TempDir
	static Path tempDir;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
	}

	private String authHeader() throws Exception {
		String responseBody = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"alice\",\"password\":\"secret\"}"))
				.andReturn().getResponse().getContentAsString();
		return "Bearer " + JsonPath.<String>read(responseBody, "$.token");
	}

	@Test
	void uploadThenDownloadRoundTrips() throws Exception {
		String authHeader = authHeader();
		MockMultipartFile upload = new MockMultipartFile("file", "report.txt", "text/plain",
				"hello world".getBytes());

		String responseBody = mockMvc.perform(multipart("/api/files").file(upload)
						.header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.originalFilename").value("report.txt"))
				.andExpect(jsonPath("$.contentType").value("text/plain"))
				.andExpect(jsonPath("$.size").value(11))
				.andReturn().getResponse().getContentAsString();

		String id = JsonPath.read(responseBody, "$.id");

		mockMvc.perform(get("/api/files/{id}", id).header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Type", "text/plain"))
				.andExpect(header().string("Content-Disposition", "attachment; filename=\"report.txt\""))
				.andExpect(content().bytes("hello world".getBytes()));
	}

	@Test
	void uploadEmptyFileReturnsBadRequest() throws Exception {
		MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

		mockMvc.perform(multipart("/api/files").file(empty).header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void downloadUnknownIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/files/{id}", "does-not-exist").header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isNotFound());
	}

	@Test
	void uploadSanitizesPathTraversalFilenameAndStaysWithinBaseDir() throws Exception {
		MockMultipartFile upload = new MockMultipartFile("file", "../../etc/passwd", "text/plain",
				"data".getBytes());

		mockMvc.perform(multipart("/api/files").file(upload).header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalFilename").value("passwd"));

		try (var paths = Files.list(tempDir)) {
			assertThat(paths).allMatch(path -> path.getParent().equals(tempDir));
		}
	}
}
