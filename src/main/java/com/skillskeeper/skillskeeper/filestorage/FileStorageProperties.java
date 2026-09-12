package com.skillskeeper.skillskeeper.filestorage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(Path baseDir) {

	public FileStorageProperties {
		baseDir = baseDir.toAbsolutePath().normalize();
		try {
			Files.createDirectories(baseDir);
		} catch (IOException e) {
			throw new UncheckedIOException("Could not create file storage directory: " + baseDir, e);
		}
	}
}
