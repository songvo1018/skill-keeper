package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class AuthControllerTest {

	@Test
	void rejectedCredentialsThrowInvalidCredentialsException() {
		CredentialsVerifier credentialsVerifier = mock(CredentialsVerifier.class);
		when(credentialsVerifier.verify("alice", "wrong-password")).thenReturn(false);
		AuthController controller = new AuthController(credentialsVerifier, new TokenService());

		assertThatThrownBy(() -> controller.login(new LoginRequest("alice", "wrong-password")))
				.isInstanceOf(InvalidCredentialsException.class);
	}
}
