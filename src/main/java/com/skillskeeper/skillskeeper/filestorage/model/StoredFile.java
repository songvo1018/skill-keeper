package com.skillskeeper.skillskeeper.filestorage.model;

import org.springframework.core.io.Resource;

public record StoredFile(Resource resource, FileMetadata metadata) {
}
