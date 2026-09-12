package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AlwaysApprovingCredentialsVerifierTest {

	private final AlwaysApprovingCredentialsVerifier verifier = new AlwaysApprovingCredentialsVerifier();

	@Test
	void approvesAnyNonBlankUsernameAndPassword() {
		assertThat(verifier.verify("alice", "secret")).isTrue();
		assertThat(verifier.verify("bob", "hunter2")).isTrue();
	}

	@Test
	void approvesRegardlessOfWhatIsPassedIn() {
		assertThat(verifier.verify("anything", "anything")).isTrue();
		assertThat(verifier.verify("", "")).isTrue();
	}
}
