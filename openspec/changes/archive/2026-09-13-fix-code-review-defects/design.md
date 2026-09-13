## Context

See [proposal.md](proposal.md) for motivation and [docs/reviews/2026-09-13-senior-java-review.md](../../../docs/reviews/2026-09-13-senior-java-review.md) for the authoritative description of every defect, numbered as `Finding N`. Task references below use those numbers.

The codebase is two feature packages (`filestorage`, `auth`) of small concrete classes, with errors expressed as custom `RuntimeException` subtypes mapped to `ProblemDetail` by package-scoped `@RestControllerAdvice`. Messages live in package-scoped constants classes (`FileStorageMessages`, `AuthMessages`) per the project's `no-inline-concatenated-strings` skill, and methods are expected to throw rather than return `null` per its `correct-function-skill`. `FileStorageService` holds a `ConcurrentHashMap` index built at construction from `*.meta.json` sidecars; `TokenService` holds a `ConcurrentHashMap.newKeySet()` of issued tokens. There is no logging, no security dependency and no database — metadata persistence is a JSON sidecar per payload file.

This change is the first one driven by a review rather than a feature request: it touches both packages, and every edit is a defect fix rather than new capability, so the guiding constraint is to preserve the existing architecture and idioms rather than rebuild on them.

## Goals / Non-Goals

**Goals:**
- Remove the two ways the service breaks on its own stored data: a startup abort on an unusable sidecar, and a permanently undownloadable file.
- Make an upload all-or-nothing, and make listing and download agree about what exists.
- Make every failure the service hides from a client visible to an operator.
- Close the token store's unbounded growth and the protocol-level mistakes in token enforcement, without adopting Spring Security yet.
- Make the test suite able to fail where it currently cannot.

**Non-Goals:** as listed in [proposal.md](proposal.md) — notably per-owner file access, Spring Security, PostgreSQL, listing order/pagination, a `DELETE` endpoint, `/actuator/**` coverage, rate limiting, and the infrastructure items (findings 14, 15, 17, 18, 19, 26, 27, 28, 29).

## Decisions

**Validate the declared content type at upload, and still fall back defensively at download.**
Finding 2 is a write-side defect showing up as a read-side failure: a malformed `Content-Type` is persisted verbatim and then blows up `MediaType.parseMediaType` on every download. Validating on `store()` turns it into an immediate `400`, which is where a client can still act on it. But sidecars written before this change already contain such values, so the download path also needs a fallback to `application/octet-stream` instead of propagating `InvalidMediaTypeException` — otherwise existing data stays permanently unreadable. Both halves are needed; either alone leaves a hole. Alternative considered: normalising the bad value at startup during the index scan — rejected, because it silently rewrites stored data and still leaves the write path accepting garbage.

**Build `Content-Disposition` with Spring's `ContentDisposition` builder, and retire the format-string constant.**
Finding 3: `sanitizeFilename` strips only path separators, so quotes, CR/LF and non-ASCII survive into a hand-formatted header. `ContentDisposition.attachment().filename(name, UTF_8)` does RFC 6266/5987 encoding and quoting correctly. This removes `FileStorageMessages.CONTENT_DISPOSITION_ATTACHMENT_TEMPLATE` — the `no-inline-concatenated-strings` rule is about literals concatenated at a call site, and a framework builder is not a literal, so retiring the constant respects the rule rather than bypassing it. Alternative considered: extending `sanitizeFilename` to strip more characters — rejected, because it degrades the filename the spec says to restore, where encoding preserves it.

**Make `store()` atomic with a temp file plus `ATOMIC_MOVE`, and compensate on metadata failure.**
Finding 4: bytes are written first, metadata second, so a failure between them leaves an invisible, undeletable payload. Writing the payload to a temporary name, then the sidecar, then moving the payload into place means a reader never observes a payload without its metadata; if the sidecar write fails, the temp file is deleted and the id never existed. Alternative considered: writing the sidecar first — rejected, because it produces the opposite inconsistency (a listed id with no content), which is the more visible failure to a client.

**The in-memory index becomes the single source of truth for metadata; `load()` reads only bytes.**
Finding 5: `listFiles()` reads the index while `load()` re-parses the sidecar, so the two can disagree in three distinct ways, and every download pays for a JSON parse it does not need. Serving metadata from the index removes the divergence and the I/O at once. The sidecar stays on disk as the durable record that rebuilds the index at startup — it just stops being read during request handling. Consequence accepted: a sidecar added to the directory by an external process mid-run is not visible until restart, which is already true of the listing today.

**The startup scan validates each record and survives anything unusable.**
Finding 1: `{}` parses into a record of nulls, so `JacksonException` never fires and `index.put(null, …)` aborts startup. The scan must (a) catch `RuntimeException`, not just `JacksonException`, (b) require a non-blank id that matches the sidecar's filename, and (c) require the payload file to exist, so a sidecar without content is not listed as downloadable. Each rejected record is logged and skipped. Keying the index by the filename-derived id rather than trusting the JSON body also closes the third row of the divergence table in Finding 5.

**Logging is SLF4J obtained via `LoggerFactory`, scoped to what the client is not told.**
Finding 6. No dependency is needed — Logback arrives with the starters. The rule applied: anything the service swallows or reduces to a generic `500` gets a `WARN` or `ERROR` with the file id and cause; rejected authentication gets a `WARN` without the credential. Successful requests are left to the container's access log rather than duplicated, so this does not become request tracing. `FileStorageException` also gains a handler in `FileStorageExceptionHandler`, so a storage failure is logged once at a known place rather than escaping to the container.

**`TokenService` becomes a keyed store with an issue instant, a configured TTL and a hard bound.**
Finding 7 alone is a documented non-goal of the original auth change, but combined with the always-approving verifier and no rate limiting it becomes unauthenticated memory exhaustion, which no non-goal covers. `Map<String, TokenRecord>` where `TokenRecord` carries the username and issue instant supports expiry, the bound, and Finding 8's identity need with one structure. Expiry is evaluated lazily on validation (a token past its TTL is invalid and removed) and the bound is enforced when issuing: expired entries are purged first, and only if the store is still at capacity is the oldest live entry evicted. Alternative considered: Caffeine with `expireAfterWrite` and `maximumSize` — rejected to keep the original change's zero-dependency decision, since `Instant` arithmetic over a `ConcurrentHashMap` covers exactly these two needs. Alternative considered: a `@Scheduled` sweeper — rejected as a second moving part for no gain when validation already touches every token.

**The caller's identity is published as a request attribute, not a `SecurityContext`.**
Finding 8: the interceptor currently answers only yes/no, so nothing downstream can know who is calling, and file ownership is therefore impossible. Setting the resolved username as a request attribute is the smallest change that matches the existing `HandlerInterceptor` design and unblocks a later ownership change. It deliberately stops short of authorization. This is where the review's escalation trigger (Finding 29) applies: the first requirement of the form "only the owner may see this" is the point to adopt Spring Security, not to grow this attribute into a home-made security context.

**The stub verifier is confined by profile, with an explicit failure rather than a missing bean.**
Finding 7: `AlwaysApprovingCredentialsVerifier` gets `@Profile("!prod")`. Relying on the resulting `NoSuchBeanDefinitionException` would technically fail fast but with a message that reads like a wiring mistake, so a `prod`-profile configuration declares a `CredentialsVerifier` that fails on creation with a message naming what is missing. The class name was chosen in the original change to be unmistakable to a reader; this makes it unmistakable to a deployment too.

**`FileStorageProperties` becomes a pure record; directory creation moves to the service.**
Finding 11: `Files.createDirectories` in a record's canonical constructor is a side effect inside a value object, it turns a missing property into an NPE during binding, and it forces tests to touch the filesystem to construct the value. The service already owns the directory's lifetime, so it creates it in `@PostConstruct` before the index scan. A missing `base-dir` becomes a binding failure naming the property.

**`resolveWithinBaseDir` throws instead of returning `null`.**
Finding 12, and a direct application of the project's own `correct-function-skill`. It throws `StoredFileNotFoundException`, which is what the caller already converts the `null` into, collapsing a four-part condition at the call site.

**Persistence uses its own `ObjectMapper`, not the injected web one.**
Finding 13: the sidecar format is an on-disk contract, and sharing Spring's HTTP mapper means a future API-wide JSON setting silently changes it and breaks reading existing files. A dedicated mapper created for the service pins the storage format independently of the wire format.

**`LoginRequest` gains `@Size` bounds and a redacted `toString()`.**
Finding 16: `@NotBlank` admits arbitrarily long values, and the record's generated `toString()` prints the password, which reaches logs through `BindingResult` at debug level. Bounds come from constants so the spec's limit is stated once.

**Tests: delete what cannot fail, add what the defects need, and isolate the storage directory.**
Findings 20–25. `AlwaysApprovingCredentialsVerifierTest` is deleted outright — it asserts `return true` four times and would actively oppose a real verifier later (21). The path-traversal assertion in `FileStorageControllerTest` is replaced by one that can fail: the file count in the storage directory, and that nothing appeared beside it (20). `LoginRequestTest` keeps its `ValidatorFactory` open for the class's lifetime instead of using a `Validator` from a closed factory (22). The storage-directory override moves to a shared base class so no test writes into the repository (23). The duplicate unauthorized assertions collapse into one place (25). `FileStorageUploadLimitsTest` gains a comment stating why it sends no token — the container rejects the oversized body before the interceptor runs — so the ordering it documents is deliberate rather than accidental (24).

## Risks / Trade-offs

- [A token now expires, which no client expects today] → Accepted and declared BREAKING in the proposal; the TTL is configurable with a conservative default, and the 401 is the same response a client must already handle after a restart.
- [Under login volume above the bound, a live token can be evicted and a working session dies] → Mitigated by purging expired entries before evicting live ones, so this only happens under sustained abuse, where losing a session is preferable to exhausting the heap. Revisit with rate limiting, which this change explicitly does not add.
- [Rejecting a malformed content type breaks a client that was uploading one successfully] → Intentional: the upload only appeared to succeed, since every later download of that file returned 500. The `400` surfaces the problem where it can be fixed.
- [Validating content type narrows what can be stored] → Only syntactic validity is required, not an allow-list, so any well-formed media type is still accepted. An allow-list would be a policy decision this change does not make.
- [Requiring the payload to exist before listing a sidecar hides records that the current build would list] → Intended by Finding 5: such an id is a guaranteed 404, so listing it is the bug.
- [Publishing the username as a request attribute is a weaker contract than a real security context] → Accepted deliberately as the minimum enabler; Finding 29 records the trigger for replacing it with Spring Security rather than extending it.
- [`@Profile("!prod")` assumes the production profile is literally named `prod`] → Recorded as an assumption; the failing `prod` configuration makes a misnamed profile visible the first time it is deployed, rather than silently re-enabling the stub.
- [Spec wording uses lowercase RFC-2119 keywords per `openspec/config.yaml`, while the existing main specs use uppercase `SHALL`] → The config rule is followed here; the inconsistency with already-written specs is noted for the project owner to settle, and is cosmetic either way.
