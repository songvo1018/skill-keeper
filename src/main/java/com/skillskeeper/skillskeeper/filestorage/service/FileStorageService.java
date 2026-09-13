package com.skillskeeper.skillskeeper.filestorage.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

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

import jakarta.annotation.PostConstruct;

@Service
public class FileStorageService implements FileStorage {

	private static final Logger log = LoggerFactory.getLogger(FileStorageService.class);

	private final Path baseDir;
	private final FileMetadataStore metadataStore;
	private final Map<String, FileMetadata> index = new ConcurrentHashMap<>();

	public FileStorageService(FileStorageProperties properties, FileMetadataStore metadataStore) {
		this.baseDir = properties.baseDir().toAbsolutePath().normalize();
		this.metadataStore = metadataStore;
	}

	@PostConstruct
	void initialize() {
		createBaseDir();
		loadIndexFromDisk();
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

		// The content is written under a temporary name and only moved into place once its metadata
		// is durable, so a failure part way through can never leave content without metadata behind.
		Path tempPath = baseDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX + FileStorageMessages.TEMP_FILE_SUFFIX);
		Path binPath = payloadPath(id);
		Path metaPath = metadataPath(id);

		try {
			file.transferTo(tempPath);
		}
		catch (IOException e) {
			deleteQuietly(tempPath);
			throw new FileStorageException("Failed to store uploaded file", e);
		}

		try {
			metadataStore.write(metaPath, metadata);
		}
		catch (RuntimeException e) {
			deleteQuietly(tempPath);
			throw e;
		}

		try {
			Files.move(tempPath, binPath, StandardCopyOption.ATOMIC_MOVE);
		}
		catch (IOException e) {
			deleteQuietly(tempPath);
			deleteQuietly(metaPath);
			throw new FileStorageException(FileStorageMessages.PAYLOAD_PUBLISH_FAILED_PREFIX + id, e);
		}

		index.put(id, metadata);
		return metadata;
	}

	/**
	 * Metadata comes from the index rather than from disk: it is already in memory, and reading it
	 * back would let the listing and this method disagree about what exists.
	 */
	@Override
	public StoredFile load(String id) {
		FileMetadata metadata = index.get(id);
		if (metadata == null) {
			throw new StoredFileNotFoundException(id);
		}

		Path binPath = resolveWithinBaseDir(id + FileStorageMessages.BIN_FILE_SUFFIX);
		if (!Files.isRegularFile(binPath)) {
			throw new StoredFileNotFoundException(id);
		}

		Resource resource = new FileSystemResource(binPath);
		return new StoredFile(resource, metadata);
	}

	@Override
	public List<FileMetadata> listFiles() {
		return List.copyOf(index.values());
	}

	private void createBaseDir() {
		try {
			Files.createDirectories(baseDir);
		}
		catch (IOException e) {
			throw new UncheckedIOException(FileStorageMessages.DIR_CREATE_FAILED_PREFIX + baseDir, e);
		}
	}

	private void loadIndexFromDisk() {
		String metaGlob = "*" + FileStorageMessages.META_FILE_SUFFIX;
		try (DirectoryStream<Path> metaFiles = Files.newDirectoryStream(baseDir, metaGlob)) {
			for (Path metaPath : metaFiles) {
				indexSidecar(metaPath);
			}
		}
		catch (IOException e) {
			throw new FileStorageException(FileStorageMessages.DIR_SCAN_FAILED_PREFIX + baseDir, e);
		}
	}

	/**
	 * Adds one sidecar to the index, or skips it. Anything a crash could have left behind — an
	 * unreadable file, valid JSON that does not describe a file, a sidecar whose content is gone —
	 * is logged and excluded rather than allowed to abort startup.
	 */
	private void indexSidecar(Path metaPath) {
		String fileName = metaPath.getFileName().toString();
		String id = fileName.substring(0, fileName.length() - FileStorageMessages.META_FILE_SUFFIX.length());

		FileMetadata metadata;
		try {
			metadata = metadataStore.read(metaPath);
		}
		catch (RuntimeException e) {
			log.warn("Skipping unreadable metadata record {}: {}", fileName, e.getMessage());
			return;
		}

		if (metadata.id() == null || metadata.id().isBlank() || !metadata.id().equals(id)) {
			log.warn("Skipping metadata record {}: it records id {}, which does not identify that file", fileName,
					metadata.id());
			return;
		}

		if (!Files.isRegularFile(payloadPath(id))) {
			log.warn("Skipping metadata record {}: no stored content accompanies it", fileName);
			return;
		}

		index.put(id, metadata);
	}

	private Path payloadPath(String id) {
		return baseDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX);
	}

	private Path metadataPath(String id) {
		return baseDir.resolve(id + FileStorageMessages.META_FILE_SUFFIX);
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
