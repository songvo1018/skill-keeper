## Why

The codebase currently builds several strings (error messages, generated filenames) by concatenating inline string literals directly at the method-call site (e.g. `"Failed to write metadata for file " + id`). This scatters wording across call sites, makes messages hard to find and reuse consistently, and has no enforced convention preventing new code from doing the same. Codifying the rule as a Claude Code skill — alongside fixing the current violations — keeps future code aligned automatically.

## What Changes

- Add a new Claude Code skill (`.claude/skills/no-inline-concatenated-strings/SKILL.md`) documenting the convention: an inline string literal must never be concatenated directly in a method-call argument; the literal must instead be declared as a named constant in a constants class for its package/domain, and the call site combines that constant with the variable part (e.g. `MESSAGES.FILE_WRITE_FAILED + id` or `String.format(MESSAGES.FILE_WRITE_FAILED, id)`).
- Add one constants class per existing domain package that needs one: `com.skillskeeper.skillskeeper.filestorage.FileStorageMessages` for the `filestorage` package's message/filename-suffix literals.
- Refactor the existing call sites in `filestorage` (exception messages in `FileStorageProperties`, `FileStorageService`, `StoredFileNotFoundException`; the `.bin`/`.meta.json` filename suffixes in `FileStorageService`; the `Content-Disposition` header value in `FileStorageController`) to use the new constants instead of inline literals.
- No behavior change: this is a pure refactor plus a new tooling skill file. `skip_specs: true` is set on this change since no spec-level behavior changes.

## Capabilities

### New Capabilities
(none — pure refactor, no behavior change)

### Modified Capabilities
(none — `file-storage`'s observable behavior is unchanged; see Impact)

## Impact

- New file: `.claude/skills/no-inline-concatenated-strings/SKILL.md` (convention doc, no runtime effect).
- New file: `src/main/java/com/skillskeeper/skillskeeper/filestorage/FileStorageMessages.java` (constants class).
- Modified: `FileStorageProperties.java`, `FileStorageService.java`, `StoredFileNotFoundException.java`, `FileStorageController.java` — call sites updated to reference constants instead of inline literals. No public API, HTTP contract, or stored-data format changes.
- Existing tests in `FileStorageServiceTest`, `FileStorageControllerTest`, `FileStorageUploadLimitsTest` must continue to pass unmodified (they assert on behavior, not on internal message wording), confirming no observable change.
