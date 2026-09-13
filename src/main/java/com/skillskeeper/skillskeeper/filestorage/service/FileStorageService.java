package com.skillskeeper.skillskeeper.filestorage.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.skillskeeper.skillskeeper.filestorage.FileStorageMessages;
import com.skillskeeper.skillskeeper.filestorage.exception.EmptyUploadException;
import com.skillskeeper.skillskeeper.filestorage.exception.FileStorageException;
import com.skillskeeper.skillskeeper.filestorage.exception.InvalidContentTypeException;
import com.skillskeeper.skillskeeper.filestorage.exception.StoredFileNotFoundException;
import com.skillskeeper.skillskeeper.filestorage.model.FileMetadata;
import com.skillskeeper.skillskeeper.filestorage.model.FileStorageProperties;
import com.skillskeeper.skillskeeper.filestorage.model.StoredFile;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRepository;
import com.skillskeeper.skillskeeper.filestorage.repository.StoredFileRow;

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService implements FileStorage {

	private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

	private final Path baseDir;
	private final StoredFileRepository repository;

	public FileStorageService(FileStorageProperties properties, StoredFileRepository repository) {
		this.baseDir = properties.baseDir().toAbsolutePath().normalize();
		this.repository = repository;
	}

	@PostConstruct
	void initialize() {
		createBaseDir();
	}

	@Override
	public FileMetadata store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new EmptyUploadException("Uploaded file must not be empty");
		}

		String contentType = validatedContentType(file.getContentType());
		String id = UUID.randomUUID().toString();
		FileMetadata metadata = new FileMetadata(id, sanitizeFilename(file.getOriginalFilename()), contentType,
				file.getSize());

		// The content is published before its row is inserted, and the row is what makes a file
		// visible. A crash between the two therefore leaves content nobody can reach, rather than a
		// row promising a file that cannot be downloaded.
		Path tempPath = baseDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX + FileStorageMessages.TEMP_FILE_SUFFIX);
		Path binPath = payloadPath(id);

		try {
			file.transferTo(tempPath);
		}
		catch (IOException e) {
			deleteQuietly(tempPath);
			throw new FileStorageException("Failed to store uploaded file", e);
		}

		try {
			Files.move(tempPath, binPath, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e) {
			deleteQuietly(tempPath);
			throw new FileStorageException(FileStorageMessages.PAYLOAD_PUBLISH_FAILED_PREFIX + id, e);
		}

		try {
			repository.insert(StoredFileRow.forInsert(metadata));
		}
		catch (RuntimeException e) {
			deleteQuietly(binPath);
			throw new FileStorageException(FileStorageMessages.METADATA_WRITE_FAILED_PREFIX + id, e);
		}

		return metadata;
	}

	@Override
	public StoredFile load(String id) {
		FileMetadata metadata = repository.findById(id)
				.map(StoredFileRow::toMetadata)
				.orElseThrow(() -> new StoredFileNotFoundException(id));

		Path binPath = resolveWithinBaseDir(id + FileStorageMessages.BIN_FILE_SUFFIX);
		if (!Files.isRegularFile(binPath)) {
			throw new StoredFileNotFoundException(id);
		}

		Resource resource = new FileSystemResource(binPath);
		return new StoredFile(resource, metadata);
	}

	/**
	 * Rows whose content is no longer on disk are left out, so that every id this returns can be
	 * passed to {@link #load(String)}. The check costs one call per row and is done per request
	 * rather than once at startup, because content can go missing at any time.
	 */
	@Override
	public List<FileMetadata> listFiles() {
		return repository.findAllOrdered().stream()
				.filter(row -> Files.isRegularFile(payloadPath(row.id())))
				.map(StoredFileRow::toMetadata)
				.toList();
	}

	private void createBaseDir() {
		try {
			Files.createDirectories(baseDir);
		}
		catch (IOException e) {
			throw new UncheckedIOException(FileStorageMessages.DIR_CREATE_FAILED_PREFIX + baseDir, e);
		}
	}

	private Path payloadPath(String id) {
		return baseDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX);
	}

	private void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		}
		catch (IOException e) {
			log.warn("Could not remove leftover file {}: {}", path.getFileName(), e.getMessage());
		}
	}

	private String validatedContentType(String contentType) {
		if (contentType == null || contentType.isBlank()) {
			return null;
		}
		try {
			return MediaType.parseMediaType(contentType).toString();
		}
		catch (InvalidMediaTypeException e) {
			throw new InvalidContentTypeException(contentType);
		}
	}

	private Path resolveWithinBaseDir(String fileName) {
		Path resolved = baseDir.resolve(fileName).normalize();
		if (!resolved.startsWith(baseDir)) {
			throw new StoredFileNotFoundException(fileName);
		}
		return resolved;
	}

	private static String sanitizeFilename(String originalFilename) {
		if (originalFilename == null || originalFilename.isBlank()) {
			return "file";
		}
		String normalized = originalFilename.replace('\\', '/');
		int lastSlash = normalized.lastIndexOf('/');
		String name = lastSlash >= 0 ? normalized.substring(lastSlash + 1) : normalized;
		return name.isBlank() ? "file" : name;
	}
}
