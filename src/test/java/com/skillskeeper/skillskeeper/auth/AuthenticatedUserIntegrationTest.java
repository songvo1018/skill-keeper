package com.skillskeeper.skillskeeper.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.skillskeeper.skillskeeper.auth.web.AuthenticatedUser;

import com.jayway.jsonpath.JsonPath;
import com.skillskeeper.skillskeeper.support.AuthenticatedApiTest;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Proves the username travels from login all the way into an endpoint's own handling — the enabler
 * that makes per-caller rules possible later, through a throwaway endpoint because no production one
 * reads the caller's identity yet.
 */
class AuthenticatedUserIntegrationTest extends AuthenticatedApiTest {

	@TestConfiguration
	static class WhoAmIConfiguration {

		/**
		 * Registered by being a nested component of the configuration; declaring it as a {@code @Bean}
		 * as well would map the same handler twice.
		 */
		@RestController
		static class WhoAmIController {

			@GetMapping("/api/test/whoami")
			String whoami(HttpServletRequest request) {
				return AuthenticatedUser.username(request);
			}
		}
	}

	private String tokenFor(String username) throws Exception {
		String responseBody = mockMvc.perform(post("/api/auth/login")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"username\":\"" + username + "\",\"password\":\"secret\"}"))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		return JsonPath.read(responseBody, "$.token");
	}

	@Test
	void handlingSeesTheUsernameThatLoggedIn() throws Exception {
		mockMvc.perform(get("/api/test/whoami")
						.header(HttpHeaders.AUTHORIZATION, BEARER + tokenFor("bob")))
				.andExpect(status().isOk())
				.andExpect(content().string("bob"));
	}

	@Test
	void eachTokenCarriesItsOwnCaller() throws Exception {
		String aliceToken = tokenFor("alice");
		String bobToken = tokenFor("bob");

		mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, BEARER + aliceToken))
				.andExpect(content().string("alice"));
		mockMvc.perform(get("/api/test/whoami").header(HttpHeaders.AUTHORIZATION, BEARER + bobToken))
				.andExpect(content().string("bob"));
	}

	@Test
	void theThrowawayEndpointIsItselfProtected() throws Exception {
		mockMvc.perform(get("/api/test/whoami")).andExpect(status().isUnauthorized());
	}
}
