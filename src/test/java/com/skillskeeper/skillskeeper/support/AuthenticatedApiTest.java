package com.skillskeeper.skillskeeper.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.annotation.DirtiesContext.ClassMode;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

/**
 * Base for tests that drive the API through {@link MockMvc}.
 *
 * <p>Binds the storage directory to a temporary one for every subclass: without it a test writes
 * into {@code ./data/files} under whatever directory the build ran from.
 *
 * <p>The context is discarded after each class because every subclass inherits the same
 * {@code @DynamicPropertySource}, which makes their context configurations identical and therefore
 * cacheable as one. Reusing it would leave the storage service bound to the first subclass's
 * temporary directory, which JUnit deletes as soon as that class finishes.
 */
@DirtiesContext(classMode = ClassMode.AFTER_CLASS)
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AuthenticatedApiTest {

	protected static final String BEARER = "Bearer ";

	@TempDir
	static Path storageDir;

	@Autowired
	protected MockMvc mockMvc;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> storageDir.toString());
	}

	protected static Path storageDir() {
		return storageDir;
	}

	/**
	 * @return a ready-to-send {@code Authorization} header value for a freshly issued token
	 */
	protected String authHeader() throws Exception {
		return BEARER + token();
	}

	protected String token() throws Exception {
		String responseBody = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"alice\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(responseBody, "$.token");
	}
}
