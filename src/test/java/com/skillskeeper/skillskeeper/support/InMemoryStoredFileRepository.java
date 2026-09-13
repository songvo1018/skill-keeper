package com.skillskeeper.skillskeeper.support;

import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.dao.DuplicateKeyException;

import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRepository;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRow;

/**
 * A stand-in for {@link StoredFileRepository} in tests that are about the service's own logic
 * rather than about SQL. What the real table does and this does not - the insert statement, the
 * ordering clause, the unique constraint - is covered against a real PostgreSQL by
 * {@code StoredFileRepositoryTest}.
 *
 * <p>Only the methods the production code calls are implemented; the rest of {@code CrudRepository}
 * throws, so a test that starts depending on one fails loudly instead of quietly passing.
 */
public class InMemoryStoredFileRepository implements StoredFileRepository {

	private static final String UNSUPPORTED = "Not implemented by the in-memory test repository";

	private final Map<String, StoredFileRow> rows = new LinkedHashMap<>();

	private Instant nextCreatedAt = Instant.parse("2026-01-01T00:00:00Z");

	@Override
	public void insert(StoredFileRow row) {
		if (rows.containsKey(row.id())) {
			throw new DuplicateKeyException(row.id());
		}
		rows.put(row.id(), new StoredFileRow(row.id(), row.originalFilename(), row.contentType(), row.sizeBytes(),
				row.ownerUsername(), nextCreatedAt));
		nextCreatedAt = nextCreatedAt.plusMillis(1);
	}

	@Override
	public void insert(String id, String originalFilename, String contentType, long sizeBytes,
			String ownerUsername) {
		insert(new StoredFileRow(id, originalFilename, contentType, sizeBytes, ownerUsername, null));
	}

	/**
	 * Matches the query it stands in for: names are compared without regard to letter case, and a
	 * row with no owner belongs to nobody and so matches no name.
	 */
	@Override
	public List<StoredFileRow> findAllOrderedByOwner(String owner) {
		return rows.values().stream()
				.filter(row -> isOwnedBy(row, owner))
				.sorted(Comparator.comparing(StoredFileRow::createdAt).thenComparing(StoredFileRow::id))
				.toList();
	}

	@Override
	public Optional<StoredFileRow> findByIdAndOwner(String id, String owner) {
		return findById(id).filter(row -> isOwnedBy(row, owner));
	}

	@Override
	public Optional<StoredFileRow> findById(String id) {
		return Optional.ofNullable(rows.get(id));
	}

	private static boolean isOwnedBy(StoredFileRow row, String owner) {
		return row.ownerUsername() != null && row.ownerUsername().equalsIgnoreCase(owner);
	}

	@Override
	public boolean existsById(String id) {
		return rows.containsKey(id);
	}

	@Override
	public long count() {
		return rows.size();
	}

	@Override
	public <S extends StoredFileRow> S save(S entity) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public <S extends StoredFileRow> Iterable<S> saveAll(Iterable<S> entities) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public Iterable<StoredFileRow> findAll() {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public Iterable<StoredFileRow> findAllById(Iterable<String> ids) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void deleteById(String id) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void delete(StoredFileRow entity) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void deleteAllById(Iterable<? extends String> ids) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void deleteAll(Iterable<? extends StoredFileRow> entities) {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}

	@Override
	public void deleteAll() {
		throw new UnsupportedOperationException(UNSUPPORTED);
	}
}
