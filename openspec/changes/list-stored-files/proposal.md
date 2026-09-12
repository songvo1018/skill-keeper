## Why

Clients that upload files currently have no way to discover what is already stored — the only way to find a file is to already know its id from a prior upload response. A listing endpoint lets clients enumerate what the service holds.

## What Changes

- Add `GET /api/files`, returning the id and original filename of every file currently stored, so clients can discover stored files without already knowing their ids.
- No changes to the existing upload (`POST /api/files`) or download (`GET /api/files/{id}`) endpoints.

## Capabilities

### New Capabilities
(none)

### Modified Capabilities
- `file-storage`: adds a "List stored files" requirement (new capability behavior; existing requirements unchanged)

## Impact

- New code in the existing `filestorage` package: a listing method on `FileStorageService` and a new controller method, plus a lightweight response DTO holding just `id` and `originalFilename` (the full `FileMetadata` also carries `contentType`/`size`, which the request does not ask the listing to expose).
- No changes to stored data format, upload, or download behavior.
- Assumption (minor, not asked about): the list is unordered and unpaginated — reasonable for the current single-directory, no-database storage model; revisit if the number of stored files grows large enough for this to matter.
