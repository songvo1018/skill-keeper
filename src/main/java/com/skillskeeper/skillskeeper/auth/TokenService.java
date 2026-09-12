package com.skillskeeper.skillskeeper.auth;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

@Service
public class TokenService {

	private final Set<String> tokens = ConcurrentHashMap.newKeySet();

	public String issueToken() {
		String token = UUID.randomUUID().toString();
		tokens.add(token);
		return token;
	}

	public boolean isValid(String token) {
		return token != null && tokens.contains(token);
	}
}
