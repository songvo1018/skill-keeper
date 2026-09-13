package com.skillskeeper.skillskeeper.auth.service;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.skillskeeper.skillskeeper.auth.exception.UsernameAlreadyTakenException;
import com.skillskeeper.skillskeeper.auth.exception.WeakPasswordException;
import com.skillskeeper.skillskeeper.auth.repository.AppUserRepository;
import com.skillskeeper.skillskeeper.auth.repository.AppUserRow;

@Service
public class UserRegistrationService implements UserRegistry {

	private final AppUserRepository repository;
	private final PasswordPolicy passwordPolicy;
	private final PasswordEncoder passwordEncoder;

	public UserRegistrationService(AppUserRepository repository, PasswordPolicy passwordPolicy,
			PasswordEncoder passwordEncoder) {
		this.repository = repository;
		this.passwordPolicy = passwordPolicy;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * The username is not checked for availability before the insert. Looking first and inserting
	 * after is a race that two simultaneous registrations of the same name both win; the unique
	 * index is what actually decides, so the insert is attempted and its rejection is the answer.
	 *
	 * <p>The name is stored exactly as it was given. Uniqueness ignores case, but what the user
	 * typed is what comes back to them.
	 */
	@Override
	public String register(String username, String password) {
		List<String> violations = passwordPolicy.violations(password);
		if (!violations.isEmpty()) {
			throw new WeakPasswordException(violations);
		}

		String id = UUID.randomUUID().toString();
		try {
			repository.insert(AppUserRow.forInsert(id, username, passwordEncoder.encode(password)));
		}
		catch (DuplicateKeyException e) {
			throw new UsernameAlreadyTakenException(e);
		}

		return id;
	}
}
