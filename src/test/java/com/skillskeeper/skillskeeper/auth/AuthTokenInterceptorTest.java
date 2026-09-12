package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

class AuthTokenInterceptorTest {

	private final TokenService tokenService = new TokenService();
	private final AuthTokenInterceptor interceptor = new AuthTokenInterceptor(tokenService);
	private final HttpServletResponse response = mock(HttpServletResponse.class);

	@Test
	void missingAuthorizationHeaderThrows() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Authorization")).thenReturn(null);

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void malformedAuthorizationHeaderThrows() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Authorization")).thenReturn("Token abc123");

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void unrecognizedTokenThrows() {
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Authorization")).thenReturn("Bearer does-not-exist");

		assertThatThrownBy(() -> interceptor.preHandle(request, response, new Object()))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	@Test
	void validTokenAllowsRequestThrough() {
		String token = tokenService.issueToken();
		HttpServletRequest request = mock(HttpServletRequest.class);
		when(request.getHeader("Authorization")).thenReturn("Bearer " + token);

		assertThat(interceptor.preHandle(request, response, new Object())).isTrue();
	}
}
