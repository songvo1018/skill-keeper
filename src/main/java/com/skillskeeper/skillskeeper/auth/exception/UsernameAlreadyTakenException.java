package com.skillskeeper.skillskeeper.auth.exception;

import com.skillskeeper.skillskeeper.auth.AuthMessages;

/**
 * Raised when the unique index rejects a registration because the username is taken - including
 * when it differs only in letter case.
 */
public class UsernameAlreadyTakenException extends RuntimeException {

	public UsernameAlreadyTakenException(Throwable cause) {
		super(AuthMessages.USERNAME_ALREADY_TAKEN, cause);
	}
}
