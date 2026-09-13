package com.skillskeeper.skillskeeper.auth.web;

import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Reads the username the token interceptor resolved for the current request.
 *
 * <p>Deliberately only an accessor: it carries identity to request handling and nothing more. The
 * first requirement that one caller may do something another may not is the point to adopt Spring
 * Security, rather than to grow this into a home-made security context.
 */
public final class AuthenticatedUser {

	static final String REQUEST_ATTRIBUTE = "com.skillskeeper.skillskeeper.auth.username";

	private AuthenticatedUser() {
	}

	/**
	 * @throws MissingOrInvalidTokenException if the request was not authenticated
	 */
	public static String username(HttpServletRequest request) {
		if (request.getAttribute(REQUEST_ATTRIBUTE) instanceof String username) {
			return username;
		}
		throw new MissingOrInvalidTokenException();
	}
}
