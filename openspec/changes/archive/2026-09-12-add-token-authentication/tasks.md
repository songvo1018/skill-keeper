## 1. Login endpoint and credential seam

- [x] 1.1 Add the `auth` package with `LoginRequest` (username/password, `@NotBlank` on both) and a token response record; verify by writing a unit test that bean validation rejects a blank username or password.
- [x] 1.2 Add the `CredentialsVerifier` interface and its `AlwaysApprovingCredentialsVerifier` `@Service` implementation, approving any non-blank username/password; verify with a unit test asserting it approves representative inputs.
- [x] 1.3 Add a `TokenService` `@Service` backed by `ConcurrentHashMap.newKeySet()`: `issueToken()` generates and records a new opaque token, `isValid(String token)` checks membership; verify with a unit test covering issue-then-valid and an unrecognized token being invalid.
- [x] 1.4 Add `AuthController` with `POST /api/auth/login`: `@Valid` the request, call `CredentialsVerifier`, issue and return a token on approval (`200 OK`) or respond `401 Unauthorized` on rejection; verify with a `MockMvc` test for the 200 case and a controller unit test (mocking `CredentialsVerifier` to reject, since the real implementation always approves) for the 401 case, plus a `MockMvc` test that blank fields return `400`.

## 2. Enforce the token on every other endpoint

- [x] 2.1 Add `MissingOrInvalidTokenException` and an `AuthMessages` constants class (mirroring `FileStorageMessages`) for its message strings; verify the project compiles.
- [x] 2.2 Add `AuthTokenInterceptor implements HandlerInterceptor`: reads the `Authorization` header, extracts a `Bearer` token, and throws `MissingOrInvalidTokenException` when the header is absent or the token isn't valid per `TokenService`; verify with a unit test covering missing header, malformed header, invalid token, and valid token.
- [x] 2.3 Add `AuthWebConfig implements WebMvcConfigurer` registering `AuthTokenInterceptor` on `/api/**` with `/api/auth/login` excluded; verify with the integration test in 2.4.
- [x] 2.4 Add `AuthExceptionHandler` (`@RestControllerAdvice`) mapping `MissingOrInvalidTokenException` to a `401` `ProblemDetail`, matching `FileStorageExceptionHandler`'s style; verify with a `MockMvc` test hitting a protected endpoint with no token and asserting `401` with the expected `ProblemDetail` body.

## 3. Wire up and verify existing endpoints

- [x] 3.1 Add a `MockMvc` integration test asserting `GET /api/hello`, `POST /api/files`, `GET /api/files`, and `GET /api/files/{id}` each return `401` when called without a token, and succeed exactly as before when called with a token obtained from `POST /api/auth/login`; verify the test passes.
- [x] 3.2 Update the existing `FileStorageControllerTest` (and any other existing controller test) to first log in and attach the returned token to every request, since they currently call endpoints unauthenticated; verify with `mvn test` that the full suite passes.
- [x] 3.3 Manually run the application and exercise the flow with a real HTTP client (login, then call a protected endpoint with and without the token) to confirm observed responses match the `authentication` spec's scenarios.
