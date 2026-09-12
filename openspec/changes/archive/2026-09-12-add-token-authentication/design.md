## Context

See [proposal.md](proposal.md) for motivation. Today the codebase has no security dependency at all (`pom.xml` lists only `spring-boot-starter-webmvc`, `-validation`, `-json`, `-actuator`) and no `config`/`security` package — `filestorage` is the only feature package, built as small concrete classes with no interface layer (`FileStorageService` is a single `@Service`, called directly by `FileStorageController`; errors are custom `RuntimeException`s mapped to `ProblemDetail` by a package-scoped `@RestControllerAdvice`). This change is the first cross-cutting concern in the project — it has to run ahead of every existing controller (`HelloController`, `FileStorageController`) rather than living inside one feature package.

## Goals / Non-Goals

**Goals:**
- Gate every current `/api/**` endpoint behind a bearer token, and any future one by default, without requiring each new controller to opt in individually.
- Keep the login endpoint's contract stable even though credential verification isn't real yet, so a real check can replace the stub later without an API change.

**Non-Goals:**
- Real credential verification — the proposal explicitly asks for a stub that always approves; there is no user store to check against yet.
- Token expiry, refresh, or logout/revocation — not requested; a token is valid until the process restarts.
- Password hashing/storage — there is nothing to hash against yet.
- Protecting Spring Boot Actuator endpoints (`/actuator/**`) — out of scope; the proposal names the application's own `/api/*` endpoints only.
- Rate limiting or brute-force protection on login — not requested.

## Decisions

**Enforce the token with a plain Spring MVC `HandlerInterceptor`, not Spring Security.**
The project has zero security dependencies today and otherwise favors small concrete classes over framework machinery. The entire requirement is "does this request carry a header naming a token this process issued" — a `HandlerInterceptor` answers that directly. Spring Security would add a large new dependency and a different mental model (filter chains, `SecurityContext`, authentication providers) for a check this simple. Alternative considered: Spring Security with a custom `AuthenticationProvider` — rejected as disproportionate and inconsistent with the codebase's existing style.

**The token is an opaque, randomly generated string (`UUID.randomUUID()`), not a JWT.**
Validity only ever means "was this exact string issued by login and not yet forgotten" — an in-memory lookup answers that with no need to decode claims. A JWT would still need the same in-memory bookkeeping to support the non-goals list above (nothing here reads claims out of the token), so it would add a signing key and a library for no behavior gained. This also keeps the change's dependency footprint at zero, per the proposal's Impact section.

**Valid tokens live in a `Set<String>` backed by `ConcurrentHashMap.newKeySet()`, on a singleton token-store bean — the same shape `FileStorageService` already uses for its in-memory file index.**
`FileStorageService`'s `Map<String, FileMetadata> index` ([FileStorageService.java](../../../src/main/java/com/skillskeeper/skillskeeper/filestorage/FileStorageService.java)) is a Spring singleton field read and written from concurrent request-handling threads — exactly the shape a token set needs. Reusing a `ConcurrentHashMap`-backed structure keeps the new code idiomatic for this codebase rather than introducing a different concurrency primitive. Alternative considered: an external cache (Caffeine, Redis) — unjustified for an in-memory, single-instance, no-expiry set.

**`CredentialsVerifier` is an interface with one implementation, `AlwaysApprovingCredentialsVerifier` — the one deliberate exception to this codebase's "single concrete service" convention.**
The proposal explicitly asks for "an unimplemented service that currently always returns confirmation" — a seam meant to be replaced by a real credential check later without touching the login endpoint. Every other service in the codebase (`FileStorageService`) is a single concrete class because no second implementation is planned; this one is different by the user's own description, so the interface earns its place here specifically, rather than being added speculatively.

**A new `WebMvcConfigurer` registers the interceptor on `/api/**`, excluding exactly `/api/auth/login`.**
Scoping to `/api/**` leaves Actuator (mounted outside `/api`) untouched, matching the Non-Goals, without a second exclusion rule. Excluding only the login path — rather than allow-listing "public" paths — keeps the default closed: any endpoint added later under `/api/**` is protected unless someone deliberately excludes it.

**A missing or unrecognized token is reported the same way `filestorage` reports its errors: a `RuntimeException` subtype, caught by a new `@RestControllerAdvice` in the `auth` package, returning a `ProblemDetail` with `401 Unauthorized`.**
Spring still routes an exception thrown from a `HandlerInterceptor#preHandle` through the normal `@RestControllerAdvice`/`HandlerExceptionResolver` chain, so throwing (e.g.) `MissingOrInvalidTokenException` from the interceptor and mapping it in a new `AuthExceptionHandler` — same shape as the existing `FileStorageExceptionHandler` — keeps one consistent error-response convention across the whole API instead of introducing a second one.

**Login request validation reuses `spring-boot-starter-validation`** (`@NotBlank` on a `LoginRequest` record's fields, `@Valid` on the controller parameter) rather than hand-rolled blank checks.
The dependency is already declared in `pom.xml` but unused anywhere in the codebase today; using it for this avoids reinventing bean validation and adds no new dependency.

## Risks / Trade-offs

- [The token set only ever grows — nothing removes an entry] → Accepted: no logout/expiry was requested (Non-Goal). Revisit if the process is expected to run for a long time under heavy login volume.
- [Restarting the process invalidates every outstanding token, forcing every client to log in again] → Acceptable: tokens are explicitly in-memory only, matching the non-persistent parts of the existing file-storage index's behavior across restarts.
- [Any non-blank username/password logs in successfully, since the only verifier is a stub] → Intentional, per the proposal. The naming (`AlwaysApprovingCredentialsVerifier`) is chosen so this can't be mistaken for a real check by someone reading the code later.
- [A `HandlerInterceptor` only covers requests dispatched through Spring MVC's `DispatcherServlet`] → Not a concern today: every existing and planned endpoint is a `@RestController` method served by the one `DispatcherServlet`.
