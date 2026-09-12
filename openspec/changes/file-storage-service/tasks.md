## 1. Configuration

- [ ] 1.1 Add `app.file-storage.base-dir` (default `./data/files`) and multipart limits (`spring.servlet.multipart.max-file-size`, `max-request-size`) to `application.properties`, and verify the app starts and creates the base directory on boot
- [ ] 1.2 Add a `FileStorageProperties` `@ConfigurationProperties` class binding `app.file-storage.base-dir`, and verify it resolves to an absolute, normalized path

## 2. Storage service

- [ ] 2.1 Implement `FileStorageService.store(MultipartFile)` that generates a UUID, writes `<id>.bin` and `<id>.meta.json` (originalFilename, contentType, size) under the base dir, and returns file metadata; verify with a unit test that a stored file's bytes and metadata round-trip
- [ ] 2.2 Implement `FileStorageService.load(String id)` that resolves `<id>.bin`/`<id>.meta.json`, verifies the resolved path is a descendant of the base dir, and returns the resource plus metadata or empty/not-found when missing; verify with a unit test covering an existing id, a missing id, and an id containing path-traversal characters (e.g. `../x`)
- [ ] 2.3 Reject empty uploads (no file part or zero bytes) in the service layer with a dedicated exception; verify with a unit test

## 3. REST API

- [ ] 3.1 Implement `POST /api/files` accepting a multipart file, delegating to `FileStorageService.store`, returning `201 Created` with `{id, originalFilename, contentType, size}`; verify with an integration test (`@SpringBootTest` + `MockMvc`) for the success case
- [ ] 3.2 Implement `GET /api/files/{id}` streaming the file back with the original `Content-Type` and a `Content-Disposition: attachment; filename="..."` header; verify with an integration test that downloaded bytes and headers match what was uploaded
- [ ] 3.3 Add exception handling (`@RestControllerAdvice`) mapping: empty upload → `400`, `MaxUploadSizeExceededException` → `413`, unknown id → `404`; verify with integration tests for each case
- [ ] 3.4 Add an integration test asserting a filename containing path-traversal segments is stored safely and does not escape the configured base directory

## 4. Verification

- [ ] 4.1 Run `./mvnw test` and confirm all new and existing tests pass
- [ ] 4.2 Manually exercise `POST /api/files` and `GET /api/files/{id}` against the running app (`./mvnw spring-boot:run`) with `curl` and confirm the round trip works end to end
