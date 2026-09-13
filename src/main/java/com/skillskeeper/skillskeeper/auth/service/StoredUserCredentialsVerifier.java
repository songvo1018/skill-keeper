package com.skillskeeper.skillskeeper.auth.service;

import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.skillskeeper.skillskeeper.auth.repository.AppUserRepository;
import com.skillskeeper.skillskeeper.auth.repository.AppUserRow;

/**
 * Approves a username and password only when they match a registered user.
 *
 * <p>Names are matched without regard to letter case, the same way registration enforces their
 * uniqueness, so a user can log in with their name written however they like.
 */
@Service
public class StoredUserCredentialsVerifier implements CredentialsVerifier {

	/**
	 * A syntactically valid BCrypt hash of a value nobody knows, compared against when the username
	 * is unknown. Returning early instead would make a request for a name that does not exist
	 * measurably faster than one for a name that does, which is enough to enumerate accounts even
	 * though both answer 401.
	 */
	private static final String ABSENT_USER_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

	private final AppUserRepository repository;
	private final PasswordEncoder passwordEncoder;

	public StoredUserCredentialsVerifier(AppUserRepository repository, PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordEncoder = passwordEncoder;
	}

	@Override
	public boolean verify(String username, String password) {
		if (username == null || password == null) {
			return false;
		}

		Optional<AppUserRow> user = repository.findByUsernameIgnoringCase(username);
		String storedHash = user.map(AppUserRow::passwordHash).orElse(ABSENT_USER_HASH);
		boolean matches = passwordEncoder.matches(password, storedHash);

		return user.isPresent() && matches;
	}
}
