package com.skillskeeper.skillskeeper.filestorage.model;

import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;

/**
 * A plain value object: creating the directory is {@link FileStorageService}'s job, so that binding
 * this record has no side effects and a missing property fails binding by name rather than later.
 */
@Validated
@ConfigurationProperties(prefix = "app.file-storage")
public record FileStorageProperties(@NotNull Path baseDir) {
}
