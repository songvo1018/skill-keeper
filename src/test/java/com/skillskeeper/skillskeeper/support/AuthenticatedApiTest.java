package com.skillskeeper.skillskeeper.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
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

	/**
	 * The account these tests log in as. The password has to satisfy the registration rules, so it
	 * is no longer the throwaway it was when any credentials were approved.
	 */
	protected static final String TEST_USERNAME = "alice";

	protected static final String TEST_PASSWORD = "Sup3rSecret!";

	protected static final String CREDENTIALS_TEMPLATE = "{\"username\":\"%s\",\"password\":\"%s\"}";

	@TempDir
	static Path storageDir;

	@Autowired
	protected MockMvc mockMvc;

	@DynamicPropertySource
	static void storageProperties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> storageDir.toString());
		PostgresTestContainer.registerProperties(registry);
		TestPasswordHashing.registerProperties(registry);
	}

	protected static Path storageDir() {
		return storageDir;
	}

	/**
	 * Registers the user the tests log in as, through the API rather than by writing to the table,
	 * so these tests take the same path a client does and a password that breaks the rules fails
	 * here instead of somewhere confusing.
	 *
	 * <p>Runs before every test, and each test class has a database of its own, so the first test
	 * of a class creates the account and the rest find it already there - which is what the
	 * {@code 409} allows for.
	 */
	@BeforeEach
	void registerTestUser() throws Exception {
		int status = mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CREDENTIALS_TEMPLATE.formatted(TEST_USERNAME, TEST_PASSWORD)))
				.andReturn().getResponse().getStatus();

		assertThat(status).isIn(HttpStatus.CREATED.value(), HttpStatus.CONFLICT.value());
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
						.content(CREDENTIALS_TEMPLATE.formatted(TEST_USERNAME, TEST_PASSWORD)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(responseBody, "$.token");
	}
}
