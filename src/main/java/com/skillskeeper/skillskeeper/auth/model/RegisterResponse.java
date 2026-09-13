package com.skillskeeper.skillskeeper.auth.model;

/**
 * What registration returns: enough to identify the account that was created, and nothing about the
 * password. Registration issues no token - the client goes to login for that, so tokens are handed
 * out in one place only.
 */
public record RegisterResponse(String id, String username) {
}
