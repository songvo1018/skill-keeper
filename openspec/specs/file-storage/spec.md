# file-storage Specification

## Purpose

Lets clients persist arbitrary files on the service's local disk and retrieve them later by id, so other features can rely on durable file storage without managing the filesystem themselves.

## Requirements

### Requirement: Upload a file
The system SHALL accept a single-file multipart upload and persist it on local disk, returning a unique file id and metadata (id, original filename, content type, size) that can be used to retrieve the file later.

#### Scenario: Successful upload
- **WHEN** a client submits a non-empty multipart file
- **THEN** the system stores the file's content, filename, and content type, and responds with a `201 Created` containing the generated file id and metadata

#### Scenario: Empty file rejected
- **WHEN** a client submits an upload with no file part or a zero-byte file
- **THEN** the system responds with `400 Bad Request` and does not store anything

#### Scenario: Oversized file rejected
- **WHEN** a client submits a file larger than the configured maximum upload size
- **THEN** the system responds with `413 Payload Too Large` and does not store the file

### Requirement: Download a file by id
The system SHALL allow retrieving a previously uploaded file's raw content by its file id, restoring the original content type and filename.

#### Scenario: Successful download
- **WHEN** a client requests a file by an id that exists in storage
- **THEN** the system responds with `200 OK`, the file's original bytes as the body, the original `Content-Type`, and the original filename in `Content-Disposition`

#### Scenario: Unknown id
- **WHEN** a client requests a file by an id that does not exist in storage
- **THEN** the system responds with `404 Not Found`

### Requirement: Uploaded filenames are sanitized
The system SHALL prevent a client-supplied filename from causing access to any path outside the configured storage directory.

#### Scenario: Path traversal attempt
- **WHEN** a client uploads a file whose original filename contains path traversal segments (e.g. `../../etc/passwd`) or an absolute path
- **THEN** the system stores the file under a generated id within the configured storage directory only, without using the raw filename as a filesystem path, and returns the sanitized original filename as metadata

### Requirement: List stored files
The system SHALL allow retrieving the metadata (id, original filename, content type, size) of every file currently in storage.

#### Scenario: Files exist
- **WHEN** a client requests the list of stored files and one or more files exist in storage
- **THEN** the system responds with `200 OK` and a list containing, for each stored file, its id, original filename, content type, and size

#### Scenario: No files stored
- **WHEN** a client requests the list of stored files and none exist in storage
- **THEN** the system responds with `200 OK` and an empty list

#### Scenario: Files uploaded before the service last started
- **WHEN** a client requests the list of stored files and some of those files were uploaded before the service's current run started
- **THEN** those files still appear in the list, with the same metadata as if they had been uploaded during the current run
