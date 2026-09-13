package com.skillskeeper.skillskeeper.auth;

public final class AuthMessages {

	public static final String MISSING_OR_INVALID_TOKEN = "Missing or invalid authentication token";

	public static final String INVALID_CREDENTIALS = "Invalid username or password";

	public static final String NO_REAL_CREDENTIALS_VERIFIER = "No real CredentialsVerifier is available: the production "
			+ "profile must not fall back to AlwaysApprovingCredentialsVerifier, which approves any credentials";

	public static final String BEARER_PREFIX = "Bearer ";

	/**
	 * The {@code WWW-Authenticate} challenge RFC 7235 requires on every 401.
	 */
	public static final String BEARER_CHALLENGE = "Bearer";

	private AuthMessages() {
	}
}
