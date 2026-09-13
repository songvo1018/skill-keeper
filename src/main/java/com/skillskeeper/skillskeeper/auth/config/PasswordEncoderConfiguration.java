package com.skillskeeper.skillskeeper.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.skillskeeper.skillskeeper.auth.model.PasswordProperties;

/**
 * The one thing taken from Spring Security: password hashing. The full starter is deliberately not
 * on the classpath, so nothing here brings a filter chain or an access model that would compete
 * with the interceptor already guarding the API.
 */
@Configuration
class PasswordEncoderConfiguration {

	@Bean
	PasswordEncoder passwordEncoder(PasswordProperties properties) {
		return new BCryptPasswordEncoder(properties.bcryptStrength());
	}
}
