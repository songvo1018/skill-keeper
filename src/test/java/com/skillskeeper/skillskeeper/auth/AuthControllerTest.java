package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.skillskeeper.skillskeeper.auth.controller.AuthController;
import com.skillskeeper.skillskeeper.auth.exception.InvalidCredentialsException;
import com.skillskeeper.skillskeeper.auth.model.AuthTokenProperties;
import com.skillskeeper.skillskeeper.auth.model.LoginRequest;
import com.skillskeeper.skillskeeper.auth.service.CredentialsVerifier;
import com.skillskeeper.skillskeeper.auth.service.TokenService;
import com.skillskeeper.skillskeeper.auth.service.UserRegistry;

import com.skillskeeper.skillskeeper.support.LogCapture;

class AuthControllerTest {

	private final CredentialsVerifier credentialsVerifier = mock(CredentialsVerifier.class);
	private final TokenService tokenService = new TokenService(
			new AuthTokenProperties(Duration.ofMinutes(30), 100), Clock.systemUTC());
	private final AuthController controller = new AuthController(this.credentialsVerifier, this.tokenService,
			mock(UserRegistry.class));

	@Test
	void rejectedCredentialsThrowInvalidCredentialsException() {
		when(this.credentialsVerifier.verify("alice", "wrong-password")).thenReturn(false);

		assertThatThrownBy(() -> this.controller.login(new LoginRequest("alice", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);
	}

	@Test
	void approvedCredentialsIssueATokenBoundToTheUsername() {
		when(this.credentialsVerifier.verify("alice", "secret")).thenReturn(true);

		String token = this.controller.login(new LoginRequest("alice", "secret")).getBody().token();

		assertThat(this.tokenService.authenticate(token)).isEqualTo("alice");
	}

	@Test
	void rejectedLoginIsLoggedWithoutThePassword() {
		when(this.credentialsVerifier.verify("alice", "hunter2")).thenReturn(false);

		try (LogCapture logs = LogCapture.of(AuthController.class)) {
			assertThatThrownBy(() -> this.controller.login(new LoginRequest("alice", "hunter2")))
					.isInstanceOf(InvalidCredentialsException.class);

			assertThat(logs.warningsAndWorse()).isNotEmpty()
					.allSatisfy(message -> assertThat(message).doesNotContain("hunter2"))
					.anySatisfy(message -> assertThat(message).contains("alice"));
		}
	}
}
