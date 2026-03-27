---
description: An exhaustive reference of the module file format.
---
# Module file reference

## `aliases`

An alias can be used to share code, dependencies, and/or settings between a group of platforms that doesn't already 
have a name (an exclusive common ancestor) in the default hierarchy. Aliases can be used as `@platform` qualifiers in the settings.

Read more in the [Multiplatform](../user-guide/multiplatform.md#aliases) section.

Example:

```yaml
# Create an alias to share code between JVM and Android platforms.  
product:
  type: lib
  platforms: [ jvm, android, iosArm64, iosSimulatorArm64 ]

aliases:
  - jvmAndAndroid: [jvm, android]

# Dependencies for JVM and Android platforms:
dependencies@jvmAndAndroid:
  ...
```

## `apply`

The `apply` section lists the templates applied to the module.
Read more in the [Module templates](../user-guide/templates.md) section.

Use `- ./<relative path>` or `- ../<relative path>` notation, where the `<relative path>` points at a template file.

Example:

```yaml
# Apply a `common.module-template.yaml` template to the module
product: jvm/app

apply:
  - ../common.module-template.yaml
```

## `dependencies` and `test-dependencies`

The `dependencies` section defines the list of modules and libraries necessary to build the module.
Certain dependencies can also be exported as part of the module API.
Read more in the [Dependencies](../user-guide/dependencies.md) section.

The `test-dependencies` section defines the dependencies necessary to build and run tests of the module.
Read more in the [Testing](../user-guide/testing.md) section.

Supported dependency types:

| Notation                                         | Description                                                                                                                |
|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `- ./<relative path>`<br/>`- ../<relative path>` | Dependency on [another module](../user-guide/dependencies.md#module-dependencies) in the codebase.                         |
| `- <group ID>:<artifact ID>:<version>`           | Dependency on [a Kotlin or Java library](../user-guide/dependencies.md#external-maven-dependencies) in a Maven repository. |
| `- $<catalog.key>`                               | Dependency from [a dependency catalog](../user-guide/dependencies.md#library-catalogs).                                    |
| `- bom: <group ID>:<artifact ID>:<version>`      | Dependency on [a BOM](../user-guide/dependencies.md#using-a-maven-bom).                                                    |
| `- bom: $<catalog.key>`                          | Dependency on [a BOM from a dependency catalog](../user-guide/dependencies.md#library-catalogs).                           |

Each dependency (except BOM) has the following attributes:

| Attribute           | Default | Description                                                                                                                        |
|---------------------|---------|------------------------------------------------------------------------------------------------------------------------------------|
| `exported: boolean` | `false` | Whether a dependency should be [visible as a part of a published API](../user-guide/dependencies.md#transitivity-and-scope).       |
| `scope: enum`       | `all`   | When the dependency should be used. Read more about the [dependency scopes](../user-guide/dependencies.md#transitivity-and-scope). |

Available scopes:

| Scopes         | Description                                                                                                |
|----------------|------------------------------------------------------------------------------------------------------------|
| `all`          | The dependency is available during compilation and runtime.                                                |  
| `compile-only` | The dependency is only available during compilation. This is a 'provided' dependency in Maven terminology. |
| `runtime-only` | The dependency is not available during compilation, but available during testing and running.              |

Examples:

```yaml
# Short form for the dependency attributes
dependencies:
  - io.ktor:ktor-client-core:2.2.0                   # Kotlin or Java dependency 
  - org.postgresql:postgresql:42.3.3: runtime-only
  - ../common-types: exported                        # Dependency on another module in the codebase 
  - $compose.foundation                              # Dependency from the 'compose' catalog
  - bom: io.ktor:ktor-bom:2.2.0                      # Importing BOM 
  - io.ktor:ktor-serialization-kotlinx-json          # Kotlin or Java dependency with a version resolved from BOM
```

```yaml
# Full form for the dependency attributes
dependencies:
  - io.ktor:ktor-client-core:2.2.0
  - ../common-types:
      exported: true
      scope: all
  - org.postgresql:postgresql:42.3.3:
      exported: false
      scope: runtime-only
```

The `dependencies` section can also be [qualified with a platform](../user-guide/multiplatform.md#platform-qualifier):

```yaml
# Dependencies used to build the common part of the product
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# Dependencies used to build the JVM part of the product
dependencies@jvm:
  - io.ktor:ktor-client-java:2.2.0
  - org.postgresql:postgresql:42.3.3: runtime-only
```

## `description`

An optional description of the module. This description supports Markdown formatting and can span multiple lines.

When writing multiline descriptions, the first line should act as a short summary that can stand on its own, like 
commit messages. Only the first line is displayed by default in `./amper show modules`.

This description is used by the CLI and by IDEs to show information about the module.
For libraries, it is also used as a description in published metadata by default.

## `layout`

The `layout` defines the module file structure. Valid values:

* `amper`: place your files in `src`, `test`, and `resources` directories 
* `maven-like`: just like Maven (`src/main/kotlin`, `src/main/java`, `src/test/kotlin`, `src/main/resources`)

The default value is `amper`.

!!! warning "The `maven-like` layout is only supported in modules with `jvm/app` or `jvm/lib` product type."

Examples:

```yaml
product: jvm/app

layout: maven-like

settings:
  # ...

```

## `pluginInfo`

The `pluginInfo` section is only available if the `product.type` is `jvm/amper-plugin`.
It configures plugin-specific build settings.

| Attribute                 | Default                     | Description                                                                                                                                                                                     |
|---------------------------|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id: string`              | Module name                 | The ID that is used to refer to the plugin in the configuration files.                                                                                                                          |  
| ~~`description: string`~~ | `null`                      | **Deprecated**. Use the plugin module's top-level `description` instead.                                                                                                                        |  
| `settingsClass: string`   | `null` (no plugin settings) | The fully qualified name of the @Configurable-annotated interface to be used as plugin configuration. This interface can't come from a dependency, it must be declared in the source directory. |

## `product`

The `product` section defines what should be produced out of the module.
Read more about the [product types](../user-guide/basics.md#product-type).

| Attribute             | Default               | Description                                 |
|-----------------------|-----------------------|---------------------------------------------|
| `platform: enum list` | (derived from `type`) | What platforms to generate the product for. |
| `type: enum`          | -                     | What type of product to generate.           |

Supported product types and platforms:

| Product Type       | Description                                                               | Platforms                                                        |
|--------------------|---------------------------------------------------------------------------|------------------------------------------------------------------|
| `android/app`      | An Android VM application.                                                | `android`                                                        |
| `ios/app`          | An iOS application.                                                       | device: `iosArm64`<br> simulators: `iosX64`, `iosSimulatorArm64` |
| `js/app`           | A JavaScript application.                                                 | `js`                                                             |
| `jvm/amper-plugin` | A plugin for Amper (see [Plugins](../user-guide/plugins/quick-start.md)). | `jvm`                                                            |
| `jvm/app`          | A JVM application (console, desktop, server...).                          | `jvm`                                                            |
| `jvm/lib`          | A JVM library that other modules can depend on.                           | `jvm`                                                            |
| `lib`              | A reusable multiplatform library that other modules can depend on.        | any (the list must be specified explicitly)                      |
| `linux/app`        | A native Linux application.                                               | `linuxX86`, `linuxArm64`                                         |
| `macos/app`        | A native macOS application.                                               | `macosX64`, `macosArm64`                                         |
| `wasmJs/app`       | A Wasm (JS) application.                                                  | `wasmJs`                                                         |
| `wasmWasi/app`     | A Wasm (WASI) application.                                                | `wasmWasi`                                                       |
| `windows/app`      | A native Windows application.                                             | `mingwX64`                                                       |

Check the list of all [Kotlin Multiplatform targets](https://kotlinlang.org/docs/native-target-support.html) and the
level of their support.

Examples:

```yaml title="Short form"
# Defaults to all supported platforms for the corresponding target
product: macos/app
```

```yaml title="Full form, explicitly specified platforms"
product:
  type: macos/app
  platforms: [ macosArm64, macosArm64 ]
```

```yaml title="Multiplatform Library for JVM and Android platforms"
product:
  type: lib
  platforms: [ jvm, android ]
```

## `repositories`

The `repositories` section defines the list of repositories used to look up and download the module dependencies.
Read more about [Managing Maven repositories](../user-guide/dependencies.md#managing-maven-repositories).

| Attribute              | Default          | Description                                            | 
|------------------------|------------------|--------------------------------------------------------|
| `credentials: object?` | `null`           | Credentials to connect to this repository (if needed). |
| `id: string`           | (set from `url`) | The ID of the repository, used to reference it.        |
| `url: string`          | -                | The URL of the repository.                             |

Credentials support username/password authentication and have the following attributes:

| Attribute             | Description                                                                                       |
|-----------------------|---------------------------------------------------------------------------------------------------|
| `file: path`          | A relative path to a file with the credentials. Currently, only `*.property` files are supported. |
| `passwordKey: string` | A key in the file that holds the password.                                                        |
| `usernameKey: string` | A key in the file that holds the username.                                                        |

Examples:

```yaml title="Short form"
repositories:
  - https://repo.spring.io/ui/native/release #(1)!
  - https://jitpack.io
```

1. When using just a string, it is used as both the `url` and `id` of the repository

```yaml title="Full form"
repositories:
  - url: https://repo.spring.io/ui/native/release
  - id: jitpack
    url: https://jitpack.io
```

```yaml title="Specifying credentials"
repositories:
  - url: https://my.private.repository/
    credentials:
      file: ./local.properties
      usernameKey: my.private.repository.username
      passwordKey: my.private.repository.password  
```

```yaml title="Using the local Maven repository"
repositories:
  - mavenLocal # special URL that points to ~/.m2/repository
```

## `settings` and `test-settings`

The `settings` section configures the toolchains used in the build process.

The `test-settings` section controls building and running the module tests.
Read more in the [Testing](../user-guide/testing.md) section.

### `settings.android`

`settings.android` configures the Android toolchain and platform.

| Attribute                     | Default                 | Description                                                                                                                                                                                                                   |
|-------------------------------|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `applicationId: string`       | (set from `namespace`)  | The ID for the application on a device and in the Google Play Store. [Read more](https://developer.android.com/build/configure-app-module#set-namespace).                                                                     |
| `namespace: string`           | `org.example.namespace` | A Kotlin or Java package name for the generated `R` and `BuildConfig` classes. [Read more](https://developer.android.com/build/configure-app-module#set-namespace).                                                           |
| `compileSdk: int`             | 36                      | The API level to compile the code. The code can use only the Android APIs up to that API level. [Read more](https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CommonExtension#compileSdk()). |
| `targetSdk: int`              | (set from `compileSdk`) | The target API level for the application. [Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                             |
| `minSdk: int`                 | 21                      | Minimum API level needed to run the application. [Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                      |
| `maxSdk: int?`                | `null`                  | Maximum API level on which the application can run. [Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                   |
| `signing: object`             |                         | Android signing settings. [Read more](https://developer.android.com/studio/publish/app-signing).                                                                                                                              |
| `versionCode: int`            | 1                       | Version code. [Read more](https://developer.android.com/studio/publish/versioning).                                                                                                                                           |
| `versionName: string`         | `unspecified`           | Version name. [Read more](https://developer.android.com/studio/publish/versioning).                                                                                                                                           |
| `parcelize: object \| string` | (disabled)              | Configure [Parcelize](https://developer.android.com/kotlin/parcelize).                                                                                                                                                        |

#### `settings.android.parcelize`

`settings.android.parcelize` configures [Parcelize](https://developer.android.com/kotlin/parcelize) for the Android
platform in the module. The value can be the simple `enabled` string, or an object with the following attributes:

| Attribute                            | Default | Description                                                                                                                                                                                                                                                                                                                                                                |
|--------------------------------------|---------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled: boolean`                   | `false` | Whether to enable [Parcelize](https://developer.android.com/kotlin/parcelize). When enabled, an implementation of the `Parcelable` interface is automatically generated for classes annotated with `@Parcelize`.                                                                                                                                                           |
| `additionalAnnotations: string list` | `[]`    | The full-qualified names of additional annotations that should be considered as `@Parcelize`. This is useful if you need to annotate classes in common code shared between different platforms, where the real `@Parcelize` annotation is not available. In that case, create your own common annotation and add its fully-qualified name so that Parcelize recognizes it. |

```yaml title="Short form"
# Enables Parcelize to process @Parcelize-annotated classes
settings:
  android:
    parcelize: enabled
```

```yaml title="Custom annotation"
# Configures Parcelize to process a custom @com.example.MyCommonParcelize annotation
settings:
  android:
    parcelize:
      enabled: true
      additionalAnnotations: [ com.example.MyCommonParcelize ]
```

#### `settings.android.signing`

`settings.android.signing` configures signing of Android apps [Read more](https://developer.android.com/studio/publish/app-signing)


| Attribute              | Default                 | Description                                                                                            |
|------------------------|-------------------------|--------------------------------------------------------------------------------------------------------|
| `enabled: boolean`     | `false`                 | Whether signing enabled or not. [Read more](https://developer.android.com/studio/publish/app-signing). |
| `propertiesFile: path` | `./keystore.properties` | Location of properties file. [Read more](https://developer.android.com/studio/publish/app-signing).    |

### `settings.compose`

`settings.compose` configures the [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
framework. Read more about [Compose configuration](../user-guide/builtin-tech/compose-multiplatform.md).

| Attribute              | Default  | Description                                                    |
|------------------------|----------|----------------------------------------------------------------|
| `enabled: boolean`     | `false`  | Enable Compose runtime, dependencies and the compiler plugins. |
| `version: string`      | `1.10.3` | The Compose plugin version to use.                             |
| `resources: object`    |          | Compose Resources settings.                                    |
| `experimental: object` |          | Experimental Compose settings.                                 |

`settings.compose.resources` configures Compose Resources settings.

| Attribute                   | Default | Description                                                                                                                                                 |
|-----------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `packageName: string`       | `""`    | A unique identifier for the resources in the current module. Used as package for the generated Res class and for isolating resources in the final artifact. |
| `exposedAccessors: boolean` | `false` | Whether the generated resources accessors should be exposed to other modules (public) or internal.                                                          |

`settings.compose.experimental` configures experimental Compose features.

| Attribute           | Default | Description                               |
|---------------------|---------|-------------------------------------------|
| `hotReload: object` |         | Experimental Compose hot-reload settings. |

`settings.compose.experimental.hotReload` configures experimental hot reload (JVM only).

| Attribute         | Default | Description                                      |
|-------------------|---------|--------------------------------------------------|
| `version: string` | `1.0.0` | The Compose Hot Reload toolchain version to use. |

Examples:

```yaml title="Short form"
settings:
  compose: enabled
```

```yaml title="Full form"
settings:
  compose:
    enabled: true
    version: 1.6.10
```

```yaml title="Full form with resources configuration"
settings:
  compose:
    enabled: true
    version: 1.6.10
    resources:
      packageName: "com.example.myapp.resources"
      exposedAccessors: true
```

### `settings.java`

`settings.java` configures the Java language and the compiler.

| Attribute                       | Default | Description                                            |
|---------------------------------|---------|--------------------------------------------------------|
| `annotationProcessing: object`  |         | Java annotation processing settings                    |
| `compileIncrementally: boolean` | `false` | Enables incremental compilation for Java sources       |
| `freeCompilerArgs: string list` | `[]`    | Pass any compiler option directly to the Java compiler |

#### `settings.java.annotationProcessing`

`settings.java.annotationProcessing` configures Java annotation processing.

| Attribute               | Default | Description                                                                                                                    |
|-------------------------|---------|--------------------------------------------------------------------------------------------------------------------------------|
| `processorOptions: map` | `{}`    | Options to pass to annotation processors                                                                                       |
| `processors: list`      | `[]`    | The list of annotation processors to use. Each item can be a path to a local module, a catalog reference, or maven coordinates |

Examples:

```yaml
settings:
  java:
    annotationProcessing:
      processors:
        - org.mapstruct:mapstruct-processor:1.6.3
```

```yaml title="Passing processor options"
settings:
  java:
    annotationProcessing:
      processors:
        - $libs.auto.service # using catalog reference
      processorOptions:
        debug: true
```

### `settings.junit`

`settings.junit` configures the JUnit test runner on the JVM and Android platforms. Read more
about [testing support](../user-guide/testing.md).

By default, JUnit 5 is used.

| Value     | Description                                                        |
|-----------|--------------------------------------------------------------------|
| `junit-5` | JUnit 5 dependencies and the test runner are configured (default). |
| `junit-4` | JUnit 4 dependencies and the test runner are configured.           |
| `none`    | JUnit is not automatically configured.                             |

### `settings.jvm`

`settings.jvm` configures the JVM platform-specific settings.

| Attribute                      | Default                                                 | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                    |
|--------------------------------|---------------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `jdk: object`                  |                                                         | Defines requirements for the JDK to use. These requirements are used to validate `JAVA_HOME` or to provision a matching JDK automatically. See details below and the [JDK provisioning](../user-guide/advanced/jdk-provisioning.md) page.                                                                                                                                                                                                                      |
| `mainClass: string`            | [auto-detected](../user-guide/product-types/jvm-app.md) | (Only for `jvm/app` [product type](../user-guide/basics.md#product-type)) The fully-qualified name of the class used to run the application.                                                                                                                                                                                                                                                                                                                   |
| `release: enum`                | (set from `jdk.version`)                                | The minimum JVM release version that the code should be compatible with. This enforces compatibility on 3 levels. First, it is used as the target version for the bytecode generated from Kotlin and Java sources. Second, it limits the Java platform APIs available to Kotlin and Java sources. Third, it limits the Java language constructs in Java sources. If this is set to null, these constraints are not applied and the compiler defaults are used. |
| `runtimeClasspathMode: enum`   | `jars`                                                  | How the runtime classpath is constructed: `jars` (default) builds local module dependencies as jars; `classes` uses compiled classes for local modules on the runtime classpath.                                                                                                                                                                                                                                                                               |
| `storeParameterNames: boolean` | `false`                                                 | Enables storing formal parameter names of constructors and methods in the generated class files. These can later be accessed using reflection.                                                                                                                                                                                                                                                                                                                 |

#### `settings.jvm.jdk`

Configures how Amper selects or provisions a JDK for the module. If `JAVA_HOME` points to a suitable JDK, Amper can use it; otherwise it can download a matching JDK via the Foojay Discovery API and cache it. See the [JDK provisioning](../user-guide/advanced/jdk-provisioning.md) page for a deep dive.

| Property               | Type        | Default                           | Description                                                                                                                                                      |
|------------------------|-------------|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `version`              | int         | Amper default JDK major version   | Major JDK version to use (e.g., 8, 11, 17, 21, 25). Amper prefers the latest update in that line.                                                                |
| `distributions`        | list<enum>? | `null` (accept all distributions) | Allow‑list of acceptable JDK distributions (vendors). If `null`, any known distribution is acceptable.                                                           |
| `selectionMode`        | enum        | `auto`                            | Strategy for choosing between `JAVA_HOME` and provisioning: `auto`                                                                                               | `alwaysProvision` | `javaHome`. |
| `acknowledgedLicenses` | list<enum>  | `[]`                              | Distributions that require a commercial license and which you explicitly acknowledge. If you restrict `distributions` to any paid vendor, you must list it here. |

Supported values for `distributions` and `acknowledgedLicenses`:

- `temurin` (Eclipse Temurin, a.k.a. Adoptium)
- `zulu` (Azul Zulu)
- `corretto` (Amazon Corretto)
- `jetbrains` (JetBrains Runtime)
- `oracleOpenJdk` (Oracle OpenJDK)
- `microsoft` (Microsoft)
- `bisheng` (BiSheng)
- `dragonwell` (Alibaba Dragonwell)
- `kona` (Tencent Kona)
- `liberica` (BellSoft Liberica)
- `openLogic` (Perforce OpenLogic)
- `sapMachine` (SapMachine)
- `semeru` (IBM Semeru Open Edition)
- `oracle` (Oracle JDK; requires license)
- `zuluPrime` (Azul Zulu Prime; requires license)
- `semeruCertified` (IBM Semeru Certified; requires license)

Values for `selectionMode`:

- `auto` (default) — use `JAVA_HOME` if it matches the criteria; otherwise provision a JDK.
- `alwaysProvision` — ignore `JAVA_HOME` and always provision a matching JDK (download or reuse cached one).
- `javaHome` — require `JAVA_HOME` to match the criteria; fail if it does not. Provisioning is disabled.

!!! example "See examples in the [JDK provisioning section](../user-guide/advanced/jdk-provisioning.md#examples)."

#### `settings.jvm.test`

`settings.jvm.test` configures the test settings on the JVM and Android platforms.
Read more about [testing support](../user-guide/testing.md).

| Value                          | Default | Description                                   |
|--------------------------------|---------|-----------------------------------------------|
| `junitPlatformVersion: string` | 6.0.1   | The JUnit platform version used to run tests. |
| `extraEnvironment: map`        | `{}`    | Environment variables for the test process.   |
| `freeJvmArgs: string list`     | `[]`    | Free JVM arguments for the test process.      |
| `systemProperties: map`        | `{}`    | JVM system properties for the test process.   |

### `settings.kotlin`

`settings.kotlin` configures the Kotlin language and the compiler.

| Attribute                        | Default                      | Description                                                                                                                                                          |
|----------------------------------|------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `version: string`                | 2.3.20                       | The version of the Kotlin compiler and stdlib to use.                                                                                                                |
| `allOpen: object`                |                              | Configure the [Kotlin all-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html).                                                                   |
| `allWarningsAsErrors: boolean`   | `false`                      | Turn any warnings into a compilation error.                                                                                                                          |
| `apiVersion: enum`               | (set from `languageVersion`) | Allow using declarations only from the specified version of Kotlin bundled libraries.                                                                                |
| `compilerPlugins: object list`   | `[]`                         | Configure third-party Kotlin compiler plugins.                                                                                                                       |
| `debug: boolean`                 | `true`                       | (Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) Enable emitting debug information.                                               |
| `freeCompilerArgs: string list`  | `[]`                         | Pass any [compiler option](https://kotlinlang.org/docs/compiler-reference.html#compiler-options) directly.                                                           |
| `jsPlainObjects: object \| enum` |                              | Enable the Kotlin JS-plain-objects compiler plugin.                                                                                                                  |
| `ksp: object`                    |                              | Configure [Kotlin Symbol Processing](../user-guide/advanced/ksp.md).                                                                                                 |
| `languageVersion: enum`          | (major.minor from `version`) | Provide source compatibility with the specified version of Kotlin.                                                                                                   |
| `noArg: object`                  |                              | Configure the [Kotlin no-arg compiler plugin](https://kotlinlang.org/docs/no-arg-plugin.html).                                                                       |
| `optIns: enum list`              | `[]`                         | Enable usages of API that [requires opt-in](https://kotlinlang.org/docs/opt-in-requirements.html) with a requirement annotation with the given fully qualified name. |
| `progressiveMode: boolean`       | `false`                      | Enable the [progressive mode for the compiler](https://kotlinlang.org/docs/compiler-reference.html#progressive).                                                     |
| `serialization: object \| enum`  |                              | Configure [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization).                                                                                   |
| `suppressWarnings: boolean`      | `false`                      | Suppress the compiler from displaying warnings during compilation.                                                                                                   |
| `verbose: boolean`               | `false`                      | Enable verbose logging output which includes details of the compilation process.                                                                                     |

The `serialization` attribute is an object with the following properties:

| Attribute          | Default                | Description                                                                                                                                                                           |
|--------------------|------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `enabled: boolean` | `false`                | Enable the `@Serializable` annotation processing, and add the core serialization library. When enabled, a built-in catalog for kotlinx.serialization format dependencies is provided. |
| `version: string`  | `1.10.0`               | The version to use for the core serialization library and the serialization formats.                                                                                                  |
| `format: enum`     | `none` (only core lib) | A shortcut for `enabled: true` and adding the given serialization format dependency. For instance, `json` adds the JSON format in addition to enabling serialization.                 |

You can also use a short form and directly specify `serialization: enabled` or `serialization: json`.

Examples:

```yaml
# Set Kotlin language version and opt-ins
settings:
  kotlin:
    languageVersion: 1.8
    optIns: [ kotlin.io.path.ExperimentalPathApi ]
```

```yaml
# Enable Kotlin Serialization with the JSON format
settings:
  kotlin:
    serialization: json
```

```yaml
# Enable Kotlin Serialization with the JSON format and a specific version 
settings:
  kotlin:
    serialization: 
      format: json
      version: 1.9.0
```

```yaml
# Enable Kotlin Serialization with multiple formats
settings:
  kotlin:
    serialization: enabled

dependencies:
  - $kotlin.serialization.json
  - $kotlin.serialization.protobuf
```

```yaml
# Enable Kotlin Serialization with multiple formats and a specific version 
settings:
  kotlin:
    serialization: 
      enabled: true
      version: 1.9.0

dependencies:
  - $kotlin.serialization.json
  - $kotlin.serialization.protobuf
```

#### `settings.kotlin.allOpen`

`settings.kotlin.allOpen` configures the [Kotlin all-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html),
which makes classes annotated with specific annotations open automatically without the explicit `open` keyword.

| Attribute                  | Default | Description                                                                                                    |
|----------------------------|---------|----------------------------------------------------------------------------------------------------------------|
| `enabled: boolean`         | `false` | Enable the Kotlin all-open compiler plugin                                                                     |  
| `annotations: string list` | `[]`    | List of annotations that trigger open class/method generation                                                  |  
| `presets: enum list`       | `[]`    | Predefined sets of annotations for common frameworks (available presets: `spring`, `micronaut`, and `quarkus`) |  

Examples:

```yaml title="All-open with custom annotations"
settings:
  kotlin:
    allOpen:
      enabled: true
      annotations: [ com.example.MyOpen, com.example.MyFramework.Open ]
```

```yaml title="All-open with Spring preset"
settings:
  kotlin:
    allOpen:
      enabled: true
      presets: [ spring ]
```

#### `settings.kotlin.compilerPlugins`

`settings.kotlin.compilerPlugins` allows adding 
[third-party compiler plugins](../user-guide/advanced/kotlin-compiler-plugins.md#third-party-compiler-plugins) to your 
compilation.

| Attribute                      |      | Description                                                                                                                                                                                                                                                                                                                             |
|:-------------------------------|------|:----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `id: string`                   |      | The ID of this compiler plugin, used to pass options. It is defined by the `pluginId` property in the `CommandLineProcessor` implementation of the plugin. If the plugin is also implemented as a Gradle plugin, its ID can also be found in `getCompilerPluginId()` in the corresponding `KotlinCompilerPluginSupportPlugin` subclass. |
| `dependency: string`           |      | The compiler plugin dependency, in the form of `groupId:artifactId:version` Maven coordinates, or a catalog reference.                                                                                                                                                                                                                  |
| `options: map<string, string>` | `{}` | The options to pass to this compiler plugin, as a key-value map.                                                                                                                                                                                                                                                                        |

Check the [third-party compiler plugins](../user-guide/advanced/kotlin-compiler-plugins.md#third-party-compiler-plugins)
section for more information and examples.

#### `settings.kotlin.jsPlainObjects`

`settings.kotlin.jsPlainObjects` configures the [JS plain objects compiler plugin](https://kotlinlang.org/docs/js-plain-objects.html),
which lets you create and copy plain JS objects in a type-safe way.

| Attribute                     | Default | Description                                        |
|-------------------------------|---------|----------------------------------------------------|
| `enabled: boolean`            | `false` | Enable the Kotlin JS plain objects compiler plugin |  

Check the dedicated [JS plain objects](../user-guide/advanced/kotlin-compiler-plugins.md#js-plain-objects) section for 
more information.

#### `settings.kotlin.noArg`

`settings.kotlin.noArg` configures the [Kotlin no-arg compiler plugin](https://kotlinlang.org/docs/no-arg-plugin.html),
which generates no-arg constructors for classes with specific annotations.

| Attribute                     | Default | Description                                                                             |
|-------------------------------|---------|-----------------------------------------------------------------------------------------|
| `enabled: boolean`            | `false` | Enable the Kotlin no-arg compiler plugin                                                |  
| `annotations: string list`    | `[]`    | List of annotations that trigger no-arg constructor generation                          |  
| `invokeInitializers: boolean` | `false` | Whether to call initializers in the synthesized constructor                             |  
| `presets: enum list`          | `[]`    | Predefined sets of annotations (currently only `jpa` preset for JPA entity annotations) |  

Examples:

```yaml title="No-arg with JPA preset"
# Enable no-arg for JPA entities
settings:
  kotlin:
    noArg:
      enabled: true
      presets: [ jpa ]
```

```yaml title="No-arg with custom annotations"
settings:
  kotlin:
    noArg:
      enabled: true
      annotations: [ com.example.NoArg ]
      invokeInitializers: true
```

#### `settings.kotlin.ksp`

`settings.kotlin.ksp` configures the [Kotlin Symbol Processing mechanism](../user-guide/advanced/ksp.md),
which allows processing Kotlin source code with custom processors (usually to generate extra code).

| Attribute                               | Default | Description                                                                                                              |
|-----------------------------------------|---------|--------------------------------------------------------------------------------------------------------------------------|
| `version: string`                       | `2.3.6` | The version of KSP to use                                                                                                |  
| `processors: string list`               | `[]`    | The list of KSP processors to use. Each item can be a path to a local module, a catalog reference, or maven coordinates. |  
| `processorOptions: map<string, string>` | `{}`    | Some options to pass to KSP processors. Refer to each processor documentation for details.                               |  

### `settings.ktor`

`settings.ktor` configures the Ktor server framework.

| Attribute           | Default | Description                                                                                                          |
|---------------------|---------|----------------------------------------------------------------------------------------------------------------------|
| `enabled: boolean`  | `false` | Enable the Ktor server framework. This is just a convenience to generate library catalog entries for Ktor libraries. |  
| `version: string`   | `3.4.1` | The Ktor version used for the BOM and in the generated library catalog entries                                       |  
| `applyBom: boolean` | `true`  | Whether to apply the Ktor BOM                                                                                        |

Example:

```yaml
settings:
  ktor:
    enabled: true
    version: 2.3.2 # version customization
```

### `settings.lombok`

`settings.lombok` configures Lombok.

| Attribute          | Default   | Description                                         |
|--------------------|-----------|-----------------------------------------------------|
| `enabled: boolean` | `false`   | Enable Lombok                                       |  
| `version: string`  | `1.18.38` | Lombok version for runtime and annotation processor |

Example:

```yaml
settings:
  lombok:
    enabled: true
```

### `settings.native`

`settings.native` configures settings specific to native applications.

| Attribute            | Default | Description                                                        |
|----------------------|---------|--------------------------------------------------------------------|
| `entryPoint: string` | `null`  | The fully-qualified name of the application's entry point function |

Example:

```yaml
# Configure native settings for the module
settings:
  native:
    entryPoint: com.example.MainKt.main
```

### `settings.springBoot`

`settings.springBoot` configures the Spring Boot framework (JVM platform only).

| Attribute           | Default | Description                          |
|---------------------|---------|--------------------------------------|
| `enabled: boolean`  | `false` | Enable Spring Boot                   |  
| `version: string`   | `4.0.5` | Spring Boot version                  |  
| `applyBom: boolean` | `true`  | Whether to apply the Spring Boot BOM |

Example:

```yaml
settings:
  springBoot:
    enabled: true
    version: 3.1.0 # version customization
```
