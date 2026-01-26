---
description: This page describes how tests work in Amper.
---
# Testing

Test code is located in the `test/` folder:

```
├─ src/            # production code
├─ test/           # test code
│  ├─ MainTest.kt
│  ╰─ ...
╰─ module.yaml
```

By default, the [Kotlin test](https://kotlinlang.org/api/latest/kotlin.test/) framework is preconfigured for each
platform. Additional test-only dependencies should be added to the `test-dependencies:` section of your module
configuration file:

```yaml title="module.yaml"
product: jvm/app

# these dependencies are available in main and test code
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# additional dependencies for test code
test-dependencies:
  - io.ktor:ktor-server-test-host:2.2.0
```

To add or override [toolchain settings](basics.md#settings) in tests, use the `test-settings:` section:
```yaml title="module.yaml"
# these dependencies are available in main and test code
setting:
  kotlin:
    ...

# additional test-specific setting 
test-settings:
  kotlin:
    ...
```

Test settings and dependencies by default are inherited from the main configuration according to the 
[configuration propagation rules](multiplatform.md#dependencysettings-propagation).
Example:
```
├─ src/
├─ src@ios/
├─ test/           # Sees declarations from src/. Executed on all platforms.
│  ├─ MainTest.kt
│  ╰─ ...
├─ test@ios/       # Sees declarations from src/, src@ios/, and `test/`. Executed on iOS platforms only.
│  ├─ IOSTest.kt
│  ╰─ ...
╰─ module.yaml
```

```yaml title="module.yaml"
product:
  type: lib
  platforms: [android, iosArm64]

# these dependencies are available in main and test code
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# dependencies for test code
test-dependencies:
  - org.jetbrains.kotlin:kotlin-test:1.8.10
  
# these settings affect the main and test code
settings: 
  kotlin:
    languageVersion: 1.8

# these settings affect tests only
test-settings:
  kotlin:
    languageVersion: 1.9 # overrides settings.kotlin.languageVersion 1.8
```
