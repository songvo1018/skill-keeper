## 1. In-memory index

- [ ] 1.1 Add a `ConcurrentHashMap<String, FileMetadata>` index field to `FileStorageService`, populated at construction time by scanning the base directory for `*.meta.json` sidecars (skipping any that fail to parse); verify with a unit test that pre-existing sidecar files (written directly to the temp storage dir before the service is constructed) appear once the service is constructed, simulating a restart
- [ ] 1.2 Update `store()` to add the new file's metadata to the index after a successful write; verify with a unit test that a file stored via `store()` is immediately visible via the index

## 2. Listing

- [ ] 2.1 Implement `FileStorageService.listFiles()` returning the current index values as a list of `FileMetadata`; verify with a unit test covering multiple stored files and an empty store
- [ ] 2.2 Implement `GET /api/files` on `FileStorageController` returning `200 OK` with the list from `FileStorageService.listFiles()`; verify with an integration test (`@SpringBootTest` + `MockMvc`) asserting the metadata of previously uploaded files appears in the response
- [ ] 2.3 Add an integration test asserting `GET /api/files` returns `200 OK` with an empty list when no files have been uploaded

## 3. Verification

- [ ] 3.1 Run `./mvnw test` and confirm all tests (new and existing) pass
- [ ] 3.2 Manually exercise `GET /api/files` against the running app (`./mvnw spring-boot:run`) with `curl` after uploading one or more files and confirm the listed metadata matches what was uploaded
