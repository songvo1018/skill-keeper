package com.skillskeeper.skillskeeper.auth.web;

import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Builds a request that looks the way one does after {@link AuthTokenInterceptor} has accepted its
 * token, for unit tests that call a controller method directly instead of going through MockMvc.
 *
 * <p>Lives in this package, rather than beside the other test support, because the attribute the
 * interceptor writes is package-private on {@link AuthenticatedUser} - which is the point: no
 * production code outside this package can invent an authenticated request.
 */
public final class AuthenticatedRequests {

	private AuthenticatedRequests() {
	}

	public static MockHttpServletRequest forUser(String username) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, username);
		return request;
	}
}
