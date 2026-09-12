package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TokenServiceTest {

	@Test
	void issuedTokenIsValid() {
		TokenService service = new TokenService();

		String token = service.issueToken();

		assertThat(service.isValid(token)).isTrue();
	}

	@Test
	void unrecognizedTokenIsInvalid() {
		TokenService service = new TokenService();

		assertThat(service.isValid("does-not-exist")).isFalse();
	}

	@Test
	void nullTokenIsInvalid() {
		TokenService service = new TokenService();

		assertThat(service.isValid(null)).isFalse();
	}

	@Test
	void eachIssuedTokenIsUnique() {
		TokenService service = new TokenService();

		String first = service.issueToken();
		String second = service.issueToken();

		assertThat(first).isNotEqualTo(second);
		assertThat(service.isValid(first)).isTrue();
		assertThat(service.isValid(second)).isTrue();
	}
}
