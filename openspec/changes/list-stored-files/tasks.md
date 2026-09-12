## 1. Service method

- [ ] 1.1 Add a `FileSummary` record (`id`, `originalFilename`) to the `filestorage` package; verify with `./mvnw compile`
- [ ] 1.2 Implement `FileStorageService.listFiles()` that scans the base directory for `*.meta.json` sidecars, reads each into a `FileSummary`, and skips (does not fail on) any sidecar that fails to parse; verify with a unit test covering: multiple stored files, an empty store, and a corrupted `.meta.json` sidecar being skipped

## 2. REST endpoint

- [ ] 2.1 Implement `GET /api/files` on `FileStorageController` returning `200 OK` with the list from `FileStorageService.listFiles()`; verify with an integration test (`@SpringBootTest` + `MockMvc`) asserting the ids and filenames of previously uploaded files appear in the response
- [ ] 2.2 Add an integration test asserting `GET /api/files` returns `200 OK` with an empty list when no files have been uploaded

## 3. Verification

- [ ] 3.1 Run `./mvnw test` and confirm all tests (new and existing) pass
- [ ] 3.2 Manually exercise `GET /api/files` against the running app (`./mvnw spring-boot:run`) with `curl` after uploading one or more files and confirm the listed ids/filenames match what was uploaded
