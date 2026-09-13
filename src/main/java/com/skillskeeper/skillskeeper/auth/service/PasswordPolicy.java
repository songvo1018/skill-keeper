package com.skillskeeper.skillskeeper.auth.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.skillskeeper.skillskeeper.auth.AuthMessages;

/**
 * The rules a password has to satisfy to be accepted at registration.
 *
 * <p>Returns every rule the password fails rather than the first one, so a client can fix them in
 * one go instead of discovering them one request at a time. Kept out of bean validation for exactly
 * that reason: assembling a readable list from constraint violations is more work than writing the
 * list, and here the rules can be checked without a web layer.
 */
@Component
public class PasswordPolicy {

	/**
	 * A special character is anything that is neither a letter nor a digit, rather than a listed
	 * set of punctuation. Listing the set would reject a password for containing a character nobody
	 * thought to allow. A space counts, deliberately.
	 */
	private static boolean isSpecial(char character) {
		return !Character.isLetterOrDigit(character);
	}

	/**
	 * @return the rules this password fails, in a stable order; empty when it satisfies all of them
	 */
	public List<String> violations(String password) {
		String candidate = password == null ? "" : password;
		List<String> violations = new ArrayList<>();

		if (candidate.length() < AuthMessages.MIN_PASSWORD_LENGTH) {
			violations.add(AuthMessages.PASSWORD_TOO_SHORT_TEMPLATE.formatted(AuthMessages.MIN_PASSWORD_LENGTH));
		}
		if (candidate.chars().noneMatch(Character::isUpperCase)) {
			violations.add(AuthMessages.PASSWORD_NEEDS_UPPERCASE);
		}
		if (candidate.chars().noneMatch(Character::isLowerCase)) {
			violations.add(AuthMessages.PASSWORD_NEEDS_LOWERCASE);
		}
		if (candidate.chars().noneMatch(Character::isDigit)) {
			violations.add(AuthMessages.PASSWORD_NEEDS_DIGIT);
		}
		if (candidate.chars().noneMatch(character -> isSpecial((char) character))) {
			violations.add(AuthMessages.PASSWORD_NEEDS_SPECIAL);
		}

		return List.copyOf(violations);
	}
}
