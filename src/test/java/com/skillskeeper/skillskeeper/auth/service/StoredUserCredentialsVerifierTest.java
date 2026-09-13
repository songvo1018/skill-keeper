package com.skillskeeper.skillskeeper.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.skillskeeper.skillskeeper.support.PostgresTestContainer;
import com.skillskeeper.skillskeeper.support.TestPasswordHashing;

/**
 * Runs against the real table and the real hashing: what is being checked is that a password can be
 * verified against what was stored, and a double for either side would prove nothing about that.
 */
@SpringBootTest
@Transactional
class StoredUserCredentialsVerifierTest {

	private static final String USERNAME = "verified-user";

	private static final String PASSWORD = "Sup3rSecret!";

	@TempDir
	static Path tempDir;

	@Autowired
	private CredentialsVerifier verifier;

	@Autowired
	private UserRegistry userRegistry;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
		PostgresTestContainer.registerProperties(registry);
		TestPasswordHashing.registerProperties(registry);
	}

	@Test
	void theRealVerifierIsTheOneWired() {
		assertThat(verifier).isInstanceOf(StoredUserCredentialsVerifier.class);
	}

	@Test
	void theRegisteredPasswordIsAccepted() {
		userRegistry.register(USERNAME, PASSWORD);

		assertThat(verifier.verify(USERNAME, PASSWORD)).isTrue();
	}

	@Test
	void aDifferentPasswordIsRejected() {
		userRegistry.register(USERNAME, PASSWORD);

		assertThat(verifier.verify(USERNAME, "Wr0ng!Password")).isFalse();
	}

	@Test
	void anUnregisteredNameIsRejected() {
		assertThat(verifier.verify("nobody-at-all", PASSWORD)).isFalse();
	}

	@Test
	void theNameMayBeWrittenInAnyCase() {
		userRegistry.register(USERNAME, PASSWORD);

		assertThat(verifier.verify(USERNAME.toUpperCase(), PASSWORD)).isTrue();
		assertThat(verifier.verify("Verified-User", PASSWORD)).isTrue();
	}

	@Test
	void thePasswordItselfIsStillCaseSensitive() {
		userRegistry.register(USERNAME, PASSWORD);

		assertThat(verifier.verify(USERNAME, PASSWORD.toUpperCase())).isFalse();
	}

	@Test
	void nullsAreRejectedRatherThanThrown() {
		assertThat(verifier.verify(null, PASSWORD)).isFalse();
		assertThat(verifier.verify(USERNAME, null)).isFalse();
	}
}
