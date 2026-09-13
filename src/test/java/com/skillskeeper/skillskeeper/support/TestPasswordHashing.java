package com.skillskeeper.skillskeeper.support;

import org.springframework.test.context.DynamicPropertyRegistry;

/**
 * Lowers the BCrypt work factor for tests that register or log in.
 *
 * <p>BCrypt is slow deliberately, and these tests log in in almost every test method; at the
 * configured production strength the suite would spend most of its time hashing. It must never be
 * lowered anywhere the service actually runs.
 *
 * <p>Applied through {@code @DynamicPropertySource} rather than a test {@code application.properties}:
 * a second file of that name on the classpath replaces the main one outright instead of adding to
 * it, which silently drops every other setting the application relies on.
 */
public final class TestPasswordHashing {

	private static final int FAST_BCRYPT_STRENGTH = 4;

	private TestPasswordHashing() {
	}

	public static void registerProperties(DynamicPropertyRegistry registry) {
		registry.add("app.auth.password.bcrypt-strength", () -> FAST_BCRYPT_STRENGTH);
	}
}
