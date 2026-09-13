package com.skillskeeper.skillskeeper.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;

import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

/**
 * Drives expiry through configuration rather than an injected clock, so the configured lifetime is
 * shown to be what the running application actually enforces.
 */
@TestPropertySource(properties = "app.auth.token.time-to-live=1ms")
class TokenExpiryIntegrationTest extends AuthenticatedApiTest {

	@Test
	void tokenStopsWorkingOnceItsLifetimeElapsed() throws Exception {
		String authHeader = authHeader();

		Thread.sleep(20);

		mockMvc.perform(get("/api/hello").header(HttpHeaders.AUTHORIZATION, authHeader))
				.andExpect(status().isUnauthorized());
	}
}
