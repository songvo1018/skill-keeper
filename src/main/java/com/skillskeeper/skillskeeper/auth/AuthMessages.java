package com.skillskeeper.skillskeeper.auth;

final class AuthMessages {

	static final String MISSING_OR_INVALID_TOKEN = "Missing or invalid authentication token";

	static final String INVALID_CREDENTIALS = "Invalid username or password";

	static final String NO_REAL_CREDENTIALS_VERIFIER = "No real CredentialsVerifier is available: the production "
			+ "profile must not fall back to AlwaysApprovingCredentialsVerifier, which approves any credentials";

	static final String BEARER_PREFIX = "Bearer ";

	/**
	 * The {@code WWW-Authenticate} challenge RFC 7235 requires on every 401.
	 */
	static final String BEARER_CHALLENGE = "Bearer";

	private AuthMessages() {
	}
}
