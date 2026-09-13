package com.skillskeeper.skillskeeper.auth.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.HandlerInterceptor;

import com.skillskeeper.skillskeeper.auth.AuthMessages;
import com.skillskeeper.skillskeeper.auth.exception.MissingOrInvalidTokenException;
import com.skillskeeper.skillskeeper.auth.service.TokenAuthority;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

public class AuthTokenInterceptor implements HandlerInterceptor {

	private static final Logger log = LoggerFactory.getLogger(AuthTokenInterceptor.class);

	private final TokenAuthority tokenAuthority;

	public AuthTokenInterceptor(TokenAuthority tokenAuthority) {
		this.tokenAuthority = tokenAuthority;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String token = bearerToken(request);
		String username;
		try {
			username = tokenAuthority.authenticate(token);
		}
		catch (MissingOrInvalidTokenException e) {
			log.warn("Rejected {} {}: token not recognised or no longer valid", request.getMethod(),
					request.getRequestURI());
			throw e;
		}

		request.setAttribute(AuthenticatedUser.REQUEST_ATTRIBUTE, username);
		return true;
	}

	/**
	 * RFC 7235 makes the scheme name case-insensitive, so {@code bearer} from a correct client must
	 * be accepted as readily as {@code Bearer}.
	 */
	private String bearerToken(HttpServletRequest request) {
		String header = request.getHeader(HttpHeaders.AUTHORIZATION);
		int prefixLength = AuthMessages.BEARER_PREFIX.length();
		if (header == null || header.length() <= prefixLength
				|| !header.regionMatches(true, 0, AuthMessages.BEARER_PREFIX, 0, prefixLength)) {
			log.warn("Rejected {} {}: no usable {} header", request.getMethod(), request.getRequestURI(),
					HttpHeaders.AUTHORIZATION);
			throw new MissingOrInvalidTokenException();
		}
		return header.substring(prefixLength);
	}
}
