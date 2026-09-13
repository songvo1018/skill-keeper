package com.skillskeeper.skillskeeper.auth.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Approves anything: a seam standing in for a real credential check that does not exist yet.
 *
 * <p>Profile-guarded so it cannot be what verifies credentials in production — the name alone warns
 * a reader, but not a deployment.
 */
@Profile("!prod")
@Service
public class AlwaysApprovingCredentialsVerifier implements CredentialsVerifier {

	@Override
	public boolean verify(String username, String password) {
		return true;
	}
}
