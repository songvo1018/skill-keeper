package com.skillskeeper.skillskeeper.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.skillskeeper.skillskeeper.auth.AuthMessages;

/**
 * The rules are checked without a web layer, which is the reason they live in a class of their own
 * rather than in bean validation.
 */
class PasswordPolicyTest {

	private static final String VALID = "Passw0rd!";

	private final PasswordPolicy policy = new PasswordPolicy();

	@Test
	void aPasswordMeetingEveryRuleHasNoViolations() {
		assertThat(policy.violations(VALID)).isEmpty();
	}

	@Test
	void aPasswordExactlyAtTheMinimumLengthIsAccepted() {
		String atTheBoundary = "Abcdef1!";

		assertThat(atTheBoundary).hasSize(AuthMessages.MIN_PASSWORD_LENGTH);
		assertThat(policy.violations(atTheBoundary)).isEmpty();
	}

	@Test
	void aPasswordOneCharacterShortIsRejected() {
		String justUnder = "Abcde1!";

		assertThat(justUnder).hasSize(AuthMessages.MIN_PASSWORD_LENGTH - 1);
		assertThat(policy.violations(justUnder))
				.containsExactly(AuthMessages.PASSWORD_TOO_SHORT_TEMPLATE.formatted(AuthMessages.MIN_PASSWORD_LENGTH));
	}

	@Test
	void aPasswordWithoutAnUpperCaseLetterIsRejected() {
		assertThat(policy.violations("passw0rd!")).containsExactly(AuthMessages.PASSWORD_NEEDS_UPPERCASE);
	}

	@Test
	void aPasswordWithoutALowerCaseLetterIsRejected() {
		assertThat(policy.violations("PASSW0RD!")).containsExactly(AuthMessages.PASSWORD_NEEDS_LOWERCASE);
	}

	@Test
	void aPasswordWithoutADigitIsRejected() {
		assertThat(policy.violations("Password!")).containsExactly(AuthMessages.PASSWORD_NEEDS_DIGIT);
	}

	@Test
	void aPasswordWithoutASpecialCharacterIsRejected() {
		assertThat(policy.violations("Passw0rds")).containsExactly(AuthMessages.PASSWORD_NEEDS_SPECIAL);
	}

	@Test
	void everyBrokenRuleIsReportedNotJustTheFirst() {
		assertThat(policy.violations("abc")).containsExactlyInAnyOrder(
				AuthMessages.PASSWORD_TOO_SHORT_TEMPLATE.formatted(AuthMessages.MIN_PASSWORD_LENGTH),
				AuthMessages.PASSWORD_NEEDS_UPPERCASE,
				AuthMessages.PASSWORD_NEEDS_DIGIT,
				AuthMessages.PASSWORD_NEEDS_SPECIAL);
	}

	@Test
	void anEmptyPasswordBreaksEveryRule() {
		assertThat(policy.violations("")).hasSize(5);
	}

	/**
	 * A space is neither a letter nor a digit, so it satisfies the special-character rule. Recorded
	 * as a test because it is a consequence of how the rule is defined rather than an oversight.
	 */
	@Test
	void aSpaceCountsAsASpecialCharacter() {
		assertThat(policy.violations("Passw0rd ")).isEmpty();
	}

	@Test
	void aNonAsciiLetterStillSatisfiesTheLetterRules() {
		assertThat(policy.violations("Пароль1!")).isEmpty();
	}
}
