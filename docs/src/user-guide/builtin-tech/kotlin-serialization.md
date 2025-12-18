---
description: |
  Kotlin Serialization is the official multiplatform and multi-format serialization library for Kotlin.
  Amper provides support for it out of the box.
---
# Kotlin Serialization

[Kotlin Serialization](https://github.com/Kotlin/kotlinx.serialization) is the official multiplatform and multi-format
serialization library for Kotlin.

If you need to (de)serialize Kotlin classes to/from JSON, you can enable Kotlin Serialization it in its simplest form:
```yaml
settings:
  kotlin:
    serialization: json  # JSON or other format
```
This snippet configures the compiler to process `@Serializable` classes, and adds dependencies on the serialization
runtime and JSON format libraries.

You can also customize the version of the Kotlin Serialization libraries using the full form of the configuration:

```yaml
settings:
  kotlin:
    serialization:
      format: json
      version: 1.7.3
```

## More control over serialization formats

If you don't need serialization format dependencies or if you need more control over them, you can use the following:
```yaml
settings:
  kotlin:
    serialization: enabled # configures the compiler and serialization runtime library
```
This snippet on its own only configures the compiler and the serialization runtime library, but doesn't add any format
dependency. However, it adds a built-in catalog with official serialization formats libraries, which you can use in
your `dependencies` section. This is useful in multiple cases:

* if you need a format dependency only in tests:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  test-dependencies:
    - $kotlin.serialization.json
  ```

* if you need to customize the scope of the format dependencies:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json: compile-only
  ```

* if you need to expose format dependencies transitively:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json: exported
  ```

* if you need multiple formats:
  ```yaml
  settings:
    kotlin:
      serialization: enabled
  
  dependencies:
    - $kotlin.serialization.json
    - $kotlin.serialization.protobuf
  ```
