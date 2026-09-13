package com.skillskeeper.skillskeeper.auth.model;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param bcryptStrength BCrypt work factor. Being slow is the point of it, so this is not a knob to
 * turn for throughput; the tests lower it only because they log in in almost every test and would
 * otherwise spend most of their time hashing.
 */
@ConfigurationProperties(prefix = "app.auth.password")
public record PasswordProperties(@DefaultValue("10") int bcryptStrength) {
}
