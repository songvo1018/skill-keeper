package com.skillskeeper.skillskeeper.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/**
 * Fails startup under the production profile while the only credential verification available is the
 * always-approving stub. Relying on the missing bean alone would also fail, but with a message that
 * reads like a wiring mistake rather than a deliberate refusal.
 */
@Profile("prod")
@Configuration
class ProductionCredentialsVerifierConfiguration {

	@Bean
	CredentialsVerifier credentialsVerifier() {
		throw new IllegalStateException(AuthMessages.NO_REAL_CREDENTIALS_VERIFIER);
	}
}
