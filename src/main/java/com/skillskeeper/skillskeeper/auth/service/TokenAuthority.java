package com.skillskeeper.skillskeeper.auth.service;

import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;

/**
 * Issues bearer tokens and rules on whether one presented back to it is still valid.
 *
 * <p>Implemented by {@link TokenService}. How long a token lives and how many are retained are the
 * implementation's concern, as is the bookkeeping that enforces them.
 */
public interface TokenAuthority {

	/**
	 * Issues a token for a caller whose credentials have already been approved.
	 *
	 * @param username the caller the token identifies from now on
	 * @return the opaque token to present in an {@code Authorization} header
	 */
	String issueToken(String username);

	/**
	 * @return whether this token was issued here and has not passed its lifetime
	 */
	boolean isValid(String token);

	/**
	 * Resolves a token to the username that obtained it.
	 *
	 * @throws MissingOrInvalidTokenException if the token is absent, unknown, or past its lifetime
	 */
	String authenticate(String token);
}
