package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;
import com.skillskeeper.skillskeeper.auth.model.AuthTokenProperties;
import com.skillskeeper.skillskeeper.auth.service.TokenService;
import com.skillskeeper.skillskeeper.auth.web.AuthTokenInterceptor;
import com.skillskeeper.skillskeeper.auth.web.AuthenticatedUser;

import com.skillskeeper.skillskeeper.support.LogCapture;

import jakarta.servlet.http.HttpServletResponse;

class AuthTokenInterceptorTest {

	private final TokenService tokenService = new TokenService(
			new AuthTokenProperties(Duration.ofMinutes(30), 100), Clock.systemUTC());
	private final AuthTokenInterceptor interceptor = new AuthTokenInterceptor(this.tokenService);
	private final HttpServletResponse response = mock(HttpServletResponse.class);

	private static MockHttpServletRequest requestWithAuthorization(String headerValue) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/files");
		if (headerValue != null) {
			request.addHeader("Authorization", headerValue);
		}
		return request;
	}

	@Test
	void missingAuthorizationHeaderThrows() {
		MockHttpServletRequest request = requestWithAuthorization(null);

		assertThatThrownBy(() -> this.interceptor.preHandle(request, this.response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void malformedAuthorizationHeaderThrows() {
		MockHttpServletRequest request = requestWithAuthorization("Token abc123");

		assertThatThrownBy(() -> this.interceptor.preHandle(request, this.response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void schemeWithoutATokenThrows() {
		MockHttpServletRequest request = requestWithAuthorization("Bearer ");

		assertThatThrownBy(() -> this.interceptor.preHandle(request, this.response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void unrecognizedTokenThrows() {
		MockHttpServletRequest request = requestWithAuthorization("Bearer does-not-exist");

		assertThatThrownBy(() -> this.interceptor.preHandle(request, this.response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void validTokenAllowsRequestThrough() {
		String token = this.tokenService.issueToken("alice");
		MockHttpServletRequest request = requestWithAuthorization("Bearer " + token);

		assertThat(this.interceptor.preHandle(request, this.response, new Object())).isTrue();
	}

	// --- Scheme name is case-insensitive per RFC 7235 (Finding 9) ---

	@Test
	void schemeNameIsAcceptedInAnyCase() {
		String token = this.tokenService.issueToken("alice");

		for (String scheme : new String[] { "Bearer", "bearer", "BEARER", "BeArEr" }) {
			MockHttpServletRequest request = requestWithAuthorization(scheme + " " + token);

			assertThat(this.interceptor.preHandle(request, this.response, new Object())).isTrue();
		}
	}

	@Test
	void bareTokenWithoutASchemeIsStillRejected() {
		String token = this.tokenService.issueToken("alice");
		MockHttpServletRequest request = requestWithAuthorization(token);

		assertThatThrownBy(() -> this.interceptor.preHandle(request, this.response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	// --- Caller identity reaches request handling (Finding 8) ---

	@Test
	void acceptedRequestCarriesTheAuthenticatedUsername() {
		String token = this.tokenService.issueToken("alice");
		MockHttpServletRequest request = requestWithAuthorization("Bearer " + token);

		this.interceptor.preHandle(request, this.response, new Object());

		assertThat(AuthenticatedUser.username(request)).isEqualTo("alice");
	}

	@Test
	void unauthenticatedRequestHasNoUsername() {
		MockHttpServletRequest request = requestWithAuthorization(null);

		assertThatThrownBy(() -> AuthenticatedUser.username(request))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	// --- Rejection is recorded without leaking the token (Finding 6) ---

	@Test
	void rejectionIsLoggedWithoutThePresentedToken() {
		MockHttpServletRequest request = requestWithAuthorization("Bearer super-secret-token");

		try (LogCapture logs = LogCapture.of(AuthTokenInterceptor.class)) {
			assertThatThrownBy(() -> this.interceptor.preHandle(request, this.response, new Object()))
					.isInstanceOf(MissingOrInvalidTokenException.class);

			assertThat(logs.warningsAndWorse()).isNotEmpty()
					.allSatisfy(message -> assertThat(message).doesNotContain("super-secret-token"));
		}
	}
}
