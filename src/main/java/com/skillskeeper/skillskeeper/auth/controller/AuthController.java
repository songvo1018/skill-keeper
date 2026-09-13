package com.skillskeeper.skillskeeper.auth.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.skillskeeper.skillskeeper.auth.AuthMessages;
import com.skillskeeper.skillskeeper.auth.exception.InvalidCredentialsException;
import com.skillskeeper.skillskeeper.auth.model.LoginRequest;
import com.skillskeeper.skillskeeper.auth.model.LoginResponse;
import com.skillskeeper.skillskeeper.auth.service.CredentialsVerifier;
import com.skillskeeper.skillskeeper.auth.service.TokenAuthority;

import jakarta.validation.Valid;

@RestController
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final CredentialsVerifier credentialsVerifier;
	private final TokenAuthority tokenAuthority;

	public AuthController(CredentialsVerifier credentialsVerifier, TokenAuthority tokenAuthority) {
		this.credentialsVerifier = credentialsVerifier;
		this.tokenAuthority = tokenAuthority;
	}

	@PostMapping("/api/auth/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		if (!credentialsVerifier.verify(request.username(), request.password())) {
			log.warn("Rejected login for username {}: credentials not approved", request.username());
			throw new InvalidCredentialsException(AuthMessages.INVALID_CREDENTIALS);
		}
		return ResponseEntity.ok(new LoginResponse(tokenAuthority.issueToken(request.username())));
	}
}
