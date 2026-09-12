## Context

See [proposal.md](proposal.md) for motivation. Storage has no database or in-memory index: each stored file is a pair of sidecar files on disk, `<id>.bin` and `<id>.meta.json` (see [FileStorageService.java](../../../src/main/java/com/skillskeeper/skillskeeper/filestorage/FileStorageService.java)), written by `store()` and read by `load()`. Listing must work from what's on disk.

## Goals / Non-Goals

**Goals:**
- Enumerate every file currently in storage by reading its `.meta.json` sidecar, without a database.
- Behave sensibly if a `.meta.json` sidecar is missing or unreadable (partial/failed prior write), rather than making the whole listing fail.

**Non-Goals:**
- Pagination, sorting, or filtering — out of scope per proposal (unordered, unpaginated is an accepted assumption for the current scale).
- Fixing or cleaning up orphaned/corrupt sidecar files — listing only reads, it does not repair storage.

## Decisions

**List by scanning the base directory for `*.meta.json` files, not `*.bin` files.**
The metadata file is what holds `id` and `originalFilename`; scanning for `.bin` files would still require reading each corresponding `.meta.json` anyway, so scanning for `.meta.json` directly avoids a second lookup per entry.

**Skip an entry whose `.meta.json` fails to parse, rather than failing the whole request.**
A single corrupted or half-written sidecar (e.g. from a crash mid-`store()`, per the crash scenario noted in the original file-storage design) should not make the entire listing endpoint return an error. The design.md for `file-storage-service` already accepted "orphaned `.bin` with no metadata" as a possible post-crash state; the symmetric case (unreadable `.meta.json`) is handled the same way here — silently excluded from the list.
Alternative considered: fail the whole request on any unreadable entry. Rejected — one bad file would make the entire store unlistable.

**Response DTO is a new lightweight record (`id`, `originalFilename`), not the existing `FileMetadata`.**
Matches the proposal's explicit scope (ids and names only); reusing `FileMetadata` would expose `contentType`/`size` that were not asked for and that require no extra cost to omit.

## Risks / Trade-offs

- [Directory scan cost grows linearly with the number of stored files, on every request] → Acceptable at current scale (no database, no pagination is the whole point of this iteration); revisit with an index or pagination if the store grows large.
- [Skipping unreadable entries silently could mask a real bug] → Acceptable per the crash-tolerance precedent already established for downloads (a missing/corrupt sidecar is treated as absent there too); nothing about this change makes that state easier to reach.
