package com.skillskeeper.skillskeeper.auth.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.skillskeeper.skillskeeper.support.PostgresTestContainer;

/**
 * Exercises the table against a real PostgreSQL: the insert is hand-written SQL and both the
 * case-insensitive lookup and the uniqueness rule live in SQL, so none of them is proven by
 * anything an in-memory double could check. Each test runs in a transaction that is rolled back.
 */
@SpringBootTest
@Transactional
class AppUserRepositoryTest {

	@TempDir
	static Path tempDir;

	@Autowired
	private AppUserRepository repository;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
		PostgresTestContainer.registerProperties(registry);
	}

	@Test
	void insertedRowIsReadBackUnchanged() {
		repository.insert(AppUserRow.forInsert("id-1", "Alice", "hashed-value"));

		AppUserRow stored = repository.findById("id-1").orElseThrow();
		assertThat(stored.username()).isEqualTo("Alice");
		assertThat(stored.passwordHash()).isEqualTo("hashed-value");
		assertThat(stored.createdAt()).isNotNull();
	}

	@Test
	void userIsFoundByNameInAnyCase() {
		repository.insert(AppUserRow.forInsert("id-1", "Alice", "hashed-value"));

		assertThat(repository.findByUsernameIgnoringCase("Alice")).isPresent();
		assertThat(repository.findByUsernameIgnoringCase("alice")).isPresent();
		assertThat(repository.findByUsernameIgnoringCase("ALICE")).isPresent();
		assertThat(repository.findByUsernameIgnoringCase("aLiCe").orElseThrow().id()).isEqualTo("id-1");
	}

	@Test
	void theStoredNameKeepsTheCaseItWasWrittenWith() {
		repository.insert(AppUserRow.forInsert("id-1", "AlIcE", "hashed-value"));

		assertThat(repository.findByUsernameIgnoringCase("alice").orElseThrow().username()).isEqualTo("AlIcE");
	}

	@Test
	void unknownNameIsNotFound() {
		assertThat(repository.findByUsernameIgnoringCase("nobody")).isEmpty();
	}

	@Test
	void aNameDifferingOnlyInCaseCannotBeInsertedTwice() {
		repository.insert(AppUserRow.forInsert("id-1", "Alice", "hashed-value"));

		assertThatThrownBy(() -> repository.insert(AppUserRow.forInsert("id-2", "alice", "another-hash")))
				.isInstanceOf(DuplicateKeyException.class);
	}

	@Test
	void theSameIdCannotBeInsertedTwice() {
		repository.insert(AppUserRow.forInsert("id-1", "Alice", "hashed-value"));

		assertThatThrownBy(() -> repository.insert(AppUserRow.forInsert("id-1", "bob", "another-hash")))
				.isInstanceOf(DuplicateKeyException.class);
	}
}
