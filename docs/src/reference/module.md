# Module file reference

## `aliases`

An alias can be used to share code, dependencies, and/or settings between a group of platforms that doesn't already 
have a name (an exclusive common ancestor) in the default hierarchy. Aliases can be used as `@platform` qualifiers in the settings.

Read more about [aliases](../user-guide/multiplatform.md#aliases).

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

`apply:` section lists the templates applied to the module. Read more about the [module templates](../user-guide/templates.md)

Use `- ./<relative path>` or `- ../<relative path>` notation, where the `<relative path>` points at a template file.

Example:

```yaml
# Apply a `common.module-template.yaml` template to the module
product: jvm/app

apply:
  - ../common.module-template.yaml
```

## `dependencies` and `test-dependencies`

`dependencies:` section defines the list of modules and libraries necessary to build the module.
Certain dependencies can also be exported as part of the module API.
Read more about the [dependencies](../user-guide/dependencies.md).

`test-dependencies:` section defines the dependencies necessary to build and run tests of the module. Read more about
the [module tests](../user-guide/testing.md).

Supported dependency types:

| Notation                                         | Description                                                                                                                |
|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------------------|
| `- ./<relative path>`<br/>`- ../<relative path>` | Dependency on [another module](../user-guide/dependencies.md#module-dependencies) in the codebase.                         |
| `- <group ID>:<artifact ID>:<version>`           | Dependency on [a Kotlin or Java library](../user-guide/dependencies.md#external-maven-dependencies) in a Maven repository. |
| `- $<catalog.key>`                               | Dependency from [a dependency catalog](../user-guide/dependencies.md#library-catalogs-aka-version-catalogs).               |
| `- bom: <group ID>:<artifact ID>:<version>`      | Dependency on [a BOM](../user-guide/dependencies.md#bom-dependencies).                                                     |
| `- bom: $<catalog.key>`                          | Dependency on [a BOM from a dependency catalog](../user-guide/dependencies.md#library-catalogs-aka-version-catalogs).      |

Each dependency (except BOM) has the following attributes:

| Attribute           | Description                                                                                                                       | Default |
|---------------------|-----------------------------------------------------------------------------------------------------------------------------------|---------|
| `exported: boolean` | Whether a dependency should be [visible as a part of a published API](../user-guide/dependencies.md#scopes-and-visibility).       | `false` |
| `scope: enum`       | When the dependency should be used. Read more about the [dependency scopes](../user-guide/dependencies.md#scopes-and-visibility). | `all`   |

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

The `dependencies:` section can also be [qualified with a platform](../user-guide/multiplatform.md#platform-qualifier):

```yaml
# Dependencies used to build the common part of the product
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# Dependencies used to build the JVM part of the product
dependencies@jvm:
  - io.ktor:ktor-client-java:2.2.0
  - org.postgresql:postgresql:42.3.3: runtime-only
```

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

## `product`

The `product:` section defines what should be produced out of the module.
Read more about the [product types](../user-guide/basics.md#product-types).

| Attribute             | Description                                 |
|-----------------------|---------------------------------------------|
| `type: enum`          | What type of product to generate.           |
| `platform: enum list` | What platforms to generate the product for. |

Supported product types and platforms:

| Product Type   | Description                                                        | Platforms                                                     |
|----------------|--------------------------------------------------------------------|---------------------------------------------------------------|
| `lib`          | A reusable multiplatform library that other modules can depend on. | any (the list must be specified explicitly)                   |
| `jvm/lib`      | A JVM library that other modules can depend on.                    | `jvm`                                                         |
| `jvm/app`      | A JVM console or desktop application.                              | `jvm`                                                         |
| `linux/app`    | A native Linux application.                                        | `linuxX86`, `linuxArm64`                                      |
| `windows/app`  | A native Windows application.                                      | `mingwX64`                                                    |
| `macos/app`    | A native macOS application.                                        | `macosX64`, `macosArm64`                                      |
| `android/app`  | An Android VM application.                                         | `android`                                                     |
| `ios/app`      | An iOS application.                                                | device: `iosArm64`, simulators: `iosX64`, `iosSimulatorArm64` |
| `js/app`       | A JavaScript application.                                          | `js`                                                          |
| `wasmJs/app`   | A Wasm (JS) application.                                           | `wasmJs`                                                      |
| `wasmWasi/app` | A Wasm (WASI) application.                                         | `wasmWasi`                                                    |

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

`repositories:` section defines the list of repositories used to look up and download the module dependencies.
Read more about the [dependency repositories](../user-guide/dependencies.md#managing-maven-repositories).

| Attribute             | Description                                         | Default        | 
|-----------------------|-----------------------------------------------------|----------------| 
| `url: string`         | The url of the repository.                          |                |
| `id: string`          | The ID of the repository, used for to reference it. | repository url |
| `credentials: object` | Credentials for the authenticated repositories.     | empty          |

Read more on the [repository configuration](../user-guide/dependencies.md#managing-maven-repositories)
Credentials support username/password authentication and have the following attributes:

| Attribute             | Description                                                                                       |
|-----------------------|---------------------------------------------------------------------------------------------------|
| `file: path`          | A relative path to a file with the credentials. Currently, only `*.property` files are supported. |
| `usernameKey: string` | A key in the file that holds the username.                                                        |
| `passwordKey: string` | A key in the file that holds the password.                                                        |

Examples:

```yaml title="Short form"
repositories:
  - https://repo.spring.io/ui/native/release
  - https://jitpack.io
```

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

## `settings` and `test-settings`

`settings:` section configures the toolchains used in the build process. Read more
about [settings configuration](../user-guide/basics.md#settings).

`test-settings:` section controls building and running the module tests. Read more about
the [module tests](../user-guide/testing.md).

### `settings.android`

`settings:android:` configures the Android toolchain and platform.

| Attribute                     | Description                                                                                                                                                                                                                   | Default               |
|-------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|-----------------------|
| `applicationId: string`       | The ID for the application on a device and in the Google Play Store. [Read more](https://developer.android.com/build/configure-app-module#set-namespace).                                                                     | (namespace)           |
| `namespace: string`           | A Kotlin or Java package name for the generated `R` and `BuildConfig` classes. [Read more](https://developer.android.com/build/configure-app-module#set-namespace).                                                           | org.example.namespace |
| `compileSdk: int`             | The API level to compile the code. The code can use only the Android APIs up to that API level. [Read more](https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CommonExtension#compileSdk()). | (targetSdk)           |
| `targetSdk: int`              | The target API level for the application. [Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                             | 35                    |
| `minSdk: int`                 | Minimum API level needed to run the application. [Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                      | 21                    |
| `maxSdk: int`                 | Maximum API level on which the application can run. [Read more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                   |                       |
| `signing: object`             | Android signing settings. [Read more](https://developer.android.com/studio/publish/app-signing).                                                                                                                              |                       |
| `versionCode: int`            | Version code. [Read more](https://developer.android.com/studio/publish/versioning).                                                                                                                                           | 1                     |
| `versionName: string`         | Version name. [Read more](https://developer.android.com/studio/publish/versioning).                                                                                                                                           | unspecified           |
| `parcelize: object \| string` | Configure [Parcelize](https://developer.android.com/kotlin/parcelize).                                                                                                                                                        |                       |

#### `settings.android.parcelize`

`settings:android:parcelize` configures [Parcelize](https://developer.android.com/kotlin/parcelize) for the Android
platform in the module. The value can be the simple `enabled` string, or an object with the following attributes:

| Attribute                            | Description                                                                                                                                                                                                                                                                                                                                                                | Default |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `enabled: boolean`                   | Whether to enable [Parcelize](https://developer.android.com/kotlin/parcelize). When enabled, an implementation of the `Parcelable` interface is automatically generated for classes annotated with `@Parcelize`.                                                                                                                                                           |         |
| `additionalAnnotations: string list` | The full-qualified names of additional annotations that should be considered as `@Parcelize`. This is useful if you need to annotate classes in common code shared between different platforms, where the real `@Parcelize` annotation is not available. In that case, create your own common annotation and add its fully-qualified name so that Parcelize recognizes it. | (empty) |

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

`settings:android:signing:` configures signing of Android apps [Read more](https://developer.android.com/studio/publish/app-signing)


| Attribute              | Description                                                                                            | Default               |
|------------------------|--------------------------------------------------------------------------------------------------------|-----------------------|
| `enabled: boolean`     | Whether signing enabled or not. [Read more](https://developer.android.com/studio/publish/app-signing). | false                 |
| `propertiesFile: path` | Location of properties file. [Read more](https://developer.android.com/studio/publish/app-signing).    | ./keystore.properties |

### `settings.compose`

`settings:compose:` configures the [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
framework. Read more about [Compose configuration](../user-guide/builtin-tech/compose-multiplatform.md).

| Attribute              | Description                                                    | Default |
|------------------------|----------------------------------------------------------------|---------|
| `enabled: boolean`     | Enable Compose runtime, dependencies and the compiler plugins. | `false` |
| `version: string`      | The Compose plugin version to use.                             | `1.7.3` |
| `resources: object`    | Compose Resources settings.                                    |         |
| `experimental: object` | Experimental Compose settings.                                 |         |

`settings:compose:resources:` configures Compose Resources settings.

| Attribute                   | Description                                                                                                                                                 | Default |
|-----------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `packageName: string`       | A unique identifier for the resources in the current module. Used as package for the generated Res class and for isolating resources in the final artifact. | `""`    |
| `exposedAccessors: boolean` | Whether the generated resources accessors should be exposed to other modules (public) or internal.                                                          | `false` |

`settings:compose:experimental:` configures experimental Compose features.

| Attribute           | Description                               | Default |
|---------------------|-------------------------------------------|---------|
| `hotReload: object` | Experimental Compose hot-reload settings. |         |

`settings:compose:experimental:hotReload:` configures experimental hot reload.

| Attribute          | Description       | Default |
|--------------------|-------------------|---------|
| `enabled: boolean` | Enable hot reload | `false` |

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

`settings:java:` configures the Java language and the compiler.

| Attribute                      | Description                         | Default |
|--------------------------------|-------------------------------------|---------|
| `annotationProcessing: object` | Java annotation processing settings | (empty) |

#### `settings.java.annotationProcessing`

`settings:java:annotationProcessing:` configures Java annotation processing.

| Attribute               | Description                                                                                                                    | Default |
|-------------------------|--------------------------------------------------------------------------------------------------------------------------------|---------|
| `processors: list`      | The list of annotation processors to use. Each item can be a path to a local module, a catalog reference, or maven coordinates | (empty) |
| `processorOptions: map` | Options to pass to annotation processors                                                                                       | (empty) |

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

`settings:junit:` configures the JUnit test runner on the JVM and Android platforms. Read more
about [testing support](../user-guide/testing.md).

By default, JUnit 5 is used.

| Value     | Description                                                        |
|-----------|--------------------------------------------------------------------|
| `junit-5` | JUnit 5 dependencies and the test runner are configured (default). |
| `junit-4` | JUnit 4 dependencies and the test runner are configured.           |
| `none`    | JUnit is not automatically configured.                             |

### `settings.jvm`

`settings:jvm:` configures the JVM platform-specific settings.

| Attribute                      | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Default                                      |
|--------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|----------------------------------------------|
| `release: enum`                | The minimum JVM release version that the code should be compatible with. This enforces compatibility on 3 levels. First, it is used as the target version for the bytecode generated from Kotlin and Java sources. Second, it limits the Java platform APIs available to Kotlin and Java sources. Third, it limits the Java language constructs in Java sources. If this is set to null, these constraints are not applied and the compiler defaults are used. | 17                                           |
| `mainClass: string`            | (Only for `jvm/app` [product type](../user-guide/basics.md#product-types)) The fully-qualified name of the class used to run the application.                                                                                                                                                                                                                                                                                                                  | [auto-detected](../user-guide/basics.md#jvm) |
| `storeParameterNames: boolean` | Enables storing formal parameter names of constructors and methods in the generated class files. These can later be accessed using reflection.                                                                                                                                                                                                                                                                                                                 | false                                        |

#### `settings.jvm.test`

`settings:jvm:test:` configures the test settings on the JVM and Android platforms.
Read more about [testing support](../user-guide/testing.md).

| Value                      | Description                                                                                                   |
|----------------------------|---------------------------------------------------------------------------------------------------------------|
| `systemProperties: map`    | JVM system properties for the test process.                                                                   |
| `jvmFreeArgs: string list` | Free JVM arguments for the test process.                                                                      |

### `settings.kotlin`

`settings:kotlin:` configures the Kotlin language and the compiler.

| Attribute                       | Description                                                                                                                                                          | Default           |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `languageVersion: enum`         | Provide source compatibility with the specified version of Kotlin.                                                                                                   | 2.1               |
| `apiVersion: enum`              | Allow using declarations only from the specified version of Kotlin bundled libraries.                                                                                | (languageVersion) |
| `allWarningsAsErrors: boolean`  | Turn any warnings into a compilation error.                                                                                                                          | `false`           |
| `suppressWarnings: boolean`     | Suppress the compiler from displaying warnings during compilation.                                                                                                   | `false`           |
| `verbose: boolean`              | Enable verbose logging output which includes details of the compilation process.                                                                                     | `false`           |
| `progressiveMode: boolean`      | Enable the [progressive mode for the compiler](https://kotlinlang.org/docs/compiler-reference.html#progressive).                                                     | `false`           |
| `optIns: enum list`             | Enable usages of API that [requires opt-in](https://kotlinlang.org/docs/opt-in-requirements.html) with a requirement annotation with the given fully qualified name. | (empty)           |
| `freeCompilerArgs: string list` | Pass any [compiler option](https://kotlinlang.org/docs/compiler-reference.html#compiler-options) directly.                                                           |                   |
| `debug: boolean`                | (Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) Enable emitting debug information.                                               | `true`            |
| `serialization: object \| enum` | Configure [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization).                                                                                   |                   |
| `allOpen: object`               | Configure the [Kotlin all-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html).                                                                   |                   |
| `noArg: object`                 | Configure the [Kotlin no-arg compiler plugin](https://kotlinlang.org/docs/no-arg-plugin.html).                                                                       |                   |

The `serialization:` attribute is an object with the following properties:

| Attribute          | Description                                                                                                                                                                           | Default |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `enabled: boolean` | Enable the `@Serializable` annotation processing, and add the core serialization library. When enabled, a built-in catalog for kotlinx.serialization format dependencies is provided. | `false` |
| `format: enum`     | A shortcut for `enabled: true` and adding the given serialization format dependency. For instance, `json` adds the JSON format in addition to enabling serialization.                 |         |
| `version: string`  | The version to use for the core serialization library and the serialization formats.                                                                                                  | `1.7.3` |

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
      version: 1.7.3
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
      version: 1.7.3

dependencies:
  - $kotlin.serialization.json
  - $kotlin.serialization.protobuf
```

#### `settings.kotlin.allOpen`

`settings:kotlin:allOpen` configures the [Kotlin all-open compiler plugin](https://kotlinlang.org/docs/all-open-plugin.html),
which makes classes annotated with specific annotations open automatically without the explicit `open` keyword.

| Attribute                  | Description                                                                                                    | Default |
|----------------------------|----------------------------------------------------------------------------------------------------------------|---------|  
| `enabled: boolean`         | Enable the Kotlin all-open compiler plugin                                                                     | `false` |  
| `annotations: string list` | List of annotations that trigger open class/method generation                                                  | (empty) |  
| `presets: enum list`       | Predefined sets of annotations for common frameworks (available presets: `spring`, `micronaut`, and `quarkus`) | (empty) |  

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

#### `settings.kotlin.noArg`

`settings:kotlin:noArg` configures the [Kotlin no-arg compiler plugin](https://kotlinlang.org/docs/no-arg-plugin.html),
which generates no-arg constructors for classes with specific annotations.

| Attribute                     | Description                                                                             | Default |
|-------------------------------|-----------------------------------------------------------------------------------------|---------|  
| `enabled: boolean`            | Enable the Kotlin no-arg compiler plugin                                                | `false` |  
| `annotations: string list`    | List of annotations that trigger no-arg constructor generation                          | (empty) |  
| `invokeInitializers: boolean` | Whether to call initializers in the synthesized constructor                             | `false` |  
| `presets: enum list`          | Predefined sets of annotations (currently only `jpa` preset for JPA entity annotations) | (empty) |  

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

### `settings.ktor`

`settings:ktor:` configures the Ktor.

| Attribute          | Description  | Default |
|--------------------|--------------|---------|  
| `enabled: boolean` | Enable Ktor  | `false` |  
| `version: string`  | Ktor version | `3.1.1` |  

Example:

```yaml
settings:
  ktor:
    enabled: true
    version: 2.3.2 # version customization
```

### `settings.lombok`

`settings:lombok:` configures Lombok.

| Attribute          | Description    | Default |
|--------------------|----------------|---------|  
| `enabled: boolean` | Enable Lombok  | `false` |  

Example:

```yaml
settings:
  lombok:
    enabled: true
```

### `settings.native`

`settings:native:` configures settings specific to native applications.

| Attribute            | Description                                                        | Default |
|----------------------|--------------------------------------------------------------------|---------|
| `entryPoint: string` | The fully-qualified name of the application's entry point function | `null`  |

Example:

```yaml
# Configure native settings for the module
settings:
  native:
    entryPoint: com.example.MainKt.main
```

### `settings.springBoot`

`settings:springBoot:` configures the Spring Boot framework (JVM platform only).

| Attribute          | Description               | Default |
|--------------------|---------------------------|---------|  
| `enabled: boolean` | Enable Spring Boot        | `false` |  
| `version: string`  | Spring Boot version       | `3.4.3` |  

Example:

```yaml
settings:
  springBoot:
    enabled: true
    version: 3.1.0 # version customization
```
