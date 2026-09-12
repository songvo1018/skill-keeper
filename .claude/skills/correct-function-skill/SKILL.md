---
name: correct-function-skill         # Обязательно, 1-64 символов
description: Brief description # Обязательно, 1-1024 символов
license: MIT                   # Опционально
allowed-tools: Bash Read       # Опционально (experimental)
---

## Quick start
Methods must throw exception if calculation or handling logic expect to return number, string and something went wrong.

## When to use
- Implementing methods and functions of classes

## Instructions
- Use for methods with any type of return value 
- For methods with return result
- Exception must have short information about error 

## Examples

```java

// good
public String doSomething(String value) {
    if (value.isEmpty()) {
        throw new Exception(); 
    }
    return value;
}

// bad
public String doSomething(String value) {
    if (value.isEmpty()) {
        return null; 
    }
    return value;
}
```