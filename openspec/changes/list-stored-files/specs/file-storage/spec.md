## ADDED Requirements

### Requirement: List stored files
The system SHALL allow retrieving the id and original filename of every file currently in storage.

#### Scenario: Files exist
- **WHEN** a client requests the list of stored files and one or more files exist in storage
- **THEN** the system responds with `200 OK` and a list containing, for each stored file, its id and original filename

#### Scenario: No files stored
- **WHEN** a client requests the list of stored files and none exist in storage
- **THEN** the system responds with `200 OK` and an empty list
