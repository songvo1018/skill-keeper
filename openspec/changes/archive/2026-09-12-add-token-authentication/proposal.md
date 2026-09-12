## Why

Every endpoint in the API (`GET /api/hello`, `POST /api/files`, `GET /api/files`, `GET /api/files/{id}`) is callable by anyone with no identity check at all. Before any real credential store exists, the service needs a login endpoint that issues a token, and every other endpoint needs to start requiring that token.

## What Changes

- Add `POST /api/auth/login`, accepting a JSON `{ "username": ..., "password": ... }` body and returning `{ "token": ... }` on success.
- Add a credential-verification seam (`CredentialsVerifier` or similar) with a single stub implementation that approves any non-blank username/password pair — there is nothing yet to check credentials against, but the login endpoint is built to call this service rather than approve inline, so a real check can be swapped in later without changing the endpoint's contract.
- Issue an opaque, randomly generated token per successful login and track valid tokens in memory (no persistence, no expiry).
- **BREAKING**: require a valid `Authorization: Bearer <token>` header on every existing endpoint (`GET /api/hello`, `POST /api/files`, `GET /api/files`, `GET /api/files/{id}`). A request with no token or an unrecognized token gets `401 Unauthorized` instead of being handled. `POST /api/auth/login` itself stays open (no token required to log in).

## Non-Goals

- Real credential verification against a user store — there is no user store yet; the verifier stub approves any non-blank username/password on purpose.
- Token expiry, refresh, or logout/revocation — a token stays valid until the process restarts.
- Password hashing or any credential storage.
- Protecting Spring Boot Actuator endpoints (`/actuator/**`) — only the application's own `/api/*` endpoints are in scope.
- Rate limiting or brute-force protection on login.

## Capabilities

### New Capabilities
- `authentication`: login with username+password to obtain a bearer token, and the enforcement that gates every other endpoint behind that token.

### Modified Capabilities
(none — `file-storage`'s own request/response contract on success is unchanged; the new capability gates reachability before a request reaches any existing controller, so it is described once in `authentication` rather than duplicated into `file-storage`.)

## Impact

- New `auth` package (mirroring the existing `filestorage` package's style): controller, request/response records, the `CredentialsVerifier` seam and its always-approving stub, an in-memory token store, and a request interceptor that rejects unauthenticated calls.
- `HelloController` and `FileStorageController` are not modified directly; a new Spring MVC interceptor (registered via a new `WebMvcConfigurer`) enforces the token check ahead of them for every path except the login endpoint.
- No new dependencies: the token is an opaque server-generated string, not a JWT, so no security/JWT library is needed given nothing downstream verifies signed claims yet.
- Any existing API client (including manual testing/tooling) must call `POST /api/auth/login` first and start sending the returned token — this is the intentional breaking change.
