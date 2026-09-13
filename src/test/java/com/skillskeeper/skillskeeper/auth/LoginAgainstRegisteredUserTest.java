package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

/**
 * Logging in now means something: the credentials have to belong to an account. The base class
 * registers {@link #TEST_USERNAME} before every test, so these are a real user's credentials.
 */
class LoginAgainstRegisteredUserTest extends AuthenticatedApiTest {

	private MockHttpServletResponse login(String username, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CREDENTIALS_TEMPLATE.formatted(username, password)))
				.andReturn().getResponse();
	}

	@Test
	void aRegisteredUserGetsAToken() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CREDENTIALS_TEMPLATE.formatted(TEST_USERNAME, TEST_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void theNameMayBeWrittenInAnotherCase() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CREDENTIALS_TEMPLATE.formatted(TEST_USERNAME.toUpperCase(), TEST_PASSWORD)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void theWrongPasswordIsRefused() throws Exception {
		assertThat(login(TEST_USERNAME, "Wr0ng!Password").getStatus()).isEqualTo(401);
	}

	@Test
	void anUnregisteredNameIsRefused() throws Exception {
		assertThat(login("nobody-at-all", TEST_PASSWORD).getStatus()).isEqualTo(401);
	}

	/**
	 * The two refusals have to be indistinguishable, or the difference between them tells an
	 * attacker which usernames exist.
	 */
	@Test
	void theTwoRefusalsCannotBeToldApart() throws Exception {
		MockHttpServletResponse wrongPassword = login(TEST_USERNAME, "Wr0ng!Password");
		MockHttpServletResponse unknownName = login("nobody-at-all", TEST_PASSWORD);

		assertThat(unknownName.getStatus()).isEqualTo(wrongPassword.getStatus());
		assertThat(unknownName.getContentAsString()).isEqualTo(wrongPassword.getContentAsString());
	}

	@Test
	void neitherRefusalEchoesTheSubmittedPassword() throws Exception {
		String submitted = "Wr0ng!Password";

		assertThat(login(TEST_USERNAME, submitted).getContentAsString()).doesNotContain(submitted);
		assertThat(login("nobody-at-all", submitted).getContentAsString()).doesNotContain(submitted);
	}

	/**
	 * Registering does not hand out a token: that stays login's job alone.
	 */
	@Test
	void registeringDoesNotIssueAToken() throws Exception {
		mockMvc.perform(post("/api/auth/register")
						.contentType(MediaType.APPLICATION_JSON)
						.content(CREDENTIALS_TEMPLATE.formatted("freshly-made-user", TEST_PASSWORD)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").exists())
				.andExpect(jsonPath("$.username").value("freshly-made-user"))
				.andExpect(jsonPath("$.token").doesNotExist());
	}
}
