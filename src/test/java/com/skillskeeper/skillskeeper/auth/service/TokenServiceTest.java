package com.skillskeeper.skillskeeper.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;
import com.skillskeeper.skillskeeper.auth.model.AuthTokenProperties;

class TokenServiceTest {

	private static final Duration TTL = Duration.ofMinutes(30);

	/**
	 * A clock the test moves by hand, so expiry can be reached without waiting for real time.
	 */
	private static final class AdvanceableClock extends Clock {

		private Instant now = Instant.parse("2026-09-13T12:00:00Z");

		@Override
		public Instant instant() {
			return this.now;
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		void advance(Duration amount) {
			this.now = this.now.plus(amount);
		}
	}

	private final AdvanceableClock clock = new AdvanceableClock();

	private TokenService newService(int maxRetained) {
		return new TokenService(new AuthTokenProperties(TTL, maxRetained), this.clock);
	}

	@Test
	void issuedTokenIsValid() {
		TokenService service = newService(100);

		String token = service.issueToken("alice");

		assertThat(service.isValid(token)).isTrue();
	}

	@Test
	void unrecognizedTokenIsInvalid() {
		TokenService service = newService(100);

		assertThat(service.isValid("does-not-exist")).isFalse();
	}

	@Test
	void nullTokenIsInvalid() {
		TokenService service = newService(100);

		assertThat(service.isValid(null)).isFalse();
	}

	@Test
	void eachIssuedTokenIsUnique() {
		TokenService service = newService(100);

		String first = service.issueToken("alice");
		String second = service.issueToken("bob");

		assertThat(first).isNotEqualTo(second);
		assertThat(service.isValid(first)).isTrue();
		assertThat(service.isValid(second)).isTrue();
	}

	@Test
	void authenticateReturnsTheUsernameThatObtainedTheToken() {
		TokenService service = newService(100);

		String token = service.issueToken("alice");

		assertThat(service.authenticate(token)).isEqualTo("alice");
	}

	@Test
	void authenticateRejectsAnUnknownToken() {
		TokenService service = newService(100);

		assertThatThrownBy(() -> service.authenticate("does-not-exist"))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	// --- Expiry (Finding 7) ---

	@Test
	void tokenStaysValidWithinItsLifetime() {
		TokenService service = newService(100);
		String token = service.issueToken("alice");

		this.clock.advance(TTL.minusSeconds(1));

		assertThat(service.isValid(token)).isTrue();
	}

	@Test
	void tokenIsInvalidAndDroppedOnceItsLifetimeElapsed() {
		TokenService service = newService(100);
		String token = service.issueToken("alice");

		this.clock.advance(TTL);

		assertThat(service.isValid(token)).isFalse();
		assertThat(service.retainedCount()).isZero();
		assertThatThrownBy(() -> service.authenticate(token))
				.isInstanceOf(MissingOrInvalidTokenException.class);
	}

	// --- Bounded store (Finding 7) ---

	@Test
	void sustainedLoginVolumeNeverExceedsTheRetentionBound() {
		TokenService service = newService(10);

		String mostRecent = null;
		for (int i = 0; i < 500; i++) {
			mostRecent = service.issueToken("alice");
			this.clock.advance(Duration.ofMillis(1));
			assertThat(service.retainedCount()).isLessThanOrEqualTo(10);
		}

		assertThat(service.isValid(mostRecent)).isTrue();
	}

	@Test
	void expiredTokensAreReleasedBeforeLiveOnes() {
		TokenService service = newService(3);
		String expiring = service.issueToken("alice");

		this.clock.advance(TTL);
		String live = service.issueToken("bob");

		assertThat(service.isValid(expiring)).isFalse();
		assertThat(service.isValid(live)).isTrue();
		assertThat(service.retainedCount()).isEqualTo(1);
	}
}
