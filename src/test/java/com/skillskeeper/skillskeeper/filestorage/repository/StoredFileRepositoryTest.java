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
 * Exercises the table against a real PostgreSQL: the insert is hand-written SQL and the ordering
 * lives in an {@code order by} clause, so neither is proven by anything an in-memory double could
 * check. Each test runs in a transaction that is rolled back, so the tests do not see each other.
 */
@SpringBootTest
@Transactional
class StoredFileRepositoryTest {

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

		repository.insert(StoredFileRow.forInsert(metadata));

		StoredFileRow stored = repository.findById("id-1").orElseThrow();
		assertThat(stored.originalFilename()).isEqualTo("отчёт.pdf");
		assertThat(stored.contentType()).isEqualTo("application/pdf");
		assertThat(stored.sizeBytes()).isEqualTo(1234);
		assertThat(stored.createdAt()).isNotNull();
		assertThat(stored.toMetadata()).isEqualTo(metadata);
	}

	@Test
	void absentContentTypeIsStoredAndReadBackAsNull() {
		repository.insert(StoredFileRow.forInsert(new FileMetadata("id-null-type", "no-type.bin", null, 7)));

		StoredFileRow stored = repository.findById("id-null-type").orElseThrow();
		assertThat(stored.contentType()).isNull();
		assertThat(stored.toMetadata().contentType()).isNull();
	}

	@Test
	void insertingAnIdThatAlreadyExistsFails() {
		repository.insert(StoredFileRow.forInsert(new FileMetadata("duplicate-id", "first.txt", "text/plain", 1)));

		assertThatThrownBy(() -> repository
				.insert(StoredFileRow.forInsert(new FileMetadata("duplicate-id", "second.txt", "text/plain", 2))))
				.isInstanceOf(DuplicateKeyException.class);
	}

	/**
	 * The ids are deliberately inserted out of alphabetical order: an implementation that dropped
	 * the {@code order by} and returned rows in whatever order the table yielded would be very
	 * likely to disagree with the insertion order asserted here.
	 */
	@Test
	void rowsAreListedOldestFirst() {
		repository.insert(StoredFileRow.forInsert(new FileMetadata("c-first", "c.txt", "text/plain", 1)));
		repository.insert(StoredFileRow.forInsert(new FileMetadata("a-second", "a.txt", "text/plain", 1)));
		repository.insert(StoredFileRow.forInsert(new FileMetadata("b-third", "b.txt", "text/plain", 1)));

		List<String> ids = repository.findAllOrdered().stream().map(StoredFileRow::id).toList();

		assertThat(ids).containsExactly("c-first", "a-second", "b-third");
	}

	@Test
	void listingIsEmptyWhenNothingIsStored() {
		assertThat(repository.findAllOrdered()).isEmpty();
	}
}
