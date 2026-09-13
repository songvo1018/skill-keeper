package com.skillskeeper.skillskeeper.filestorage.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.support.PostgresTestContainer;

/**
 * Exercises the table against a real PostgreSQL: the insert is hand-written SQL and both the
 * ordering and the owner filter live in {@code where} and {@code order by} clauses, so none of them
 * is proven by anything an in-memory double could check. Each test runs in a transaction that is
 * rolled back, so the tests do not see each other.
 */
@SpringBootTest
@Transactional
class StoredFileRepositoryTest {

	private static final String OWNER = "alice";

	private static final String OTHER_OWNER = "bob";

	@TempDir
	static Path tempDir;

	@Autowired
	private StoredFileRepository repository;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("app.file-storage.base-dir", () -> tempDir.toString());
		PostgresTestContainer.registerProperties(registry);
	}

	@Test
	void insertedRowIsReadBackUnchanged() {
		FileMetadata metadata = new FileMetadata("id-1", "отчёт.pdf", "application/pdf", 1234);

		repository.insert(StoredFileRow.forInsert(metadata, OWNER));

		StoredFileRow stored = repository.findById("id-1").orElseThrow();
		assertThat(stored.originalFilename()).isEqualTo("отчёт.pdf");
		assertThat(stored.contentType()).isEqualTo("application/pdf");
		assertThat(stored.sizeBytes()).isEqualTo(1234);
		assertThat(stored.ownerUsername()).isEqualTo(OWNER);
		assertThat(stored.createdAt()).isNotNull();
		assertThat(stored.toMetadata()).isEqualTo(metadata);
	}

	@Test
	void absentContentTypeIsStoredAndReadBackAsNull() {
		repository.insert(StoredFileRow.forInsert(new FileMetadata("id-null-type", "no-type.bin", null, 7), OWNER));

		StoredFileRow stored = repository.findById("id-null-type").orElseThrow();
		assertThat(stored.contentType()).isNull();
		assertThat(stored.toMetadata().contentType()).isNull();
	}

	@Test
	void insertingAnIdThatAlreadyExistsFails() {
		repository
				.insert(StoredFileRow.forInsert(new FileMetadata("duplicate-id", "first.txt", "text/plain", 1), OWNER));

		assertThatThrownBy(() -> repository.insert(
				StoredFileRow.forInsert(new FileMetadata("duplicate-id", "second.txt", "text/plain", 2), OWNER)))
				.isInstanceOf(DuplicateKeyException.class);
	}

	/**
	 * The ids are deliberately inserted out of alphabetical order: an implementation that dropped
	 * the {@code order by} and returned rows in whatever order the table yielded would be very
	 * likely to disagree with the insertion order asserted here.
	 */
	@Test
	void rowsAreListedOldestFirst() {
		insert("c-first", OWNER);
		insert("a-second", OWNER);
		insert("b-third", OWNER);

		List<String> ids = repository.findAllOrderedByOwner(OWNER).stream().map(StoredFileRow::id).toList();

		assertThat(ids).containsExactly("c-first", "a-second", "b-third");
	}

	@Test
	void listingIsEmptyWhenNothingIsStored() {
		assertThat(repository.findAllOrderedByOwner(OWNER)).isEmpty();
	}

	@Test
	void anotherUsersRowsAreNotListed() {
		insert("mine", OWNER);
		insert("theirs", OTHER_OWNER);

		List<String> ids = repository.findAllOrderedByOwner(OWNER).stream().map(StoredFileRow::id).toList();

		assertThat(ids).containsExactly("mine");
	}

	@Test
	void rowWithoutAnOwnerIsNotListed() {
		insert("ownerless", null);

		assertThat(repository.findAllOrderedByOwner(OWNER)).isEmpty();
		assertThat(repository.findAllOrderedByOwner(OTHER_OWNER)).isEmpty();
	}

	@Test
	void ownerIsMatchedRegardlessOfCase() {
		insert("mine", "Alice");

		List<String> ids = repository.findAllOrderedByOwner("aLICE").stream().map(StoredFileRow::id).toList();

		assertThat(ids).containsExactly("mine");
	}

	@Test
	void rowIsFoundByIdForItsOwner() {
		insert("mine", OWNER);

		assertThat(repository.findByIdAndOwner("mine", "ALICE")).isPresent();
	}

	@Test
	void rowIsNotFoundByIdForAnotherUser() {
		insert("theirs", OTHER_OWNER);

		assertThat(repository.findByIdAndOwner("theirs", OWNER)).isEmpty();
	}

	@Test
	void rowWithoutAnOwnerIsNotFoundById() {
		insert("ownerless", null);

		assertThat(repository.findByIdAndOwner("ownerless", OWNER)).isEmpty();
	}

	private void insert(String id, String owner) {
		repository.insert(StoredFileRow.forInsert(new FileMetadata(id, "file.txt", "text/plain", 1), owner));
	}
}
