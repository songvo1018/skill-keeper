package com.skillskeeper.skillskeeper.auth.service;

public interface CredentialsVerifier {

	boolean verify(String username, String password);
}
