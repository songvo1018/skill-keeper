package com.skillskeeper.skillskeeper.filestorage;

public record FileMetadata(String id, String originalFilename, String contentType, long size) {
}
