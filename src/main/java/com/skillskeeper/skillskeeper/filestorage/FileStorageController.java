package com.skillskeeper.skillskeeper.filestorage;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
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

	private static final String CONTENT_TYPE_OPTIONS_HEADER = "X-Content-Type-Options";
	private static final String NO_SNIFF = "nosniff";

	private final FileStorageService fileStorageService;

	public FileStorageController(FileStorageService fileStorageService) {
		this.fileStorageService = fileStorageService;
	}

	@PostMapping("/api/files")
	public ResponseEntity<FileMetadata> upload(@RequestParam("file") MultipartFile file) {
		FileMetadata metadata = fileStorageService.store(file);
		return ResponseEntity.status(HttpStatus.CREATED).body(metadata);
	}

	@GetMapping("/api/files")
	public ResponseEntity<List<FileMetadata>> list() {
		return ResponseEntity.ok(fileStorageService.listFiles());
	}

	@GetMapping("/api/files/{id}")
	public ResponseEntity<Resource> download(@PathVariable String id) {
		StoredFile storedFile = fileStorageService.load(id);
		FileMetadata metadata = storedFile.metadata();

		return ResponseEntity.ok()
				.contentType(resolveContentType(metadata.contentType()))
				.header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition(metadata.originalFilename()))
				.header(CONTENT_TYPE_OPTIONS_HEADER, NO_SNIFF)
				.body(storedFile.resource());
	}

	/**
	 * Files stored before the content type was validated on upload can hold a value that is not a
	 * media type at all; such a file is still served, as bytes, rather than failing the request.
	 */
	private static MediaType resolveContentType(String contentType) {
		if (contentType == null) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
		try {
			return MediaType.parseMediaType(contentType);
		}
		catch (InvalidMediaTypeException e) {
			return MediaType.APPLICATION_OCTET_STREAM;
		}
	}

	/**
	 * A printable US-ASCII filename is emitted as a plain quoted {@code filename} (quotes escaped);
	 * anything else additionally gets the RFC 5987 {@code filename*} form, which is what makes
	 * non-ASCII names survive the trip instead of arriving as mojibake or breaking the header.
	 */
	private static String contentDisposition(String filename) {
		ContentDisposition.Builder builder = ContentDisposition.attachment();
		boolean printableAscii = filename.chars().allMatch(c -> c >= 0x20 && c < 0x7F);
		ContentDisposition disposition = printableAscii
				? builder.filename(filename).build()
				: builder.filename(filename, StandardCharsets.UTF_8).build();
		return disposition.toString();
	}
}
