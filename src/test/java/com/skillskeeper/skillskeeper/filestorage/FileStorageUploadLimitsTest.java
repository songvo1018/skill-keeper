package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

/**
 * Uses a real embedded server (not MockMvc) because the max-file-size limit is
 * enforced by the servlet container's multipart parsing, which MockMvc's mock
 * request never goes through.
 *
 * <p>Deliberately sends no token: the container rejects the oversized body while parsing the
 * multipart request, before the handler — and therefore before the auth interceptor — is reached. The
 * `413` here is the container's, so the ordering this test documents (413 ahead of 401) is expected
 * rather than a gap in token enforcement.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileStorageUploadLimitsTest {

	@TempDir
	static Path tempDir;

	@Autowired
	private TestRestTemplate restTemplate;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
	}

	@Test
	void uploadTooLargeFileReturnsPayloadTooLarge() {
		ByteArrayResource resource = new ByteArrayResource(new byte[11 * 1024 * 1024]) {
			@Override
			public String getFilename() {
				return "big.bin";
			}
		};

		MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
		body.add("file", resource);

		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.MULTIPART_FORM_DATA);

		ResponseEntity<String> response = restTemplate.postForEntity("/api/files",
				new HttpEntity<>(body, headers), String.class);

		assertThat(response.getStatusCode().value()).isEqualTo(413);
	}
}
