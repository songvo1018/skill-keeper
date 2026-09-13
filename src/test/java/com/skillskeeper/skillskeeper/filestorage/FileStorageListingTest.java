package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

class FileStorageListingTest extends AuthenticatedApiTest {

	@Test
	void listStartsEmptyThenReflectsUploadedFiles() throws Exception {
		String authHeader = authHeader();

		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		MockMultipartFile first = new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes());
		MockMultipartFile second = new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes());
		mockMvc.perform(multipart("/api/files").file(first).header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isCreated());
		mockMvc.perform(multipart("/api/files").file(second).header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isCreated());

		String responseBody = mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andReturn().getResponse().getContentAsString();

		List<String> filenames = JsonPath.read(responseBody, "$[*].originalFilename");
		assertThat(filenames).containsExactlyInAnyOrder("a.txt", "b.txt");

		// Every id the listing reports must be retrievable, not merely present in the response.
		List<String> ids = JsonPath.read(responseBody, "$[*].id");
		for (String id : ids) {
			mockMvc.perform(get("/api/files/{id}", id).header(HttpHeaders.AUTHORIZATION, authHeader))
					.andExpect(status().isOk());
		}
	}
}
