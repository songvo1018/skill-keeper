package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

class AuthRegistrationEndpointTest extends AuthenticatedApiTest {

	private static final String STRONG_PASSWORD = "An0ther!Pass";

	private static final String WEAK_PASSWORD = "abc";

	private static final String NEW_USER_TEMPLATE = "newcomer-%s";

	/**
	 * A name no other test in this class uses. The database is per class, not per test, so a fixed
	 * name would already exist by the second test and every {@code 201} expected here would be a
	 * {@code 409} depending on the order the tests happened to run in.
	 */
	private String newUser;

	@BeforeEach
	void chooseAName(TestInfo testInfo) {
		newUser = NEW_USER_TEMPLATE.formatted(testInfo.getTestMethod().orElseThrow().getName());
	}

	private ResultActions register(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/register")
				.contentType(MediaType.APPLICATION_JSON)
				.content(CREDENTIALS_TEMPLATE.formatted(username, password)));
	}

	@Test
	void registeringReturnsCreatedWithTheAccountsIdAndName() throws Exception {
		register(newUser, STRONG_PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNotEmpty())
				.andExpect(jsonPath("$.username").value(newUser));
	}

	@Test
	void theResponseCarriesNothingOfThePassword() throws Exception {
		String body = register(newUser, STRONG_PASSWORD)
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).doesNotContain(STRONG_PASSWORD);
	}

	/**
	 * No {@code Authorization} header is sent here: registration has to work for someone who has no
	 * account yet and therefore cannot hold a token.
	 */
	@Test
	void registeringNeedsNoToken() throws Exception {
		register(newUser, STRONG_PASSWORD).andExpect(status().isCreated());
	}

	@Test
	void theNameIsStoredWithTheCaseItWasGivenIn() throws Exception {
		register("MiXeD-" + newUser, STRONG_PASSWORD)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.username").value("MiXeD-" + newUser));
	}

	@Test
	void aNameThatIsAlreadyTakenIsRejected() throws Exception {
		register(newUser, STRONG_PASSWORD).andExpect(status().isCreated());

		register(newUser, STRONG_PASSWORD).andExpect(status().isConflict());
	}

	@Test
	void aNameTakenInAnotherLetterCaseIsRejected() throws Exception {
		register(newUser, STRONG_PASSWORD).andExpect(status().isCreated());

		register(newUser.toUpperCase(), STRONG_PASSWORD).andExpect(status().isConflict());
	}

	/**
	 * The account the base class registers is proof the conflict is decided by what is stored, not
	 * only by what this test just created.
	 */
	@Test
	void theAccountTheSuiteLogsInAsIsAlreadyTaken() throws Exception {
		register(TEST_USERNAME, STRONG_PASSWORD).andExpect(status().isConflict());
	}

	@Test
	void aBlankNameIsRejected() throws Exception {
		register("", STRONG_PASSWORD).andExpect(status().isBadRequest());
	}

	@Test
	void aMissingPasswordIsRejected() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"someone\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void anOverlongNameIsRejected() throws Exception {
		register("n".repeat(101), STRONG_PASSWORD).andExpect(status().isBadRequest());
	}

	@Test
	void aWeakPasswordIsRejectedAndEveryBrokenRuleIsNamed() throws Exception {
		String body = register(newUser, WEAK_PASSWORD)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.violations").isArray())
				.andReturn().getResponse().getContentAsString();

		assertThat(body)
				.contains(AuthMessages.PASSWORD_TOO_SHORT_TEMPLATE.formatted(AuthMessages.MIN_PASSWORD_LENGTH))
				.contains(AuthMessages.PASSWORD_NEEDS_UPPERCASE)
				.contains(AuthMessages.PASSWORD_NEEDS_DIGIT)
				.contains(AuthMessages.PASSWORD_NEEDS_SPECIAL)
				.doesNotContain(WEAK_PASSWORD);
	}

	@Test
	void aRejectedRegistrationCreatesNothing() throws Exception {
		register(newUser, WEAK_PASSWORD).andExpect(status().isBadRequest());

		register(newUser, STRONG_PASSWORD).andExpect(status().isCreated());
	}
}
