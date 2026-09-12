## Context

See [proposal.md](proposal.md) for motivation. The project is a fresh Spring Boot 4.1.1 (Java 25) service with `spring-boot-starter-webmvc` and `spring-boot-starter-validation` already present ([pom.xml](../../../pom.xml)); no persistence layer or object storage exists yet. This is the first feature added to the codebase, so there is no existing convention to follow for file id generation, storage layout, or error handling — this design sets it.

## Goals / Non-Goals

**Goals:**
- Define where and how files are laid out on local disk, and how file ids map to disk paths.
- Define the upload/download HTTP contract precisely enough to implement against the specs.
- Close off the path-traversal and unbounded-upload-size risks called out in the spec.

**Non-Goals:**
- No database or file metadata index — metadata is derived from what's stored on disk.
- No authentication/authorization on the endpoints (none exists elsewhere in the project yet).
- No file listing, deletion, or update endpoints — only upload and download, per proposal scope.
- No pluggable storage backends (e.g. S3) — local disk only.

## Decisions

**File id = generated UUID, not the original filename.**
Using the original filename as the storage key is what enables path traversal and collisions. A random UUID (`UUID.randomUUID()`) is used as both the file id returned to the client and the on-disk filename. The original filename is preserved only as metadata (sidecar file), never as part of a filesystem path.
Alternative considered: hash-of-content (content-addressable). Rejected — adds complexity (dedup semantics, hash collisions to reason about) not needed for this scope.

**Metadata stored as a sidecar `<id>.meta.json` next to `<id>.bin`.**
Each upload writes two files: `storage.base-dir/<id>.bin` (raw bytes) and `storage.base-dir/<id>.meta.json` (`{originalFilename, contentType, size}`). This avoids a database while still letting download restore the original `Content-Type` and `Content-Disposition`.
Alternative considered: encode metadata into the filename. Rejected — filenames have length/character limits and this reintroduces sanitization concerns.

**Storage directory is configurable via `app.file-storage.base-dir`, created on startup if missing.**
Defaults to `./data/files` under the working directory. Resolved to an absolute, normalized path once at startup; every id-to-path resolution is checked to still be a descendant of that resolved root before any file I/O (defense in depth beyond "ids are always UUIDs").

**Max upload size enforced via Spring's existing `spring.servlet.multipart.max-file-size` / `max-request-size`, surfaced as `413`.**
Reuses the container-level multipart limits already supported by `spring-boot-starter-webmvc` rather than a custom size check in application code, via a `MaxUploadSizeExceededException` handler mapped to `413`.

**Streaming download via `StreamingResponseBody` / `Resource`, not loading the whole file into a byte array.**
Keeps memory use independent of file size.

## Risks / Trade-offs

- [No auth on endpoints] → Acceptable for this change since no other endpoint in the project is authenticated yet; revisit when auth is introduced project-wide.
- [Disk fills up — no quota] → Out of scope for this change; mitigated only by the per-file max-size limit. Flag for a future change if usage grows.
- [Sidecar metadata file can get out of sync with the `.bin` file if the process crashes mid-write] → Write `.bin` fully, then `.meta.json`, so a crash leaves at worst an orphaned `.bin` with no metadata (treated as "not found" on download) rather than a mismatched name/type.

## Open Questions

- None — behavior needed to implement this change is fully specified above.
