package com.skillskeeper.skillskeeper.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
public class AuthController {

	private final CredentialsVerifier credentialsVerifier;
	private final TokenService tokenService;

	public AuthController(CredentialsVerifier credentialsVerifier, TokenService tokenService) {
		this.credentialsVerifier = credentialsVerifier;
		this.tokenService = tokenService;
	}

	@PostMapping("/api/auth/login")
	public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
		if (!credentialsVerifier.verify(request.username(), request.password())) {
			throw new InvalidCredentialsException("Invalid username or password");
		}
		return ResponseEntity.ok(new LoginResponse(tokenService.issueToken()));
	}
}
