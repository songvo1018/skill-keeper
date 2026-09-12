package com.skillskeeper.skillskeeper.filestorage;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
public class FileStorageController {

	private final FileStorageService fileStorageService;

	public FileStorageController(FileStorageService fileStorageService) {
		this.fileStorageService = fileStorageService;
	}

	@PostMapping("/api/files")
	public ResponseEntity<FileMetadata> upload(@RequestParam("file") MultipartFile file) {
		FileMetadata metadata = fileStorageService.store(file);
		return ResponseEntity.status(HttpStatus.CREATED).body(metadata);
	}

	@GetMapping("/api/files/{id}")
	public ResponseEntity<Resource> download(@PathVariable String id) {
		StoredFile storedFile = fileStorageService.load(id);
		FileMetadata metadata = storedFile.metadata();
		MediaType contentType = metadata.contentType() != null
				? MediaType.parseMediaType(metadata.contentType())
				: MediaType.APPLICATION_OCTET_STREAM;

		return ResponseEntity.ok()
				.contentType(contentType)
				.header(HttpHeaders.CONTENT_DISPOSITION,
						String.format(FileStorageMessages.CONTENT_DISPOSITION_ATTACHMENT_TEMPLATE, metadata.originalFilename()))
				.body(storedFile.resource());
	}
}
