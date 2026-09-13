package com.skillskeeper.skillskeeper.auth;

import static org.hamcrest.Matchers.equalToIgnoringCase;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

/**
 * Owns the shape of a 401: the per-endpoint coverage of which endpoints require a token lives in
 * {@link ExistingEndpointsRequireTokenTest}.
 */
class AuthEnforcementTest extends AuthenticatedApiTest {

	@Test
	void protectedEndpointWithoutTokenReturnsUnauthorizedProblemDetail() throws Exception {
		mockMvc.perform(get("/api/files"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.status").value(401))
				.andExpect(jsonPath("$.detail").value("Missing or invalid authentication token"));
	}

	@Test
	void missingTokenRejectionCarriesABearerChallenge() throws Exception {
		mockMvc.perform(get("/api/files"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, equalToIgnoringCase("Bearer")));
	}

	@Test
	void unrecognizedTokenRejectionCarriesABearerChallenge() throws Exception {
		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, "Bearer does-not-exist"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, equalToIgnoringCase("Bearer")));
	}

	@Test
	void lowercaseSchemeNameIsAccepted() throws Exception {
		mockMvc.perform(get("/api/files").header(HttpHeaders.AUTHORIZATION, "bearer " + token()))
				.andExpect(status().isOk());
	}

	@Test
	void overLongCredentialsAreRejectedWithoutIssuingAToken() throws Exception {
		String overLongUsername = "x".repeat(LoginRequest.MAX_USERNAME_LENGTH + 1);

		mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"" + overLongUsername + "\",\"password\":\"secret\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.token").doesNotExist());
	}
}
