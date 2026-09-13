package com.skillskeeper.skillskeeper.auth.service;

import com.skillskeeper.skillskeeper.auth.exception.UsernameAlreadyTakenException;
import com.skillskeeper.skillskeeper.auth.exception.WeakPasswordException;

/**
 * Creates the accounts that {@link CredentialsVerifier} later checks against.
 *
 * <p>Implemented by {@link UserRegistrationService}.
 */
public interface UserRegistry {

	/**
	 * Registers a user, storing the password only as a hash.
	 *
	 * @return the id generated for the new account
	 * @throws WeakPasswordException if the password fails one or more of the required rules
	 * @throws UsernameAlreadyTakenException if the username is taken, including in another letter case
	 */
	String register(String username, String password);
}
