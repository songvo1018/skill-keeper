package com.skillskeeper.skillskeeper.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
class ExistingEndpointsRequireTokenTest {

	@TempDir
	static Path tempDir;

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
	}

	private String login() throws Exception {
		String responseBody = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"alice\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(responseBody, "$.token");
	}

	@Test
	void helloWithoutTokenIsUnauthorized() throws Exception {
		mockMvc.perform(get("/api/hello")).andExpect(status().isUnauthorized());
	}

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
	void helloWithValidTokenSucceedsAsBefore() throws Exception {
		String token = login();

		mockMvc.perform(get("/api/hello").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().string("Hello, Skills Keeper!"));
	}

	@Test
	void uploadListAndDownloadWithValidTokenSucceedAsBefore() throws Exception {
		String token = login();
		MockMultipartFile upload = new MockMultipartFile("file", "report.txt", "text/plain", "hello".getBytes());

		String uploadBody = mockMvc.perform(multipart("/api/files").file(upload)
						.header("Authorization", "Bearer " + token))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();
		String id = JsonPath.read(uploadBody, "$.id");

		mockMvc.perform(get("/api/files").header("Authorization", "Bearer " + token))
				.andExpect(status().isOk());

		mockMvc.perform(get("/api/files/{id}", id).header("Authorization", "Bearer " + token))
				.andExpect(status().isOk())
				.andExpect(content().bytes("hello".getBytes()));
	}
}
