*Each task cites the `Finding N` it closes in [docs/reviews/2026-09-13-senior-java-review.md](../../../docs/reviews/2026-09-13-senior-java-review.md). Read that finding before starting the task — it carries the failing scenario and the file/line the defect lives on. Rationale for each chosen approach is in [design.md](design.md).*

## 1. Baseline

- [x] 1.1 Establish a green baseline before changing anything: confirm a JDK 25 toolchain is available (`java -version`), set `JAVA_HOME`, and run `./mvnw test`; record which tests pass. The review that produced this change was static — only JDK 21 was available on the review machine — so the current state of the suite is unverified and must be established first
- [x] 1.2 Add the reusable test support the later tasks depend on: a package-visible abstract base class (or a shared `@TestConfiguration`) that binds `app.file-storage.base-dir` to a JUnit `@TempDir` via `@DynamicPropertySource`, and a helper that logs in and returns an `Authorization` header value; verify by migrating `FileStorageControllerTest` onto it with no change to its assertions and confirming it still passes (Finding 23)

## 2. Startup survives unusable metadata (blocker, Finding 1)

- [x] 2.1 Harden `FileStorageService`'s startup scan so an unusable sidecar cannot abort startup: derive the id from the sidecar's filename rather than trusting the parsed body, reject a record whose parsed id is null, blank, or does not equal the filename-derived id, reject one whose payload file is absent, and catch `RuntimeException` rather than only `JacksonException`; verify with unit tests covering a sidecar containing `{}`, one containing invalid JSON, one whose body id disagrees with its filename, and one with no matching `.bin` — each must be skipped while a valid sidecar alongside it is still indexed
- [x] 2.2 Add an integration test asserting the application context starts successfully when the storage directory contains a sidecar holding `{}`, since the unit test alone does not prove startup is unaffected (Finding 1)

## 3. Content type is validated on write, tolerated on read (blocker, Finding 2)

- [x] 3.1 Add a new exception type for an unusable declared content type in the `filestorage` package, with its message in `FileStorageMessages`, and validate the uploaded file's declared content type in `FileStorageService.store()` before anything is written, throwing that exception when it is not a syntactically valid media type; verify with unit tests that a well-formed type is accepted and that values such as `foo`, `text/` and `a/b; charset="` are rejected with nothing written to the storage directory
- [x] 3.2 Map that exception to `400 Bad Request` in `FileStorageExceptionHandler` as a `ProblemDetail`, matching the existing handlers' shape; verify with an integration test uploading a malformed content type and asserting `400`
- [x] 3.3 Make `FileStorageController.download` fall back to `application/octet-stream` instead of propagating a parse failure, so sidecars written before task 3.1 stay retrievable; verify with a test that writes a sidecar with an uninterpretable content type directly into the storage directory and asserts the download returns `200` with the original bytes (Finding 2)

## 4. Download response headers (Finding 3)

- [x] 4.1 Replace the hand-formatted `Content-Disposition` in `FileStorageController` with `ContentDisposition.attachment().filename(name, StandardCharsets.UTF_8)`, and delete the now-unused `CONTENT_DISPOSITION_ATTACHMENT_TEMPLATE` from `FileStorageMessages`; verify with tests asserting a plain ASCII filename still yields the exact header the existing test expects, and that filenames containing a double quote and non-ASCII characters (e.g. `отчёт.pdf`) produce a well-formed encoded header with a `200` response
- [x] 4.2 Add `X-Content-Type-Options: nosniff` to the download response; verify with a test asserting the header is present (Finding 3)
- [x] 4.3 Add a real-server test (`TestRestTemplate`, hand-built multipart body with the filename's bytes written as UTF-8) asserting a non-ASCII filename is stored and returned unchanged; MockMvc cannot cover this, because it hands the filename over as a Java string instead of decoding a part header, and a `curl` check from a Windows shell cannot either, since the console may re-encode the argument before the client sees it (Finding 3)

## 5. Uploads become atomic (Finding 4)

- [x] 5.1 Rewrite `FileStorageService.store()` to write the payload to a temporary name, then write the sidecar, then move the payload into its final name with `Files.move(..., ATOMIC_MOVE)`, deleting the temporary payload if the sidecar write fails; verify with a unit test that injects a failing metadata write (e.g. a persistence mapper stub that throws) and asserts the thrown `FileStorageException`, that the storage directory is left with no files, and that the id is absent from both the listing and `load()` (Finding 4)

## 6. One source of truth for metadata (Finding 5)

- [x] 6.1 Change `FileStorageService.load()` to take metadata from the in-memory index and read only the payload bytes from disk, so listing and download cannot disagree and a download no longer parses JSON; verify with unit tests that a round trip still returns identical metadata and bytes, and that an id present on disk only as a sidecar (no payload) is neither listed nor loadable
- [x] 6.2 Add a test asserting that every id returned by `listFiles()` can be loaded successfully, after a mix of valid uploads and hand-planted broken records in the storage directory (Finding 5)

## 7. Failures become diagnosable (Finding 6)

- [x] 7.1 Introduce an SLF4J logger in `FileStorageService` and replace the empty `catch` that swallows unusable sidecars with a `WARN` naming the skipped file and the cause; verify by asserting the log output in a test (e.g. a Logback list appender or `OutputCaptureExtension`) for the `{}` sidecar case from task 2.1
- [x] 7.2 Add a handler for `FileStorageException` to `FileStorageExceptionHandler` that logs the cause at `ERROR` and returns a `500` `ProblemDetail` without exposing internal detail to the client; verify with a test asserting the status, that the response body does not contain the underlying exception message, and that the failure was logged
- [x] 7.3 Log rejected authentication: a `WARN` in `AuthTokenInterceptor` when a token is missing or invalid (without the presented token), and a `WARN` in the login path when credentials are rejected (without the password); verify with tests asserting both entries appear and that neither contains the token or password (Findings 6, 16)

## 8. Token lifetime and bounded store (Finding 7)

- [x] 8.1 Add configuration properties for the token time-to-live and the maximum number of retained tokens, bound as a record in the `auth` package with sensible defaults in `application.properties`; verify with a test asserting the values bind and that a missing property falls back to the documented default
- [x] 8.2 Replace `TokenService`'s `Set<String>` with a `ConcurrentHashMap<String, TokenRecord>` where `TokenRecord` holds the username and the issue instant, taking an injectable `Clock` so expiry is testable; keep `issueToken`/`isValid` behaviour for a fresh token unchanged so existing tests still pass; verify with the existing `TokenServiceTest` plus a new test that a token is valid before its TTL elapses
- [x] 8.3 Implement lazy expiry in validation — a token past its TTL is invalid and is removed from the store — verified with a test that advances the injected `Clock` past the TTL and asserts `isValid` returns false and the entry is gone
- [x] 8.4 Enforce the retention bound when issuing: purge expired entries first, and only if still at capacity evict the oldest live entry; verify with a test that issues well beyond the bound and asserts the store size never exceeds it and the most recently issued token is still valid, plus a test that an expired entry is released in preference to a live one
- [x] 8.5 Add an integration test asserting a protected endpoint returns `401` with a token whose TTL has elapsed, driving expiry through configuration (a very short TTL) rather than the injected clock (Finding 7)

## 9. Token enforcement protocol correctness (Findings 9, 10)

- [x] 9.1 Match the `Bearer` scheme name case-insensitively in `AuthTokenInterceptor` (e.g. `regionMatches(true, …)`), keeping the prefix constant as the single source of its spelling; verify with tests that `bearer <token>`, `BEARER <token>` and `Bearer <token>` all succeed while `Token <token>` and a bare token still fail (Finding 9)
- [x] 9.2 Make the `401` responses from `AuthExceptionHandler` carry `WWW-Authenticate: Bearer`, for both the missing/invalid token case and the rejected credentials case; verify with integration tests asserting the header on each (Finding 10)

## 10. Caller identity reaches request handling (Finding 8)

- [x] 10.1 Have `AuthTokenInterceptor` resolve the token to its `TokenRecord` and publish the username as a request attribute under a named constant, and expose a small accessor in the `auth` package for reading it; verify with a unit test on the interceptor and an integration test hitting a protected endpoint that reads the attribute and confirms it matches the username used at login. Do not add any authorization rule or file ownership here — that is a separate change (Findings 8, 29)

## 11. Stub verifier cannot run in production (Finding 7)

- [x] 11.1 Annotate `AlwaysApprovingCredentialsVerifier` with `@Profile("!prod")` and add a `prod`-profile configuration that declares a `CredentialsVerifier` failing on creation with a message naming the missing real verification; verify with a test asserting the context fails to start with the `prod` profile active and that the failure message names the real verifier, and a test asserting the default profile still starts and logs in successfully (Finding 7)

## 12. Login request hardening (Finding 16)

- [x] 12.1 Add `@Size` bounds to `LoginRequest`'s fields using constants for the limits, and override `toString()` so the password is redacted; verify with unit tests that an over-long username and an over-long password each produce a violation, that a value at the limit does not, and that `toString()` contains neither the password nor its characters
- [x] 12.2 Add an integration test asserting an over-long credential yields `400` and no token (Finding 16)

## 13. Storage code hygiene (Findings 11, 12, 13)

- [x] 13.1 Make `FileStorageProperties` a pure record — remove `Files.createDirectories` and the normalisation side effect from its constructor — and create/normalise the base directory in `FileStorageService` before the index scan (`@PostConstruct`), keeping the existing failure message constant; verify that `FileStorageServiceTest` can construct the properties without the directory existing, that the service creates it on startup, and that an absent `base-dir` property fails binding with a message naming the property (Finding 11)
- [x] 13.2 Change `resolveWithinBaseDir` to throw `StoredFileNotFoundException` instead of returning `null`, and simplify the call site's condition accordingly; verify the existing `loadPathTraversalIdThrowsNotFound` test still passes and add one asserting an id escaping the base directory throws rather than returning a value (Finding 12)
- [x] 13.3 Give the service its own `ObjectMapper` for sidecar persistence instead of the injected web mapper, created in one place so the storage format is pinned independently of the API's JSON configuration; verify by reading a sidecar written by the previous build and asserting the metadata round-trips unchanged (Finding 13)

## 14. Test suite repairs (Findings 20–25)

- [x] 14.1 Delete `AlwaysApprovingCredentialsVerifierTest` — it asserts `return true` and would require a future real verifier to approve empty credentials; confirm no other test depends on it (Finding 21)
- [x] 14.2 Replace the tautological assertion in `FileStorageControllerTest.uploadSanitizesPathTraversalFilenameAndStaysWithinBaseDir` with one that can fail: assert the storage directory holds exactly two files per successful upload and that no new entry appeared in the directory's parent; verify the test fails if the filename sanitisation is deliberately removed (Finding 20)
- [x] 14.3 Fix `LoginRequestTest` so the `Validator` is not taken from a closed `ValidatorFactory`: keep the factory in a static field and close it in `@AfterAll`; verify the class still passes (Finding 22)
- [x] 14.4 Move `AuthEnforcementTest`, `AuthLoginEndpointTest` and `SkillsKeeperApplicationTests` onto the shared temp-directory base class from task 1.2, then confirm no `./data` directory appears in the repository root after a full `./mvnw test` run (Finding 23)
- [x] 14.5 Add the comment `FileStorageUploadLimitsTest` is missing, stating that no token is sent because the container rejects the oversized body before the interceptor runs, so the documented `413`-before-`401` ordering is deliberate; do not change the assertion (Finding 24)
- [x] 14.6 Collapse the duplicated unauthorized-access assertions: keep the `ProblemDetail`-shape assertion in one place and remove the redundant `helloWithoutTokenIsUnauthorized` overlap, leaving the per-endpoint coverage in `ExistingEndpointsRequireTokenTest` intact (Finding 25)

## 15. Verification

- [x] 15.1 Run `./mvnw test` and confirm the whole suite passes, including every test added above, and that no test was weakened or removed except the deletions task 14.1 and 14.6 call for
- [x] 15.2 Confirm the suite leaves no artefacts on disk: after a full run, the repository contains no `data/` directory and no stray files outside `target/`
- [x] 15.3 Manually exercise the fixed paths against the running app (`./mvnw spring-boot:run`) with `curl`: log in, upload a file whose name contains a quote and Cyrillic characters and confirm the download filename survives, attempt an upload with a malformed `Content-Type` and confirm `400`, call a protected endpoint with `bearer` in lower case and confirm success, call one with no header and confirm `401` carries `WWW-Authenticate`. The non-ASCII filename cannot be checked this way from a Windows shell (the console re-encodes the argument before `curl` receives it, which looks like server-side corruption but is not); task 4.3 covers it automatically instead
- [x] 15.4 Stop the app, plant a sidecar containing `{}` in the storage directory, restart, and confirm the app starts, logs the skipped record, and lists the remaining files (Finding 1)
- [x] 15.5 Re-read [docs/reviews/2026-09-13-senior-java-review.md](../../../docs/reviews/2026-09-13-senior-java-review.md) and confirm every finding in the in-scope set (1–13, 16, 20–25) is closed or explicitly noted as deferred, and that no finding listed under this change's Non-Goals was changed by accident
