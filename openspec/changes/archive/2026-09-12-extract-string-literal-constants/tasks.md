## 1. Convention skill

- [x] 1.1 Create `.claude/skills/no-inline-concatenated-strings/SKILL.md` documenting the rule (inline string literals must not be concatenated directly in a method-call argument; the literal part must be a named constant in a package/domain constants class) with good/bad Java examples, following the same frontmatter format as `.claude/skills/correct-function-skill/SKILL.md`; verify the file exists

## 2. Constants class

- [x] 2.1 Create `com.skillskeeper.skillskeeper.filestorage.FileStorageMessages` with constants for: the "file not found" message prefix, the metadata write-failure and read-failure message prefixes, the storage-directory-creation-failure message prefix, the `.bin` and `.meta.json` filename suffixes, and the `Content-Disposition` attachment-filename template; verify with `./mvnw compile`

## 3. Refactor call sites

- [x] 3.1 Update `StoredFileNotFoundException` to build its message from `FileStorageMessages` instead of an inline literal concatenation
- [x] 3.2 Update `FileStorageService` to use `FileStorageMessages` constants for the `.bin`/`.meta.json` filename suffixes and the metadata write/read failure messages
- [x] 3.3 Update `FileStorageProperties` to use `FileStorageMessages` for the directory-creation-failure message
- [x] 3.4 Update `FileStorageController` to build the `Content-Disposition` header value via `String.format` with the `FileStorageMessages` template instead of inline literal concatenation

## 4. Verification

- [x] 4.1 Run `./mvnw test` and confirm all 11 existing tests still pass unmodified, proving behavior did not change
- [x] 4.2 Search the `filestorage` package for remaining inline string-literal concatenation in method-call arguments (e.g. `grep -rn '" +\|+ "' src/main/java/.../filestorage`) and confirm none remain outside `FileStorageMessages`
