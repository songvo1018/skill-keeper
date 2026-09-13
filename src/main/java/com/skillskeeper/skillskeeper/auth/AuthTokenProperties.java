package com.skillskeeper.skillskeeper.auth;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param timeToLive how long an issued token stays valid
 * @param maxRetained upper bound on retained tokens, so repeated logins cannot exhaust memory
 */
@ConfigurationProperties(prefix = "app.auth.token")
public record AuthTokenProperties(@DefaultValue("30m") Duration timeToLive,
		@DefaultValue("10000") int maxRetained) {
}
