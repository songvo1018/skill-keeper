package com.skillskeeper.skillskeeper.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The bounds are {@link LoginRequest}'s on purpose: a name or password that registration accepted
 * but login would reject as too long would create an account nobody could use.
 */
public record RegisterRequest(@NotBlank @Size(max = LoginRequest.MAX_USERNAME_LENGTH) String username,
		@NotBlank @Size(max = LoginRequest.MAX_PASSWORD_LENGTH) String password) {

	private static final String TO_STRING_TEMPLATE = "RegisterRequest[username=%s, password=***]";

	/**
	 * Overridden because the generated form prints the password, which would reach the logs through a
	 * binding result the first time anyone raises the log level.
	 */
	@Override
	public String toString() {
		return TO_STRING_TEMPLATE.formatted(username);
	}
}
