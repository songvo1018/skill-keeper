package com.skillskeeper.skillskeeper.filestorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Service
public class FileStorageService {

	private final Path baseDir;
	private final ObjectMapper objectMapper;

	public FileStorageService(FileStorageProperties properties, ObjectMapper objectMapper) {
		this.baseDir = properties.baseDir();
		this.objectMapper = objectMapper;
	}

	public FileMetadata store(MultipartFile file) {
		if (file == null || file.isEmpty()) {
			throw new EmptyUploadException("Uploaded file must not be empty");
		}

		String id = UUID.randomUUID().toString();
		Path binPath = baseDir.resolve(id + FileStorageMessages.BIN_FILE_SUFFIX);
		Path metaPath = baseDir.resolve(id + FileStorageMessages.META_FILE_SUFFIX);
		FileMetadata metadata = new FileMetadata(id, sanitizeFilename(file.getOriginalFilename()),
				file.getContentType(), file.getSize());

		try {
			file.transferTo(binPath);
		} catch (IOException e) {
			throw new FileStorageException("Failed to store uploaded file", e);
		}
		try {
			objectMapper.writeValue(metaPath.toFile(), metadata);
		} catch (JacksonException e) {
			throw new FileStorageException(FileStorageMessages.METADATA_WRITE_FAILED_PREFIX + id, e);
		}

		return metadata;
	}

	public StoredFile load(String id) {
		Path binPath = resolveWithinBaseDir(id + FileStorageMessages.BIN_FILE_SUFFIX);
		Path metaPath = resolveWithinBaseDir(id + FileStorageMessages.META_FILE_SUFFIX);

		if (binPath == null || metaPath == null || !Files.isRegularFile(binPath) || !Files.isRegularFile(metaPath)) {
			throw new StoredFileNotFoundException(id);
		}

		FileMetadata metadata;
		try {
			metadata = objectMapper.readValue(metaPath.toFile(), FileMetadata.class);
		} catch (JacksonException e) {
			throw new FileStorageException(FileStorageMessages.METADATA_READ_FAILED_PREFIX + id, e);
		}

		Resource resource = new FileSystemResource(binPath);
		return new StoredFile(resource, metadata);
	}

	private Path resolveWithinBaseDir(String fileName) {
		Path resolved = baseDir.resolve(fileName).normalize();
		return resolved.startsWith(baseDir) ? resolved : null;
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
