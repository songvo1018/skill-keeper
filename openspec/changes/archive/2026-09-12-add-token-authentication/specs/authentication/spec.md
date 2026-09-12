## Purpose

Lets a client exchange a username and password for a bearer token, and requires that token on every other endpoint so the API is no longer open to anyone.

## ADDED Requirements

### Requirement: Log in with username and password
The system SHALL accept a login request containing a username and password and, when the credential verification service approves them, respond with a bearer token that can be used to authenticate subsequent requests.

#### Scenario: Successful login
- **WHEN** a client submits a login request with a non-blank username and non-blank password
- **THEN** the system responds with `200 OK` and a token in the response body

#### Scenario: Missing username or password
- **WHEN** a client submits a login request with a blank or missing username or password
- **THEN** the system responds with `400 Bad Request` and does not issue a token

#### Scenario: Credentials rejected
- **WHEN** a client submits a login request and the credential verification service does not approve the given username and password
- **THEN** the system responds with `401 Unauthorized` and does not issue a token

### Requirement: Protect endpoints with a bearer token
The system SHALL require every request to an endpoint other than login to carry a valid bearer token previously issued by login, in an `Authorization: Bearer <token>` header, and SHALL reject the request before it reaches that endpoint's normal handling if the token is missing or not recognized as valid.

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
