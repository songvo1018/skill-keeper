package com.skillskeeper.skillskeeper.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

class AuthLoginEndpointTest extends AuthenticatedApiTest {

	@Test
	void loginWithNonBlankCredentialsReturnsToken() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"alice\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.token").isNotEmpty());
	}

	@Test
	void loginWithBlankUsernameReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"\",\"password\":\"secret\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void loginWithMissingPasswordReturnsBadRequest() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"alice\"}"))
				.andExpect(status().isBadRequest());
	}

	@Test
	void rejectedLoginResponseDoesNotEchoTheSubmittedPassword() throws Exception {
		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"\",\"password\":\"hunter2\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(result -> {
					String body = result.getResponse().getContentAsString();
					if (body.contains("hunter2")) {
						throw new AssertionError("Response disclosed the submitted password: " + body);
					}
				});
	}
}
