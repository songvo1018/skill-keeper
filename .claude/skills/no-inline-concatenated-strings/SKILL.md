---
name: no-inline-concatenated-strings         # Обязательно, 1-64 символов
description: Brief description # Обязательно, 1-1024 символов
license: MIT                   # Опционально
allowed-tools: Bash Read       # Опционально (experimental)
---

## Quick start
Method-call arguments must never contain an inline string literal concatenated with a variable. The literal part must be declared as a named constant in a constants class for its package/domain, and the call site combines that constant with the variable.

## When to use
- Building a string (message, key, filename, header value, etc.) that is passed as a method-call argument

## Instructions
- Declare the literal part as a named constant in a constants class scoped to the package/domain (e.g. `FileStorageMessages` for the `filestorage` package)
- The call site may combine `CONSTANT + variable` or `String.format(CONSTANT, variable)` - only a raw inline literal concatenated at the call site is forbidden
- A string literal with no concatenation (no variable involved) is not affected by this rule

## Examples

```java

// good
throw new StoredFileNotFoundException(FileStorageMessages.FILE_NOT_FOUND_PREFIX + fileId);

// bad
throw new StoredFileNotFoundException("No stored file with id: " + fileId);
```
