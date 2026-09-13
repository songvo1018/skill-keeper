package com.skillskeeper.skillskeeper.auth.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Kept apart from {@link AuthWebConfig}, which needs a token service in its own constructor: a
 * configuration cannot supply a bean that something it depends on requires.
 *
 * <p>The clock is injected rather than read statically so token expiry can be driven forward in a
 * test without waiting for real time to pass.
 */
@Configuration
class AuthClockConfig {

	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}
}
