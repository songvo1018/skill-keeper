package com.skillskeeper.skillskeeper.auth.exception;

import java.util.List;

import com.skillskeeper.skillskeeper.auth.AuthMessages;

/**
 * Carries every rule the submitted password failed, so the response can name all of them.
 *
 * <p>Holds the violated rules, never the password itself: this exception's message reaches both the
 * client and the log.
 */
public class WeakPasswordException extends RuntimeException {

	private final transient List<String> violations;

	public WeakPasswordException(List<String> violations) {
		super(AuthMessages.WEAK_PASSWORD);
		this.violations = List.copyOf(violations);
	}

	public List<String> violations() {
		return violations;
	}
}
