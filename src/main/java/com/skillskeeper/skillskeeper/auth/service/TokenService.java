package com.skillskeeper.skillskeeper.auth.service;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;
import com.skillskeeper.skillskeeper.auth.model.AuthTokenProperties;

@Service
public class TokenService implements TokenAuthority {

	private final Map<String, TokenRecord> tokens = new ConcurrentHashMap<>();
	private final AuthTokenProperties properties;
	private final Clock clock;

	public TokenService(AuthTokenProperties properties, Clock clock) {
		this.properties = properties;
		this.clock = clock;
	}

	@Override
	public String issueToken(String username) {
		Instant now = clock.instant();
		purgeExpired(now);
		enforceCapacity();

		String token = UUID.randomUUID().toString();
		tokens.put(token, new TokenRecord(username, now));
		return token;
	}

	@Override
	public boolean isValid(String token) {
		return lookup(token) != null;
	}

	/**
	 * Resolves a token to the username that obtained it.
	 *
	 * @throws MissingOrInvalidTokenException if the token is absent, unknown, or past its lifetime
	 */
	@Override
	public String authenticate(String token) {
		TokenRecord record = lookup(token);
		if (record == null) {
			throw new MissingOrInvalidTokenException();
		}
		return record.username();
	}

	int retainedCount() {
		return tokens.size();
	}

	/**
	 * Expiry is evaluated here rather than on a timer: validation already touches every token, so an
	 * elapsed one is recognised and dropped at the moment it is next presented.
	 */
	private TokenRecord lookup(String token) {
		if (token == null) {
			return null;
		}
		TokenRecord record = tokens.get(token);
		if (record == null) {
			return null;
		}
		if (hasExpired(record, clock.instant())) {
			tokens.remove(token, record);
			return null;
		}
		return record;
	}

	private boolean hasExpired(TokenRecord record, Instant now) {
		return !now.isBefore(record.issuedAt().plus(properties.timeToLive()));
	}

	private void purgeExpired(Instant now) {
		tokens.entrySet().removeIf(entry -> hasExpired(entry.getValue(), now));
	}

	/**
	 * Only reached once the store is full of tokens that are all still within their lifetime, so the
	 * oldest live one is evicted to admit the new login. Losing a session is preferable to exhausting
	 * the heap; a linear scan is acceptable because it happens only at capacity.
	 */
	private void enforceCapacity() {
		int maxRetained = Math.max(1, properties.maxRetained());
		while (tokens.size() >= maxRetained) {
			Map.Entry<String, TokenRecord> oldest = tokens.entrySet().stream()
					.min(Comparator.comparing(entry -> entry.getValue().issuedAt()))
					.orElse(null);
			if (oldest == null) {
				return;
			}
			tokens.remove(oldest.getKey(), oldest.getValue());
		}
	}
}
