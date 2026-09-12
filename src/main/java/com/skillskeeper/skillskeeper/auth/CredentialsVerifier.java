package com.skillskeeper.skillskeeper.auth;

public interface CredentialsVerifier {

	boolean verify(String username, String password);
}
