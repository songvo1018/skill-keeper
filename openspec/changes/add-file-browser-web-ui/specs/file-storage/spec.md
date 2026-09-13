## ADDED Requirements

### Requirement: У сохранённого файла есть владелец
Система должна сохранять вместе с метаданными файла имя пользователя, чей токен сопровождал загрузку, и хранить его в той же таблице PostgreSQL, что и остальные метаданные, добавляя колонку версионированной миграцией. Владелец не должен меняться после сохранения. Файлы, сохранённые до появления владельца, остаются без него, и такой файл не должен считаться принадлежащим кому-либо.

#### Scenario: Владелец записан при загрузке
- **WHEN** клиент с валидным токеном успешно загружает файл
- **THEN** система сохраняет имя пользователя, обладающего этим токеном, как владельца файла

#### Scenario: Владелец переживает перезапуск
- **WHEN** файл загружен, сервис перезапущен, и тот же пользователь запрашивает список файлов
- **THEN** файл присутствует в списке и скачивается по своему id

#### Scenario: Схема дополняется миграцией
- **WHEN** сервис стартует с базой, где метаданные уже есть, а колонки владельца ещё нет
- **THEN** он применяет миграцию, добавляющую колонку, запускается успешно и не теряет существующие записи

#### Scenario: Файл, сохранённый до появления владельца
- **WHEN** в базе есть метаданные файла без владельца
- **THEN** этот файл не появляется в списке ни у одного пользователя и не скачивается ни по чьему токену

## MODIFIED Requirements

### Requirement: Upload a file
The system shall accept a single-file multipart upload and persist it on local disk, returning a unique file id and metadata (id, original filename, content type, size) that can be used to retrieve the file later. The system shall record the username that obtained the request's token as the file's owner. An upload shall either persist both the file's content and its metadata, or leave nothing behind.

#### Scenario: Successful upload
- **WHEN** a client submits a non-empty multipart file
- **THEN** the system stores the file's content, filename, content type, and the caller as its owner, and responds with a `201 Created` containing the generated file id and metadata

#### Scenario: Empty file rejected
- **WHEN** a client submits an upload with no file part or a zero-byte file
- **THEN** the system responds with `400 Bad Request` and does not store anything

#### Scenario: Oversized file rejected
- **WHEN** a client submits a file larger than the configured maximum upload size
- **THEN** the system responds with `413 Payload Too Large` and does not store the file

#### Scenario: Malformed content type rejected
- **WHEN** a client submits a file whose declared content type is not a syntactically valid media type
- **THEN** the system responds with `400 Bad Request` and does not store the file

#### Scenario: Upload fails partway through persisting
- **WHEN** the system has written an uploaded file's content but then fails to persist that file's metadata
- **THEN** the request fails, no content for that file remains in the storage directory, and the generated id appears in neither the listing nor a successful download

### Requirement: Download a file by id
The system shall allow retrieving a previously uploaded file's raw content by its file id to the caller who owns that file, restoring the original content type and filename in a response whose headers are well-formed for any filename the system accepted. A file owned by another user, or stored without an owner, shall be answered the same way as a file that does not exist, so that the response does not disclose whether the id exists.

#### Scenario: Successful download
- **WHEN** a client requests a file by an id that exists in storage and is owned by that caller
- **THEN** the system responds with `200 OK`, the file's original bytes as the body, the original `Content-Type`, and the original filename in `Content-Disposition`

#### Scenario: Unknown id
- **WHEN** a client requests a file by an id that does not exist in storage
- **THEN** the system responds with `404 Not Found`

#### Scenario: File owned by another user
- **WHEN** a client requests a file by an id that exists in storage but is owned by another user
- **THEN** the system responds with `404 Not Found`, does not serve the file's bytes, and the response is indistinguishable from the response for an id that does not exist

#### Scenario: Filename that cannot appear literally in a header
- **WHEN** a client requests a file whose original filename contains a double quote, a non-ASCII character, or any other character that cannot be placed literally into an HTTP header
- **THEN** the system responds with `200 OK` and a `Content-Disposition` header that encodes the filename so the client can recover it unambiguously, rather than failing the request or emitting a malformed header

#### Scenario: Stored content type is no longer usable
- **WHEN** a client requests a file whose recorded content type cannot be interpreted as a media type, because it was stored before that value was validated
- **THEN** the system responds with `200 OK` and the file's original bytes, using a generic binary content type, rather than failing the request

#### Scenario: Content type sniffing prevented
- **WHEN** the system serves a stored file's content
- **THEN** the response instructs the client not to infer a content type other than the one the response declares

### Requirement: List stored files
The system shall allow retrieving the metadata (id, original filename, content type, size) of every file in storage that the calling user owns, and every id it reports shall be retrievable by that caller. A file owned by another user, or stored without an owner, shall not appear in the listing.

#### Scenario: Files exist
- **WHEN** a client requests the list of stored files and one or more files owned by that caller exist in storage
- **THEN** the system responds with `200 OK` and a list containing, for each of those files, its id, original filename, content type, and size

#### Scenario: No files stored
- **WHEN** a client requests the list of stored files and none owned by that caller exist
- **THEN** the system responds with `200 OK` and an empty list

#### Scenario: Another user's files
- **WHEN** a client requests the list of stored files and storage holds files owned by a different user
- **THEN** those files do not appear in the response

#### Scenario: Files uploaded before the service last started
- **WHEN** a client requests the list of stored files and some of the caller's files were uploaded before the service's current run started
- **THEN** those files still appear in the list, with the same metadata as if they had been uploaded during the current run

#### Scenario: Listing agrees with download
- **WHEN** a client requests the list of stored files
- **THEN** every id in the response can be downloaded successfully by that caller, and any file whose stored content is missing does not appear in the list

#### Scenario: Unusable metadata does not prevent startup
- **WHEN** the service starts and the storage directory contains a metadata record that cannot be read, cannot be interpreted, or does not identify the file it belongs to
- **THEN** the service starts successfully and serves requests, that record is excluded from the listing, and the remaining files are listed as normal

### Requirement: Перенос ранее сохранённых метаданных в базу
Система должна перенести в базу метаданные файлов, сохранённых до перехода на PostgreSQL, чтобы такие файлы не пропали из хранилища. Владельца у них нет и взяться ему неоткуда, поэтому перенесённый файл не появляется в списке и не скачивается, пока владелец не проставлен вручную. Перенос должен быть идемпотентным: повторный старт не должен создавать вторую запись для того же файла.

#### Scenario: Метаданные с диска найдены при старте
- **WHEN** сервис стартует, и в каталоге хранения есть запись метаданных, чей id отсутствует в базе, а содержимое этого файла на месте
- **THEN** система добавляет метаданные в базу без владельца, и такой файл не появляется в списке ни у одного пользователя

#### Scenario: Повторный старт не дублирует запись
- **WHEN** сервис стартует повторно с тем же каталогом хранения
- **THEN** для каждого перенесённого файла в базе ровно одна запись, с теми же метаданными

#### Scenario: Запись метаданных без содержимого
- **WHEN** сервис стартует, и в каталоге хранения есть запись метаданных, для которой нет сохранённого содержимого
- **THEN** она не переносится в базу, а сервис запускается успешно

#### Scenario: Владелец, проставленный вручную, возвращает файл в список
- **WHEN** перенесённой записи проставлен владелец
- **THEN** этот файл появляется в списке у названного пользователя и скачивается по его токену
