## Context

See [proposal.md](proposal.md) for motivation. Storage has no database: each stored file is a pair of sidecar files on disk, `<id>.bin` and `<id>.meta.json` (see [FileStorageService.java](../../../src/main/java/com/skillskeeper/skillskeeper/filestorage/FileStorageService.java)), written by `store()` and read by `load()`. `FileStorageService` is a `@Service` singleton, so an in-memory field on it lives for the lifetime of the application process.

## Goals / Non-Goals

**Goals:**
- Answer `listFiles()` from an in-memory structure, not a directory scan, so listing cost does not grow with request volume.
- Keep the index correct across an application restart: files uploaded in a previous run must still appear after the process restarts, since they are still physically on disk.
- Keep the index correct within a single run: every successful `store()` must be reflected in the next `listFiles()` call.

**Non-Goals:**
- Multi-instance consistency: if the service ever runs as more than one instance against the same storage directory, each instance's in-memory index only reflects uploads it personally handled. Out of scope — nothing in the project today suggests multiple instances share one storage directory.
- Removing entries from the index: there is no delete endpoint, so the index only ever grows.
- Pagination, sorting, or filtering — out of scope per proposal.

## Decisions

**In-memory index is a `ConcurrentHashMap<String, FileMetadata>` field on `FileStorageService`, keyed by file id.**
`FileStorageService` is already the single owner of both `store()` (writer) and the storage directory; adding the index here avoids introducing a new component. `ConcurrentHashMap` is used because Spring beans are singletons shared across request-handling threads, and both uploads (writes) and listings (reads) can happen concurrently.

**Populate the index once at startup by scanning the base directory for `*.meta.json` files, then keep it updated incrementally on every `store()`.**
This is what makes the "files uploaded before the current run" scenario in the spec hold: a directory scan happens exactly once per process lifetime (not per request), after which the index is authoritative and `store()` alone keeps it current. `load()` (download) is unaffected — it still reads its target file directly from disk, independent of the index.
Alternative considered: keep scanning the directory on every `listFiles()` call (the original design). Rejected per this round's direction — the point of the index is to avoid that per-request cost.

**A `.meta.json` sidecar that fails to parse during the one-time startup scan is skipped, not fatal.**
Same rationale as before: a corrupted or half-written sidecar from a prior crash (see `file-storage-service`'s design.md, which already accepted "orphaned `.bin` with no metadata" as a possible post-crash state) should not prevent the application from starting or make every other file unlistable. Because indexing is now startup-time only, this check runs once, not on every request.

**`listFiles()` returns the existing `FileMetadata` record directly — no new response DTO.**
Per this round's direction: reuse the same shape already returned by `POST /api/files`, rather than introducing a narrower listing-specific DTO.

## Risks / Trade-offs

- [Startup scan cost scales with the number of files already on disk] → One-time per process start, not per request; acceptable trade-off for eliminating per-request scanning.
- [In-memory index diverges from disk if a file is added to the storage directory by any means other than `store()`] → Out of scope: nothing outside this service writes to the storage directory today.
- [No delete endpoint means the index can only grow for the life of the process] → Matches storage itself, which also has no delete path yet; not a new limitation introduced by this change.
