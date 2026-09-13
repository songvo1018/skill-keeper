package com.skillskeeper.skillskeeper.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class AuthController {

	private static final Logger log = LoggerFactory.getLogger(AuthController.class);

	private final CredentialsVerifier credentialsVerifier;
	private final TokenService tokenService;

	public AuthController(CredentialsVerifier credentialsVerifier, TokenService tokenService) {
		this.credentialsVerifier = credentialsVerifier;
		this.tokenService = tokenService;
	}

	@PostMapping("/api/auth/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		if (!credentialsVerifier.verify(request.username(), request.password())) {
			log.warn("Rejected login for username {}: credentials not approved", request.username());
			throw new InvalidCredentialsException(AuthMessages.INVALID_CREDENTIALS);
		}
		return ResponseEntity.ok(new LoginResponse(tokenService.issueToken(request.username())));
	}
}
