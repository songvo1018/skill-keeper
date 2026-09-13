package com.skillskeeper.skillskeeper.filestorage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.jayway.jsonpath.JsonPath;

/**
 * Uses a real server and a hand-built multipart body so the filename's bytes on the wire are known
 * exactly. A {@code curl} check from a Windows shell cannot establish this: the console may re-encode
 * the argument before the client ever sees it, and MockMvc hands the filename over as a Java string,
 * so neither shows how the server decodes a part header.
 */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
class FileStorageMultipartEncodingTest {

	private static final String BOUNDARY = "test-boundary-9f1c";

	private static final String CYRILLIC_FILENAME = "отчёт квартал.pdf";

	@TempDir
	static Path tempDir;

	@Autowired
	private TestRestTemplate restTemplate;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
	}

	private String token() {
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		ResponseEntity<String> response = this.restTemplate.postForEntity("/api/auth/login",
				new HttpEntity<>("{\"username\":\"alice\",\"password\":\"secret\"}", headers), String.class);
		return JsonPath.read(response.getBody(), "$.token");
	}

	/**
	 * Writes the part header as raw UTF-8, exactly as a client that does not announce a charset would.
	 */
	private static byte[] multipartBody(String filename, String contentType, byte[] content) throws IOException {
		ByteArrayOutputStream body = new ByteArrayOutputStream();
		String header = "--" + BOUNDARY + "\r\n"
				+ "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
				+ "Content-Type: " + contentType + "\r\n\r\n";
		body.write(header.getBytes(StandardCharsets.UTF_8));
		body.write(content);
		body.write(("\r\n--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
		return body.toByteArray();
	}

	private ResponseEntity<String> upload(String filename) throws IOException {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.CONTENT_TYPE, "multipart/form-data; boundary=" + BOUNDARY);
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token());
		byte[] body = multipartBody(filename, "application/pdf", "payload".getBytes(StandardCharsets.UTF_8));
		return this.restTemplate.postForEntity("/api/files", new HttpEntity<>(body, headers), String.class);
	}

	@Test
	void nonAsciiFilenameSurvivesARealMultipartUpload() throws IOException {
		ResponseEntity<String> response = upload(CYRILLIC_FILENAME);

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(JsonPath.<String>read(response.getBody(), "$.originalFilename")).isEqualTo(CYRILLIC_FILENAME);
	}

	@Test
	void nonAsciiFilenameComesBackOnTheDownload() throws IOException {
		String id = JsonPath.read(upload(CYRILLIC_FILENAME).getBody(), "$.id");

		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token());
		ResponseEntity<byte[]> download = this.restTemplate.exchange("/api/files/{id}",
				org.springframework.http.HttpMethod.GET, new HttpEntity<>(headers), byte[].class, id);

		assertThat(download.getStatusCode().value()).isEqualTo(200);
		String disposition = download.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
		assertThat(disposition).isNotNull();
		assertThat(download.getHeaders().getContentDisposition().getFilename()).isEqualTo(CYRILLIC_FILENAME);
	}

	@Test
	void quoteInFilenameIsPreservedThroughARealUpload() throws IOException {
		ResponseEntity<String> response = upload("my'file.pdf");

		assertThat(response.getStatusCode().value()).isEqualTo(201);
		assertThat(JsonPath.<String>read(response.getBody(), "$.originalFilename")).isEqualTo("my'file.pdf");
	}
}
