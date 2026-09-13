package com.skillskeeper.skillskeeper.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;

import com.jayway.jsonpath.JsonPath;
import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

/**
 * Covers which endpoints require a token; the shape of the 401 itself is asserted once in
 * {@link AuthEnforcementTest}.
 */
class ExistingEndpointsRequireTokenTest extends AuthenticatedApiTest {

	@Test
	void uploadWithoutTokenIsUnauthorized() throws Exception {
		MockMultipartFile upload = new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes());
		mockMvc.perform(multipart("/api/files").file(upload)).andExpect(status().isUnauthorized());
	}

	@Test
	void listWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/files")).andExpect(status().isUnauthorized());
	}

	@Test
	void downloadWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/files/{id}", "some-id")).andExpect(status().isUnauthorized());
	}

	@Test
	void listWithValidTokenSucceeds() throws Exception {
		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader()))
				.andExpect(status().isOk());
	}

	@Test
	void uploadListAndDownloadWithValidTokenSucceedAsBefore() throws Exception {
		String authHeader = authHeader();
		MockMultipartFile upload = new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes());

		String uploadBody = mockMvc.perform(multipart("/api/files").file(upload)
						.header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(uploadBody, "$.id");

		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/files/{id}", id).header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isOk())
				.andExpect(content().bytes("hello".getBytes()));
	}
}
