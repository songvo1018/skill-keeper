package com.skillskeeper.skillskeeper.auth.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(@NotBlank @Size(max = LoginRequest.MAX_USERNAME_LENGTH) String username,
		@NotBlank @Size(max = LoginRequest.MAX_PASSWORD_LENGTH) String password) {

	public static final int MAX_USERNAME_LENGTH = 100;

	public static final int MAX_PASSWORD_LENGTH = 200;

	private static final String TO_STRING_TEMPLATE = "LoginRequest[username=%s, password=***]";

	/**
	 * Overridden because the generated form prints the password, which would reach the logs through a
	 * binding result the first time anyone raises the log level.
	 */
	@Override
	public String toString() {
		return TO_STRING_TEMPLATE.formatted(username);
	}
}
