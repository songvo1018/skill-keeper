package com.skillskeeper.skillskeeper.filestorage.model;

public record FileMetadata(String id, String originalFilename, String contentType, long size) {
}
