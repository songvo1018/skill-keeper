---
name: project-structure-skill         # Обязательно, 1-64 символов
description: Brief to support correctly project structure # Обязательно, 1-1024 символов
license: MIT                   # Опционально
allowed-tools: Bash Read       # Опционально (experimental)
---

## Quick start
Services and components of project must placement in domain-oriented packaging.

## When to use
- Implementing methods and functions of classes

## Instructions
- Domain functionality must be placed separataly 
- Common methods and classes move and place in skill-keeper\src\main\java\com\skillskeeper\skillskeeper\util package
- In domain package controllers, service and models must be placed in sub package
- Services must have and implement Interface of his methods

## Sub packages of a domain

Every class of a domain lives in one of these sub packages - never directly in the domain package:

- `controller` - the domain's `@RestController` classes
- `service` - the domain's service interfaces and their implementations, plus internal collaborators a service owns
- `model` - records and value objects: request and response bodies, metadata, configuration properties
- `exception` - the domain's own exception types
- `config` - the domain's `@Configuration` classes and bean definitions
- `web` - cross-cutting HTTP pieces that are not a controller: interceptors, `@RestControllerAdvice` handlers, request-scoped accessors

A domain's constants class (for example `FileStorageMessages`, see `no-inline-concatenated-strings`) stays in the domain package itself, because every sub package uses it.

## Naming a service and its interface

The interface names the role; the implementation keeps the `Service` suffix. Do not use an `Impl` suffix.

- `FileStorage` (interface) implemented by `FileStorageService`
- `CredentialsVerifier` (interface) implemented by `AlwaysApprovingCredentialsVerifier`

A class a service owns and uses internally is a collaborator, not a service, and needs no interface - give it one only when a second implementation is actually needed.

## Examples

```java

// good
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\auth
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\auth\model\ (model classes, records)
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage\service\FileStorageService.java
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage\service\FileStorage.java (interface)
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage\exception\StoredFileNotFoundException.java
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\auth\web\AuthTokenInterceptor.java
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\auth\config\AuthWebConfig.java
// bad
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage\FileStorageService.java
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage\StoredFile.java
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\HelloController.java (outside any domain)
skill-keeper\src\main\java\com\skillskeeper\skillskeeper\filestorage\service\FileStorageServiceImpl.java
```
