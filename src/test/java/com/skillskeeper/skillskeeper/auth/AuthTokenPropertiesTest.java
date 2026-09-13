package com.skillskeeper.skillskeeper.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class AuthTokenPropertiesTest {

	private static AuthTokenProperties bind(Map<String, Object> properties) {
		return new Binder(new MapConfigurationPropertySource(properties))
				.bindOrCreate("app.auth.token", AuthTokenProperties.class);
	}

	@Test
	void configuredValuesBind() {
		AuthTokenProperties properties = bind(Map.of(
				"app.auth.token.time-to-live", "5m",
				"app.auth.token.max-retained", "7"));

		assertThat(properties.timeToLive()).isEqualTo(Duration.ofMinutes(5));
		assertThat(properties.maxRetained()).isEqualTo(7);
	}

	@Test
	void absentPropertiesFallBackToDocumentedDefaults() {
		AuthTokenProperties properties = bind(Map.of());

		assertThat(properties.timeToLive()).isEqualTo(Duration.ofMinutes(30));
		assertThat(properties.maxRetained()).isEqualTo(10_000);
	}
}
