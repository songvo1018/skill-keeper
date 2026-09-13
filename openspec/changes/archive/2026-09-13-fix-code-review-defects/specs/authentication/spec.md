## MODIFIED Requirements

### Requirement: Log in with username and password
The system shall accept a login request containing a username and password and, when the credential verification service approves them, respond with a bearer token that can be used to authenticate subsequent requests. The system shall bound the accepted length of both fields, and shall never disclose a submitted password.

#### Scenario: Successful login
- **WHEN** a client submits a login request with a non-blank username and non-blank password
- **THEN** the system responds with `200 OK` and a token in the response body

#### Scenario: Missing username or password
- **WHEN** a client submits a login request with a blank or missing username or password
- **THEN** the system responds with `400 Bad Request` and does not issue a token

#### Scenario: Credentials rejected
- **WHEN** a client submits a login request and the credential verification service does not approve the given username and password
- **THEN** the system responds with `401 Unauthorized` and does not issue a token

#### Scenario: Oversized credentials
- **WHEN** a client submits a login request whose username or password exceeds the configured maximum length
- **THEN** the system responds with `400 Bad Request` and does not issue a token

#### Scenario: Submitted password is never disclosed
- **WHEN** a login request is rejected for any reason, at any diagnostic verbosity the system can be configured to use
- **THEN** neither the response nor any diagnostic entry contains the submitted password

#### Scenario: Rejected login is recorded
- **WHEN** the system rejects a login attempt
- **THEN** the system records a diagnostic entry noting the rejection and its reason, without the submitted password

### Requirement: Protect endpoints with a bearer token
The system shall require every request to an endpoint other than login to carry a valid bearer token previously issued by login, in an `Authorization: Bearer <token>` header, and shall reject the request before it reaches that endpoint's normal handling if the token is missing or not recognized as valid. When the system accepts a token, it shall make the identity that obtained that token available to the endpoint's handling.

#### Scenario: No token provided
- **WHEN** a client calls any endpoint other than login without an `Authorization` header
- **THEN** the system responds with `401 Unauthorized` and does not perform the endpoint's normal handling

#### Scenario: Unrecognized token
- **WHEN** a client calls any endpoint other than login with an `Authorization: Bearer <token>` header whose token was not issued by login (or is no longer valid)
- **THEN** the system responds with `401 Unauthorized` and does not perform the endpoint's normal handling

#### Scenario: Valid token
- **WHEN** a client calls any endpoint other than login with an `Authorization: Bearer <token>` header carrying a token issued by a prior successful login
- **THEN** the system performs that endpoint's normal handling

#### Scenario: Login itself requires no token
- **WHEN** a client calls the login endpoint without an `Authorization` header
- **THEN** the system still evaluates the login request on its own merits, rather than rejecting it for a missing token

#### Scenario: Scheme name in any letter case
- **WHEN** a client presents a valid token with the authentication scheme name written in any mixture of upper and lower case, such as `bearer`
- **THEN** the system accepts the token and performs the endpoint's normal handling

#### Scenario: Rejection carries a challenge
- **WHEN** the system rejects a request because its token is missing or not valid
- **THEN** the `401 Unauthorized` response carries a `WWW-Authenticate` header naming the `Bearer` scheme

#### Scenario: Caller identity available to handling
- **WHEN** a request carrying a valid token reaches an endpoint's normal handling
- **THEN** the username that obtained that token is available to that handling

#### Scenario: Rejected request is recorded
- **WHEN** the system rejects a request for a missing or invalid token
- **THEN** the system records a diagnostic entry noting the rejection, without the presented token

## ADDED Requirements

### Requirement: Tokens expire and the token store stays bounded
The system shall treat an issued token as valid only for a configured time-to-live, and shall keep the number of retained tokens within a configured bound, so that repeated logins cannot exhaust the service's memory.

#### Scenario: Token used within its lifetime
- **WHEN** a client calls a protected endpoint with a token whose configured time-to-live has not yet elapsed
- **THEN** the system performs the endpoint's normal handling

#### Scenario: Token used after its lifetime
- **WHEN** a client calls a protected endpoint with a token whose configured time-to-live has elapsed
- **THEN** the system responds with `401 Unauthorized` and does not perform the endpoint's normal handling

#### Scenario: Sustained login volume
- **WHEN** clients log in repeatedly, far more times than the configured retention bound
- **THEN** the number of tokens the system retains stays within that bound, and a token issued by the most recent login is still accepted

#### Scenario: Expired tokens are released before live ones
- **WHEN** the system needs to make room for a newly issued token and the store holds tokens whose time-to-live has elapsed
- **THEN** the system releases those expired tokens rather than a token that is still within its time-to-live

### Requirement: Stub credential verification cannot run in production
The system shall not verify credentials with an implementation that approves any credentials when running under the production profile, and shall refuse to start rather than serve requests with such an implementation active there.

#### Scenario: Production profile without real credential verification
- **WHEN** the service is started with the production profile active and no real credential verification implementation is available
- **THEN** startup fails with an error naming the missing real credential verification, and the service does not accept requests

#### Scenario: Non-production profile
- **WHEN** the service is started without the production profile active
- **THEN** the stub implementation verifies credentials as before, and a login with any non-blank username and password succeeds
