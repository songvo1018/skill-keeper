package com.skillskeeper.skillskeeper.auth.repository;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

/**
 * One row of {@code app_user}: a registered user as the database holds them.
 *
 * <p>{@code passwordHash} is the only form of the password that exists past the request that set
 * it. Nothing here is a response body - what registration returns is assembled separately, so that
 * the hash cannot reach a client by someone widening a record.
 *
 * @param createdAt {@code null} on a row that has not been inserted yet: the column is filled by
 * the database default, so an insert never supplies it
 */
@Table("app_user")
public record AppUserRow(@Id String id, String username, String passwordHash, Instant createdAt) {

	public static AppUserRow forInsert(String id, String username, String passwordHash) {
		return new AppUserRow(id, username, passwordHash, null);
	}
}
