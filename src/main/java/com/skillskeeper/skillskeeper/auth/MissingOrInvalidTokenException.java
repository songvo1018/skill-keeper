package com.skillskeeper.skillskeeper.auth;

public class MissingOrInvalidTokenException extends RuntimeException {

	public MissingOrInvalidTokenException() {
		super(AuthMessages.MISSING_OR_INVALID_TOKEN);
	}
}
