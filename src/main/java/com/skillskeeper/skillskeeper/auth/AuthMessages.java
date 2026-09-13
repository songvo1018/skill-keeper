package com.skillskeeper.skillskeeper.auth;

public final class AuthMessages {

	public static final String MISSING_OR_INVALID_TOKEN = "Missing or invalid authentication token";

	public static final String INVALID_CREDENTIALS = "Invalid username or password";

	public static final String USERNAME_ALREADY_TAKEN = "That username is already taken";

	/**
	 * The lower bound on a password. The upper bound is
	 * {@code LoginRequest.MAX_PASSWORD_LENGTH}, which bounds what the login endpoint will even
	 * accept - a password longer than that could be registered and then never used.
	 */
	public static final int MIN_PASSWORD_LENGTH = 8;

	public static final String WEAK_PASSWORD = "Password does not meet the required rules";

	public static final String PASSWORD_TOO_SHORT_TEMPLATE = "Password must be at least %d characters long";

	public static final String PASSWORD_NEEDS_UPPERCASE = "Password must contain an upper case letter";

	public static final String PASSWORD_NEEDS_LOWERCASE = "Password must contain a lower case letter";

	public static final String PASSWORD_NEEDS_DIGIT = "Password must contain a digit";

	public static final String PASSWORD_NEEDS_SPECIAL = "Password must contain a special character";

	/**
	 * Names the field of the error body that lists the password rules that were not met.
	 */
	public static final String VIOLATIONS_PROPERTY = "violations";

	public static final String BEARER_PREFIX = "Bearer ";

	/**
	 * The {@code WWW-Authenticate} challenge RFC 7235 requires on every 401.
	 */
	public static final String BEARER_CHALLENGE = "Bearer";

	private AuthMessages() {
	}
}
