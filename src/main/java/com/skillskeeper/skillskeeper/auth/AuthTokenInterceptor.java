package com.skillskeeper.skillskeeper.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthTokenInterceptor implements HandlerInterceptor {

	private final TokenService tokenService;

	public AuthTokenInterceptor(TokenService tokenService) {
		this.tokenService = tokenService;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (header == null || !header.startsWith(AuthMessages.BEARER_PREFIX)) {
			throw new MissingOrInvalidTokenException();
		}

		String token = header.substring(AuthMessages.BEARER_PREFIX.length());
		if (!tokenService.isValid(token)) {
			throw new MissingOrInvalidTokenException();
		}

		return true;
	}
}
