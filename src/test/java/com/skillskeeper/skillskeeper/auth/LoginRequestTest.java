package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class LoginRequestTest {

	/**
	 * The factory is kept open for the class's lifetime: a {@link Validator} taken from a closed
	 * factory is not something the contract guarantees will keep working.
	 */
	private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();

	private static final Validator VALIDATOR = FACTORY.getValidator();

	@AfterAll
	static void closeFactory() {
		FACTORY.close();
	}

	private static String repeat(int length) {
		return "x".repeat(length);
	}

	@Test
	void nonBlankUsernameAndPasswordAreValid() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR.validate(new LoginRequest("alice", "secret"));

		assertThat(violations).isEmpty();
	}

	@Test
	void blankUsernameIsRejected() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR.validate(new LoginRequest(" ", "secret"));

		assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
				.extracting(Object::toString)
				.containsExactly("username");
	}

	@Test
	void blankPasswordIsRejected() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR.validate(new LoginRequest("alice", ""));

		assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
				.extracting(Object::toString)
				.containsExactly("password");
	}

	@Test
	void missingUsernameAndPasswordAreBothRejected() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR.validate(new LoginRequest(null, null));

		assertThat(violations).hasSize(2);
	}

	// --- Length is bounded (Finding 16) ---

	@Test
	void usernameAtTheLimitIsAccepted() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR
				.validate(new LoginRequest(repeat(LoginRequest.MAX_USERNAME_LENGTH), "secret"));

		assertThat(violations).isEmpty();
	}

	@Test
	void overLongUsernameIsRejected() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR
				.validate(new LoginRequest(repeat(LoginRequest.MAX_USERNAME_LENGTH + 1), "secret"));

		assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
				.extracting(Object::toString)
				.containsExactly("username");
	}

	@Test
	void overLongPasswordIsRejected() {
		Set<ConstraintViolation<LoginRequest>> violations = VALIDATOR
				.validate(new LoginRequest("alice", repeat(LoginRequest.MAX_PASSWORD_LENGTH + 1)));

		assertThat(violations).extracting(ConstraintViolation::getPropertyPath)
				.extracting(Object::toString)
				.containsExactly("password");
	}

	// --- The password is not printable (Finding 16) ---

	@Test
	void toStringDoesNotRevealThePassword() {
		String rendered = new LoginRequest("alice", "hunter2").toString();

		assertThat(rendered).contains("alice").doesNotContain("hunter2");
	}
}
