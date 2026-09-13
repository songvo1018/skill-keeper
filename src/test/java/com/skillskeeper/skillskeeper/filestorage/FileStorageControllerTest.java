package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

class FileStorageControllerTest extends AuthenticatedApiTest {

	private static long filesInStorageDir() throws IOException {
		try (var paths = Files.list(storageDir())) {
			return paths.count();
		}
	}

	private static List<Path> entriesBesideStorageDir() throws IOException {
		try (var paths = Files.list(storageDir().getParent())) {
			return paths.filter(path -> !path.equals(storageDir())).toList();
		}
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
				.andExpect(header().string("X-Content-Type-Options", "nosniff"))
				.andExpect(content().bytes("hello world".getBytes()));
	}

	@Test
	void uploadEmptyFileReturnsBadRequest() throws Exception {
		MockMultipartFile empty = new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0]);

		mockMvc.perform(multipart("/api/files").file(empty).header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void uploadWithMalformedContentTypeReturnsBadRequest() throws Exception {
		MockMultipartFile upload = new MockMultipartFile("file", "a.txt", "not a media type", "data".getBytes());

		mockMvc.perform(multipart("/api/files").file(upload).header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isBadRequest());
	}

	@Test
	void downloadUnknownIdReturnsNotFound() throws Exception {
		mockMvc.perform(get("/api/files/{id}", "does-not-exist").header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isNotFound());
	}

	@Test
	void nonAsciiFilenameSurvivesTheRoundTrip() throws Exception {
		String authHeader = authHeader();
		MockMultipartFile upload = new MockMultipartFile("file", "отчёт.pdf", "application/pdf", "pdf".getBytes());

		String responseBody = mockMvc.perform(multipart("/api/files").file(upload)
						.header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalFilename").value("отчёт.pdf"))
				.andReturn().getResponse().getContentAsString();

		mockMvc.perform(get("/api/files/{id}", JsonPath.<String>read(responseBody, "$.id"))
						.header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk())
				.andExpect(header().string("Content-Disposition", containsString("filename*=UTF-8''")));
	}

	/**
	 * Asserts the count of files the storage directory should hold and that nothing appeared beside
	 * it. Checking only that each entry's parent is the storage directory would pass unconditionally,
	 * since listing a directory cannot return anything else.
	 */
	@Test
	void uploadSanitizesPathTraversalFilenameAndStaysWithinBaseDir() throws Exception {
		long filesBefore = filesInStorageDir();
		List<Path> besideBefore = entriesBesideStorageDir();

		mockMvc.perform(multipart("/api/files")
						.file(new MockMultipartFile("file", "../../etc/passwd", "text/plain", "data".getBytes()))
						.header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.originalFilename").value("passwd"));

		assertThat(filesInStorageDir()).isEqualTo(filesBefore + 2);
		assertThat(entriesBesideStorageDir()).isEqualTo(besideBefore);
	}
}
