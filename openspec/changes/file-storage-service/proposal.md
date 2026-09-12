## Why

The project currently exposes no way to store or retrieve binary files. Several planned features (attachments, exports, user uploads) need a place to persist files on the server's local disk and a REST API to upload and download them. Introducing this now establishes the storage contract before other features start depending on it.

## What Changes

- Add a `FileStorageService` component that saves uploaded files to a configurable directory on local disk and reads them back by id.
- Add a REST controller exposing:
  - `POST /api/files` (multipart upload) — stores the file, returns a generated file id and metadata.
  - `GET /api/files/{id}` — streams the stored file back with its original content type and filename.
- Validate uploads (non-empty, size limit) and reject path traversal in filenames.
- Return `404` for unknown ids and `400`/`413` for invalid or oversized uploads.

## Capabilities

### New Capabilities
- `file-storage`: Uploading files to and downloading files from the service's local disk storage via REST.

### Modified Capabilities
(none)

## Impact

- New code: storage service, REST controller, DTOs, exception handling for this capability.
- New config: storage base directory and max upload size (`application.properties`).
- Dependencies: uses the existing `spring-boot-starter-webmvc` and `spring-boot-starter-validation` starters already in [pom.xml](../../../pom.xml). Adds `spring-boot-starter-json` — in Spring Boot 4 the webmvc starter no longer pulls Jackson transitively, and JSON request/response bodies require it.
- No existing specs or endpoints are affected.
