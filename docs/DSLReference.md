### Product

`product:` section defines what should be produced out of the Module.
Read more about the [product types](Documentation.md#product-types).

| Attribute             | Description                                 |
|-----------------------|---------------------------------------------|
| `type: enum`          | What type of the product to generate.       |
| `platform: enum list` | What platforms to generate the product for. |

Supported product types and platforms:

| Product Type  | Description                                                                            | Platforms                                                     |
|---------------|----------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `lib`         | A reusable library which could be used as dependency by other modules in the codebase. | any                                                           |
| `jvm/app`     | A JVM console or desktop application.                                                  | `jvm`                                                         |
| `linux/app`   | A native linux application.                                                            | `linuxX86`, `linuxArm64`                                      |
| `macos/app`   | A native macOS application.                                                            | `macosX64`, `macosArm64`                                      |
| `android/app` | An Android VM application.                                                             | `android`                                                     |
| `ios/app`     | An iOS application.                                                                    | device: `iosArm64`, simulators: `iosX64`, `iosSimulatorArm64` |

Check the list of all [Kotlin Multiplatform targets](https://kotlinlang.org/docs/native-target-support.html) and level
of their support.

Examples:

```yaml
# Short form, defaults to all supported platforms for the corresponding target:
product: macos/app
```

```yaml
# Full form, with explicitly specified platform
product:
  type: macos/app
  platforms: [ macosArm64, macosArm64 ]
```

```yaml
# Multiplatform Library for JVM and Android platforms 
product:
  type: lib
  platforms: [ jvm, android ]
```

### Aliases

`aliases:` section defines the names for the custom code sharing groups. Aliases can be used as `@platform` qualifiers. Read more about [aliases](Documentation.md#aliases).

Examples:

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

### Variants

`variants:` section defines the list of build variants for the product. Variants name can be used as `@platform` qualifiers. Read more about [variants](Documentation.md#build-variants).

Examples:

```yaml
# Define two build variants: `debug` and `release`  
product: android/app

variant: [debug, release]

# Dependencies for the debug build variant:
dependencies@debug:
  ...
```

```yaml
# Define multi-dimensional variants   
product: android/app

variants:
  - [debug, release]
  - [paid, free]

dependencies@paid:
  - ...

dependencies@debug:
  - ...
```

### Module

`module:` section defines the non-code/product related aspects of the Module, such as file layout.

| Attribute      | Description               | Default   |
|----------------|---------------------------|-----------|
| `layout: enum` | Which file layout to use. | `default` |

Supported file layouts:

| Attribute    | Description                                                                                                                                    |
|--------------|------------------------------------------------------------------------------------------------------------------------------------------------|
| `default`    | [Default file layout](Documentation.md#project-layout) is used.                                                                                |
| `gradle-jvm` | The file layout corresponds to the standard Gradle [JVM layout](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html).    |
| `gradle-kmp` | The file layout corresponds to the [Kotlin Multiplatform layout](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets). |

See more on the layouts in the [documentation](Documentation.md#file-layout-with-gradle-interop).

Examples:

```yaml
# Layout is not specified, default Module layout is used
product: jvm/app
```

```yaml
# Use Gradle Kotlin Multiplatform compatibility mode for the file layout 
product:
  type: lib
  platforms: [ android, jvm ]

module:
  layout: gradle-kmp
```

### Dependencies and test dependencies

`dependecies:` section defines the list of modules and libraries necessary to build the Module.
Certain dependencies can also be exported as part of the Module API.
Read more about the [dependencies](Documentation.md#dependencies).

`test-dependenceis:` section defines the dependencies necessary to build and run tests of the Module. Read more about
the [Module tests](Documentation.md#tests).

Supported dependency types:

| Notation                                         | Description                                                                                                   |
|--------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `- ./<relative path>`<br/>`- ../<relative path>` | [Dependency on another Module](Documentation.md#internal-dependencies) in the codebase.                       |
| `- <group ID>:<artifact ID>:<version>`           | [Dependency on a Kotlin or Java library](Documentation.md#external-maven-dependencies) in a Maven repository. |
| `- $<catalog.key>`                               | [Dependency from a dependency catalog](Documentation.md#dependencyversion-catalogs).                          |

Each dependency has the following attributes:

| Attribute           | Description                                                                                                   | Default |
|---------------------|---------------------------------------------------------------------------------------------------------------|---------|
| `exported: boolean` | Whether a dependency should be [visible as a par of a published API](Documentation.md#scopes-and-visibility). | `false` |
| `scope: enum`       | [When in the build process](Documentation.md#scopes-and-visibility) should a dependency be used.              | `all`   |

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

The `dependencies:` section could also be [qualified with a platform](Documentation.md#platform-qualifier) or
a [build variant](Documentation.md#build-variants).

Examples:

```yaml
# Dependencies used to build the common part of the product
dependencies:
  - io.ktor:ktor-client-core:2.2.0

# Dependencies used to build the JVM part of the product
dependencies@jvm:
  - io.ktor:ktor-client-java:2.2.0
  - org.postgresql:postgresql:42.3.3: runtime-only
```

### Repositories

`repositories:` section defines the list of repositories used to look up and download the Module dependencies.
Read more about the [dependency repositories](Documentation.md#managing-maven-repositories).

| Attribute             | Description                                         |
|-----------------------|-----------------------------------------------------|
| `id: string`          | The ID of the repository, used for to reference it. |
| `url: string`         | The url to the repository.                          |
| `credentials: object` | Credentials for the authenticated repositories.     |

Read more on the [repository configuration](Documentation.md#managing-maven-repositories)
Credentials support username/password authentication and have the following attributes:

| Attribute             | Description                                     |
|-----------------------|-------------------------------------------------|
| `file: path`          | A relative path to a file with the credentials. |
| `usernameKey: string` | A key in the file that holds the username.      |
| `passwordKey: string` | A key in the file that holds the password.      |

Examples:

```yaml
# Short form
repositories:
  - https://repo.spring.io/ui/native/release
  - https://jitpack.io
```

```yaml
# Full form
repositories:
  - url: https://repo.spring.io/ui/native/release
  - id: jitpack
    url: https://jitpack.io
```

```yaml
# Specifying the credentials
repositories:
  - url: https://my.private.repository/
    credentials:
      file: ./local.properties
      usernameKey: my.private.repository.username
      passwordKey: my.private.repository.password  
```

### Settings and test settings

`settings:` section configures the toolchains used in the build process. Read more
about [settings configuration](Documentation.md#settings).

`test-settings:` section controls building and running the Module tests. Read more about
the [Module tests](Documentation.md#tests).

#### Kotlin

`settings:kotlin:` configures the Kotlin language and the compiler.

| Attribute                       | Description                                                                                                                                                          | Default |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `languageVersion: enum`         | Provide source compatibility with the specified version of Kotlin.                                                                                                   |         |
| `apiVersion: enum`              | Allow using declarations only from the specified version of Kotlin bundled libraries.                                                                                |         |
| `allWarningsAsErrors: boolean`  | Turn any warnings into a compilation error.                                                                                                                          |         |
| `suppressWarnings: boolean`     | Suppress the compiler from displaying warnings during compilation.                                                                                                   |         |
| `verbose: boolean`              | Enable verbose logging output which includes details of the compilation process.                                                                                     |         |
| `progressiveMode: boolean`      | Enable the [progressive mode for the compiler](https://kotlinlang.org/docs/compiler-reference.html#progressive).                                                     |         |
| `optIns: enum list`             | Enable usages of API that [requires opt-in](https://kotlinlang.org/docs/opt-in-requirements.html) with a requirement annotation with the given fully qualified name. |         |
| `freeCompilerArgs: string list` | Pass any [compiler option](https://kotlinlang.org/docs/compiler-reference.html#compiler-options) directly.                                                           |         |
| `debug: boolean`                | (Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) Enable emitting debug information.                                               |         |
| `serialization: object \| enum` | Configure the [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization).                                                                               |         |
`serialization:` attribute could an object or an enum corresponding to the `format:` attribute:

| Attribute       | Description                                                                    | Default |
|-----------------|--------------------------------------------------------------------------------|---------|
| `format: enum ` | `none` to disable serialization, `JSON` to enable serialization in JSON format | `none`  |

Examples:

```yaml
# Set Kotlin language version and opt-ins
settings:
  kotlin:
    languageVersion: 1.8
```

```yaml
# Enabling Kotlin Serialization 
settings:
  kotlin:
    serialization: json
```

```yaml
# Enabling Kotlin Serialization 
settings:
  kotlin:
    serialization: json
```

#### Java

`settings:java:` configures the Java language and the compiler for the JVM platform.

| Attribute               | Description                                                                                                                         | Default |
|-------------------------|-------------------------------------------------------------------------------------------------------------------------------------|---------|
| `source: enum`          | A Java language version of the source files.                                                                                        | 17      |

#### JVM

`settings:jvm:` configures the JVM platform specific settings.

| Attribute           | Description                                                                                                                         | Default                               |
|---------------------|-------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| `target: enum`      | A bytecode version of generated jvm bytecode (by java or kotlin compiler).                                                          | 17                                    |
| `mainClass: string` | (Only for `jvm/app` [product type](Documentation.md#product-types) A fully-qualified name of the class used to run the application. | [auto-detected](Documentation.md#jvm) |

#### Android

`settings:android:` configures the Android toolchain and platform.

| Attribute                   | Description                                                                                                                                                                                                                                   | Default |
|-----------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `applicationId: string`     | The ID for the application on the device and in the Google Play Store. Read [more](https://developer.android.com/build/configure-app-module#set-namespace).                                                                                   |         |
| `namespace: string`         | A Kotlin or Java package name for the generated `R` and `BuildConfig` classes. Read [more](https://developer.android.com/build/configure-app-module#set-namespace).                                                                           |         |
| `compileSdk: string` | The API level to compile the code. The core can use only the Android APIs included in that API level and lower. Read [more](https://developer.android.com/reference/tools/gradle-api/com/android/build/api/dsl/CommonExtension#compileSdk()). |         |
| `targetSdk: int \| string`  | The API level that the application targets. Read [more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                                           |         |
| `minSdk: int \| string`     | Minimum API level required for the application to run. Read [more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                                |         |
| `maxSdk: int`               | Maximum API level on which the application is designed to run. Read [more](https://developer.android.com/guide/topics/manifest/uses-sdk-element.html).                                                                                        |         |

#### iOS

`settings:ios:` configures the iOS toolchain and platform.

| Attribute        | Description                                                                                                                                                                                                                                                                | Default |
|------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `teamID: string` | A Team ID is a unique 10-character string generated by Apple that’s assigned to your team.<br>It's necessary if you want to run/test on a Apple device. Read [how to locate your team ID](https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/). | empty   |

#### Compose

`settings:compose:` configures the [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
framework. Read more about [Compose configuration](Documentation.md#configuring-compose-multiplatform).

| Attribute          | Description                                                    | Default |
|--------------------|----------------------------------------------------------------|---------|
| `enabled: boolean` | Enable Compose runtime, dependencies and the compiler plugins. | `false` |

Examples:

```yaml
# Short form
settings:
  compose: enabled
```

```yaml
# Full form
settings:
  compose:
    enabled: true
```

#### JUnit

`settings:junit:` configures the JUnit test runner on the JVM and Android platforms. Read more
about [testing support](Documentation.md#tests).

By default, JUnit 4 is used.

| Value     | Description                                              |
|-----------|----------------------------------------------------------|
| `junit-5` | JUnit 5 dependencies and the test runner are configured. |
| `junit-4` | JUnit 4 dependencies and the test runner are configured. |
| `none`    | JUnit is not automatically configured.                   |
