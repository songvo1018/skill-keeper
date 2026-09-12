## Why

Clients that upload files currently have no way to discover what is already stored — the only way to find a file is to already know its id from a prior upload response. A listing endpoint lets clients enumerate what the service holds.

## What Changes

- Add `GET /api/files`, returning the full metadata (id, original filename, content type, size) of every file currently stored, so clients can discover stored files without already knowing their ids.
- Maintain an in-memory index of stored file metadata (populated from disk on startup, kept up to date on every upload) so listing does not scan the storage directory on every request.
- No changes to the existing upload (`POST /api/files`) or download (`GET /api/files/{id}`) endpoints.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `file-storage`: adds a "List stored files" requirement (new capability behavior; existing requirements unchanged)

## Impact

- New code in the existing `filestorage` package: an in-memory index inside `FileStorageService` (populated at startup and updated on each upload), a `listFiles()` method reading from it, and a new controller method. The response reuses the existing `FileMetadata` record — no new response DTO.
- No changes to stored data format, upload, or download behavior.
- Assumption (minor, not asked about): the list is unordered and unpaginated — reasonable for the current single-directory, no-database storage model; revisit if the number of stored files grows large enough for this to matter.
