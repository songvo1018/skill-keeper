package com.skillskeeper.skillskeeper.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RegisterRequestTest {

	/**
	 * The generated {@code toString} would print the password, and a binding result carries the
	 * record into the log the moment anyone raises the log level.
	 */
	@Test
	void toStringShowsTheUsernameButNotThePassword() {
		RegisterRequest request = new RegisterRequest("alice", "Sup3rSecret!");

		assertThat(request.toString()).contains("alice").doesNotContain("Sup3rSecret!");
	}

	@Test
	void toStringSurvivesANullPassword() {
		assertThat(new RegisterRequest("alice", null).toString()).contains("alice");
	}

	/**
	 * Registration must not accept a name or password that login would then reject as too long,
	 * which would leave an account nobody could use.
	 */
	@Test
	void theBoundsAreTheOnesLoginEnforces() {
		assertThat(LoginRequest.MAX_USERNAME_LENGTH).isEqualTo(100);
		assertThat(LoginRequest.MAX_PASSWORD_LENGTH).isEqualTo(200);
	}
}
