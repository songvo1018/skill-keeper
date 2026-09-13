## Why

A full-codebase review ([docs/reviews/2026-09-13-senior-java-review.md](../../../docs/reviews/2026-09-13-senior-java-review.md)) found two defects no existing test catches. A metadata sidecar holding `{}` aborts application startup: the record parses, then a null id reaches a `ConcurrentHashMap` — precisely the crash-recovery case the existing `catch` was written for. A file uploaded with a malformed `Content-Type` is accepted with `201`, then returns `500` on every download, permanently. Neither failure leaves a trace, because the codebase contains no logging at all.

This change closes the findings rated blocker, major, and minor-in-the-same-code-path (1–13, 16), and repairs the tests that cannot currently fail (20–25).

## What Changes

- Startup index scan survives any unusable metadata record instead of aborting, and logs what it skipped.
- Content type is validated on upload (`400`), and falls back to a generic type on download so already-stored files become retrievable.
- `Content-Disposition` is built with Spring's `ContentDisposition`, so quoted and non-ASCII filenames encode correctly; responses declare no content sniffing.
- An upload becomes atomic: both bytes and metadata persist, or nothing remains.
- The in-memory index becomes the single source of truth, so listing and download can no longer disagree.
- SLF4J logging for every failure hidden from the client.
- Tokens get a configured time-to-live and a bounded store.
- The `Bearer` scheme is matched case-insensitively (RFC 7235), `401` carries `WWW-Authenticate`, and the authenticated username reaches request handling.
- The always-approving credential stub cannot run under the production profile.
- Login fields are length-bounded; the submitted password stays out of responses and logs.
- Tautological and stub-pinning tests are repaired, tests stop writing into the repository, and each defect above gains a case.

## Non-Goals

- Per-owner file access — exposing the caller's identity is in scope as the enabler, the authorization rule is not (findings 8, 29).
- Spring Security or PostgreSQL migration (26, 29).
- Listing order, pagination, `createdAt` (14).
- `DELETE`, retention, or sweeping already-orphaned payloads (4 is fixed going forward only).
- Protecting `/actuator/**` and correcting the main spec wording that overstates coverage (28).
- Login rate limiting or lockout.
- Remaining inline message literals, absolute `base-dir`, `pom.xml` cleanup, CI, coverage and static-analysis plugins, package renaming, `HelloController`, OpenAPI, README (15, 17, 18, 19).
- Running in more than one replica (27).

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `file-storage`: the guarantees above on upload, download and listing; a new requirement makes failures diagnosable.
- `authentication`: scheme case-insensitivity, a challenge header, caller identity, credential limits; new requirements for bounded token expiry and confining the stub verifier.

## Impact

- `filestorage`: `FileStorageService`, `FileStorageController`, `FileStorageExceptionHandler`, `FileStorageProperties` (becomes a pure record), `FileStorageMessages` (template constant retires).
- `auth`: `TokenService` (keyed store with issue time and username), `AuthTokenInterceptor`, `AuthExceptionHandler`, `AlwaysApprovingCredentialsVerifier`, `LoginRequest`, plus new token/credential properties.
- **BREAKING**: a malformed declared content type now yields `400`, not `201`; a token stops working once its time-to-live elapses.
- No new dependencies (SLF4J/Logback ship with the starters).
- Every task cites the review finding it closes.
