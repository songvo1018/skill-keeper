package com.skillskeeper.skillskeeper.auth.exception;

import com.skillskeeper.skillskeeper.auth.AuthMessages;

public class MissingOrInvalidTokenException extends RuntimeException {

	public MissingOrInvalidTokenException() {
		super(AuthMessages.MISSING_OR_INVALID_TOKEN);
	}
}
