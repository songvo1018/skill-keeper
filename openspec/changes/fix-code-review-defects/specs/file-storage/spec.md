## MODIFIED Requirements

### Requirement: Upload a file
The system shall accept a single-file multipart upload and persist it on local disk, returning a unique file id and metadata (id, original filename, content type, size) that can be used to retrieve the file later. An upload shall either persist both the file's content and its metadata, or leave nothing behind.

#### Scenario: Successful upload
- **WHEN** a client submits a non-empty multipart file
- **THEN** the system stores the file's content, filename, and content type, and responds with a `201 Created` containing the generated file id and metadata

#### Scenario: Empty file rejected
- **WHEN** a client submits an upload with no file part or a zero-byte file
- **THEN** the system responds with `400 Bad Request` and does not store anything

#### Scenario: Oversized file rejected
- **WHEN** a client submits a file larger than the configured maximum upload size
- **THEN** the system responds with `413 Payload Too Large` and does not store the file

#### Scenario: Malformed content type rejected
- **WHEN** a client submits a file whose declared content type is not a syntactically valid media type
- **THEN** the system responds with `400 Bad Request` and does not store the file

#### Scenario: Upload fails partway through persisting
- **WHEN** the system has written an uploaded file's content but then fails to persist that file's metadata
- **THEN** the request fails, no content for that file remains in the storage directory, and the generated id appears in neither the listing nor a successful download

### Requirement: Download a file by id
The system shall allow retrieving a previously uploaded file's raw content by its file id, restoring the original content type and filename in a response whose headers are well-formed for any filename the system accepted.

#### Scenario: Successful download
- **WHEN** a client requests a file by an id that exists in storage
- **THEN** the system responds with `200 OK`, the file's original bytes as the body, the original `Content-Type`, and the original filename in `Content-Disposition`

#### Scenario: Unknown id
- **WHEN** a client requests a file by an id that does not exist in storage
- **THEN** the system responds with `404 Not Found`

#### Scenario: Filename that cannot appear literally in a header
- **WHEN** a client requests a file whose original filename contains a double quote, a non-ASCII character, or any other character that cannot be placed literally into an HTTP header
- **THEN** the system responds with `200 OK` and a `Content-Disposition` header that encodes the filename so the client can recover it unambiguously, rather than failing the request or emitting a malformed header

#### Scenario: Stored content type is no longer usable
- **WHEN** a client requests a file whose recorded content type cannot be interpreted as a media type, because it was stored before that value was validated
- **THEN** the system responds with `200 OK` and the file's original bytes, using a generic binary content type, rather than failing the request

#### Scenario: Content type sniffing prevented
- **WHEN** the system serves a stored file's content
- **THEN** the response instructs the client not to infer a content type other than the one the response declares

### Requirement: List stored files
The system shall allow retrieving the metadata (id, original filename, content type, size) of every file currently in storage, and every id it reports shall be retrievable.

#### Scenario: Files exist
- **WHEN** a client requests the list of stored files and one or more files exist in storage
- **THEN** the system responds with `200 OK` and a list containing, for each stored file, its id, original filename, content type, and size

#### Scenario: No files stored
- **WHEN** a client requests the list of stored files and none exist in storage
- **THEN** the system responds with `200 OK` and an empty list

#### Scenario: Files uploaded before the service last started
- **WHEN** a client requests the list of stored files and some of those files were uploaded before the service's current run started
- **THEN** those files still appear in the list, with the same metadata as if they had been uploaded during the current run

#### Scenario: Listing agrees with download
- **WHEN** a client requests the list of stored files
- **THEN** every id in the response can be downloaded successfully, and any file whose stored content is missing does not appear in the list

#### Scenario: Unusable metadata does not prevent startup
- **WHEN** the service starts and the storage directory contains a metadata record that cannot be read, cannot be interpreted, or does not identify the file it belongs to
- **THEN** the service starts successfully and serves requests, that record is excluded from the listing, and the remaining files are listed as normal

## ADDED Requirements

### Requirement: Storage failures are diagnosable
The system shall record a diagnostic entry for every storage failure whose cause it does not report to the client, so that an operator can determine what happened without reproducing it.

#### Scenario: Failure reduced to a generic error
- **WHEN** the system abandons a storage operation and answers the client with an error that does not name the underlying cause
- **THEN** the system records a diagnostic entry containing the underlying cause and, when known, the file id

#### Scenario: Metadata record skipped
- **WHEN** the system excludes a metadata record from the listing because it is unusable
- **THEN** the system records a diagnostic entry identifying the skipped record and why it was skipped
