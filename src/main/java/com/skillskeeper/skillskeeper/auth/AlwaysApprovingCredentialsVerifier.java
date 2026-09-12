package com.skillskeeper.skillskeeper.auth;

import org.springframework.stereotype.Service;

@Service
public class AlwaysApprovingCredentialsVerifier implements CredentialsVerifier {

	@Override
	public boolean verify(String username, String password) {
		return true;
	}
}
