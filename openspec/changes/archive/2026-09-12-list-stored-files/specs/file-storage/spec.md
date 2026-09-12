## ADDED Requirements

### Requirement: List stored files
The system SHALL allow retrieving the metadata (id, original filename, content type, size) of every file currently in storage.

#### Scenario: Files exist
- **WHEN** a client requests the list of stored files and one or more files exist in storage
- **THEN** the system responds with `200 OK` and a list containing, for each stored file, its id, original filename, content type, and size

#### Scenario: No files stored
- **WHEN** a client requests the list of stored files and none exist in storage
- **THEN** the system responds with `200 OK` and an empty list

#### Scenario: Files uploaded before the service last started
- **WHEN** a client requests the list of stored files and some of those files were uploaded before the service's current run started
- **THEN** those files still appear in the list, with the same metadata as if they had been uploaded during the current run
