package com.skillskeeper.skillskeeper.filestorage;

import org.springframework.core.io.Resource;

public record StoredFile(Resource resource, FileMetadata metadata) {
}
