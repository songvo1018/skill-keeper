package com.skillskeeper.skillskeeper.auth.web;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.skillskeeper.skillskeeper.auth.AuthMessages;
import com.skillskeeper.skillskeeper.auth.exception.InvalidCredentialsException;
import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;
import com.skillskeeper.skillskeeper.auth.exception.UsernameAlreadyTakenException;
import com.skillskeeper.skillskeeper.auth.exception.WeakPasswordException;

@RestControllerAdvice
public class AuthExceptionHandler {

	@ExceptionHandler(MissingOrInvalidTokenException.class)
	public ResponseEntity<ProblemDetail> handleMissingOrInvalidToken(MissingOrInvalidTokenException ex) {
		return unauthorized(ex.getMessage());
	}

	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ProblemDetail> handleInvalidCredentials(InvalidCredentialsException ex) {
		return unauthorized(ex.getMessage());
	}

	/**
	 * Lists every rule the password failed rather than only the first, so the client can correct
	 * them in one go. The submitted password appears in neither the detail nor the listed rules.
	 */
	@ExceptionHandler(WeakPasswordException.class)
	public ProblemDetail handleWeakPassword(WeakPasswordException ex) {
		ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
		problemDetail.setProperty(AuthMessages.VIOLATIONS_PROPERTY, ex.violations());
		return problemDetail;
	}

	@ExceptionHandler(UsernameAlreadyTakenException.class)
	public ProblemDetail handleUsernameAlreadyTaken(UsernameAlreadyTakenException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	/**
	 * Returned as a {@link ResponseEntity} rather than a bare {@link ProblemDetail} so the response
	 * can carry the {@code WWW-Authenticate} challenge RFC 7235 requires on a 401.
	 */
	private static ResponseEntity<ProblemDetail> unauthorized(String detail) {
		return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
				.header(HttpHeaders.WWW_AUTHENTICATE, AuthMessages.BEARER_CHALLENGE)
				.body(ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail));
	}
}
