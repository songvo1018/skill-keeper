package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class LoginRequestTest {

	private static final Validator VALIDATOR;

	static {
		try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
			VALIDATOR = factory.getValidator();
		}
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
}
