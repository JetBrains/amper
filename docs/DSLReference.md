## Project file

### Modules

The `modules:` section lists all the modules in the project, except the root module.
If a `module.yaml` is present at the root of the project, the root module is implicitly included and doesn't need to be
listed.

Each element in the list must be the path to a module directory, relative to the project root.
A module directory must contain a `module.yaml` file.

Example:

```yaml
# include the `app` and `lib1` modules explicitly:
modules:
  - ./app
  - ./libs/lib1
```

You can also use [Glob patterns](https://en.wikipedia.org/wiki/Glob_(programming)) to include multiple module 
directories at the same time.
Only directories that contain a `module.yaml` file will be considered when matching a glob pattern.

- `*` matches zero or more characters of a path name component without crossing directory boundaries
- `?` matches exactly one character of a path name component
- `[abc]` matches exactly one character of the given set (here `a`, `b`, or `c`). A dash (`-`) can be used to match a range, such as `[a-z]`.

Using `**` to recursively match directories at multiple depth levels is not supported.

Example:

```yaml
# include all direct subfolders in the `plugins` dir that contain `module.yaml` files:
modules:
  - ./plugins/*
```

## Module file

### Product

`product:` section defines what should be produced out of the module.
Read more about the [product types](Documentation.md#product-types).

| Attribute             | Description                                 |
|-----------------------|---------------------------------------------|
| `type: enum`          | What type of product to generate.           |
| `platform: enum list` | What platforms to generate the product for. |

Supported product types and platforms:

| Product Type  | Description                                                                            | Platforms                                                     |
|---------------|----------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `lib`         | A reusable library which could be used as dependency by other modules in the codebase. | any                                                           |
| `jvm/app`     | A JVM console or desktop application.                                                  | `jvm`                                                         |
| `linux/app`   | A native Linux application.                                                            | `linuxX86`, `linuxArm64`                                      |
| `windows/app` | A native Windows application.                                                          | `mingwX64`                                                    |
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
# Full form, with an explicitly specified platform
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

`variants:` section defines the list of build variants for the product. Variant names can be used as `@platform` qualifiers.

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

`module:` section configures various aspects of the module, such as file layout.

| Attribute      | Description                                                                                                                           | Default   |
|----------------|---------------------------------------------------------------------------------------------------------------------------------------|-----------|
| `layout: enum` | (Gradle-based projects only) File layout of the module. [Read more](GradleBasedProjects.md#file-layout-with-gradle-interoperability). | `default` |

Supported file layouts:

| Attribute    | Description                                                                                                                                           |
|--------------|-------------------------------------------------------------------------------------------------------------------------------------------------------|
| `default`    | The [default Amper file layout](Documentation.md#project-layout) is used.                                                                             |
| `gradle-jvm` | The file layout corresponds to the standard Gradle [JVM layout](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html).           |
| `gradle-kmp` | The file layout corresponds to the Gradle [Kotlin Multiplatform layout](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets). |

See more on the layouts in the [documentation](GradleBasedProjects.md#file-layout-with-gradle-interoperability).

Examples:

```yaml
# Layout is not specified, default module layout is used
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

### Templates

`apply:` section lists the templates applied to the module. Read more about the [module templates](Documentation.md#templates)

Use `- ./<relative path>` or `- ../<relative path>` notation, where the `<relative path>` points at a template file.

Examples:

```yaml
# Apply a `common.module-template.yaml` template to the module
product: jvm/app

apply:
  - ../common.module-template.yaml
```

### Dependencies and test dependencies

`dependencies:` section defines the list of modules and libraries necessary to build the module.
Certain dependencies can also be exported as part of the module API.
Read more about the [dependencies](Documentation.md#dependencies).

`test-dependencies:` section defines the dependencies necessary to build and run tests of the module. Read more about
the [module tests](Documentation.md#tests).

Supported dependency types:

| Notation                                         | Description                                                                                                   |
|--------------------------------------------------|---------------------------------------------------------------------------------------------------------------|
| `- ./<relative path>`<br/>`- ../<relative path>` | Dependency on [another module](Documentation.md#module-dependencies) in the codebase.                         |
| `- <group ID>:<artifact ID>:<version>`           | Dependency on [a Kotlin or Java library](Documentation.md#external-maven-dependencies) in a Maven repository. |
| `- $<catalog.key>`                               | Dependency from [a dependency catalog](Documentation.md#library-catalogs-aka-version-catalogs).               |
| `- bom: <group ID>:<artifact ID>:<version>`      | Dependency on [a BOM](Documentation.md#external-maven-bom-dependencies).                                      |
| `- bom: $<catalog.key>`                          | Dependency on [a BOM from a dependency catalog](Documentation.md#library-catalogs-aka-version-catalogs).      |

Each dependency (except BOM) has the following attributes:

| Attribute           | Description                                                                                                          | Default |
|---------------------|----------------------------------------------------------------------------------------------------------------------|---------|
| `exported: boolean` | Whether a dependency should be [visible as a part of a published API](Documentation.md#scopes-and-visibility).       | `false` |
| `scope: enum`       | When the dependency should be used. Read more about the [dependency scopes](Documentation.md#scopes-and-visibility). | `all`   |

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

The `dependencies:` section can also be [qualified with a platform](Documentation.md#platform-qualifier).

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

`repositories:` section defines the list of repositories used to look up and download the module dependencies.
Read more about the [dependency repositories](Documentation.md#managing-maven-repositories).

| Attribute             | Description                                         | Default        | 
|-----------------------|-----------------------------------------------------|----------------| 
| `url: string`         | The url of the repository.                          |                |
| `id: string`          | The ID of the repository, used for to reference it. | repository url |
| `credentials: object` | Credentials for the authenticated repositories.     | empty          |

Read more on the [repository configuration](Documentation.md#managing-maven-repositories)
Credentials support username/password authentication and have the following attributes:

| Attribute             | Description                                                                                       |
|-----------------------|---------------------------------------------------------------------------------------------------|
| `file: path`          | A relative path to a file with the credentials. Currently, only `*.property` files are supported. |
| `usernameKey: string` | A key in the file that holds the username.                                                        |
| `passwordKey: string` | A key in the file that holds the password.                                                        |

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

`test-settings:` section controls building and running the module tests. Read more about
the [module tests](Documentation.md#tests).

#### Kotlin

`settings:kotlin:` configures the Kotlin language and the compiler.

| Attribute                       | Description                                                                                                                                                          | Default           |
|---------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|-------------------|
| `languageVersion: enum`         | Provide source compatibility with the specified version of Kotlin.                                                                                                   | 2.0               |
| `apiVersion: enum`              | Allow using declarations only from the specified version of Kotlin bundled libraries.                                                                                | (languageVersion) |
| `allWarningsAsErrors: boolean`  | Turn any warnings into a compilation error.                                                                                                                          | `false`           |
| `suppressWarnings: boolean`     | Suppress the compiler from displaying warnings during compilation.                                                                                                   | `false`           |
| `verbose: boolean`              | Enable verbose logging output which includes details of the compilation process.                                                                                     | `false`           |
| `progressiveMode: boolean`      | Enable the [progressive mode for the compiler](https://kotlinlang.org/docs/compiler-reference.html#progressive).                                                     | `false`           |
| `optIns: enum list`             | Enable usages of API that [requires opt-in](https://kotlinlang.org/docs/opt-in-requirements.html) with a requirement annotation with the given fully qualified name. | (empty)           |
| `freeCompilerArgs: string list` | Pass any [compiler option](https://kotlinlang.org/docs/compiler-reference.html#compiler-options) directly.                                                           |                   |
| `debug: boolean`                | (Only for [native targets](https://kotlinlang.org/docs/native-target-support.html)) Enable emitting debug information.                                               | `true`            |
| `serialization: object \| enum` | Configure [Kotlin serialization](https://github.com/Kotlin/kotlinx.serialization).                                                                                   |                   |

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

#### JVM

`settings:jvm:` configures the JVM platform-specific settings.

| Attribute           | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                    | Default                               |
|---------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------------------------------------|
| `release: enum`     | The minimum JVM release version that the code should be compatible with. This enforces compatibility on 3 levels. First, it is used as the target version for the bytecode generated from Kotlin and Java sources. Second, it limits the Java platform APIs available to Kotlin and Java sources. Third, it limits the Java language constructs in Java sources. If this is set to null, these constraints are not applied and the compiler defaults are used. | 17                                    |
| `mainClass: string` | (Only for `jvm/app` [product type](Documentation.md#product-types)) The fully-qualified name of the class used to run the application.                                                                                                                                                                                                                                                                                                                         | [auto-detected](Documentation.md#jvm) |

##### JVM Tests

`settings:jvm:test:` configures the test settings on the JVM and Android platforms.
Read more about [testing support](Documentation.md#tests).

| Value                      | Description                                                                                                   |
|----------------------------|---------------------------------------------------------------------------------------------------------------|
| `systemProperties: map`    | JVM system properties for the test process.                                                                   |
| `jvmFreeArgs: string list` | Free JVM arguments for the test process.                                                                      |

#### Android

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

##### Android signing

`settings:android:signing:` configures signing of Android apps [Read more](https://developer.android.com/studio/publish/app-signing)


| Attribute              | Description                                                                                            | Default               |
|------------------------|--------------------------------------------------------------------------------------------------------|-----------------------|
| `enabled: boolean`     | Whether signing enabled or not. [Read more](https://developer.android.com/studio/publish/app-signing). | false                 |
| `propertiesFile: path` | Location of properties file. [Read more](https://developer.android.com/studio/publish/app-signing).    | ./keystore.properties |

##### Parcelize

`settings:android:parcelize` configures [Parcelize](https://developer.android.com/kotlin/parcelize) for the Android
platform in the module. The value can be the simple `enabled` string, or an object with the following attributes:

| Attribute                            | Description                                                                                                                                                                                                                                                                                                                                                                | Default |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `enabled: boolean`                   | Whether to enable [Parcelize](https://developer.android.com/kotlin/parcelize). When enabled, an implementation of the `Parcelable` interface is automatically generated for classes annotated with `@Parcelize`.                                                                                                                                                           |         |
| `additionalAnnotations: string list` | The full-qualified names of additional annotations that should be considered as `@Parcelize`. This is useful if you need to annotate classes in common code shared between different platforms, where the real `@Parcelize` annotation is not available. In that case, create your own common annotation and add its fully-qualified name so that Parcelize recognizes it. | (empty) |

```yaml
# Enables Parcelize to process @Parcelize-annotated classes (short form)
settings:
  android:
    parcelize: enabled
```

```yaml
# Enables Parcelize, and configures it to process a custom @com.example.MyCommonParcelize annotation
settings:
  android:
    parcelize:
      enabled: true
      additionalAnnotations: [ com.example.MyCommonParcelize ]
```

#### iOS

`settings:ios:` configures the iOS toolchain and platform.

| Attribute           | Description                                                                                                                                                                                                                                                                                                                              | Default |
|---------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|---------|
| `teamID: string`    | A Team ID is a unique string assigned to your team by Apple.<br>It's necessary if you want to run/test on a Apple device.<br>Read [how to locate your team ID in Xcode](https://developer.apple.com/help/account/manage-your-team/locate-your-team-id/), or use [KDoctor tool](https://github.com/Kotlin/kdoctor) (`kdoctor --team-ids`) | empty   |
| `framework: object` | (Only for the library [product type](Documentation.md#product-types) Configure the generated framework to [share the common code with an Xcode project](https://kotlinlang.org/docs/multiplatform-mobile-understand-project-structure.html#ios-framework).                                                                               |         |

`settings:ios:framework:` configures the generated framework. By default, a dynamically linked framework with the name of the module is generated

| Attribute           | Description                                                            | Default |
|---------------------|------------------------------------------------------------------------|---------|
| `basename: string`  | The name of the generated framework.                                   | kotlin  |
| `isStatic: boolean` | Whether to create a dynamically linked or statically linked framework. | false   |

#### Compose

`settings:compose:` configures the [Compose Multiplatform](https://www.jetbrains.com/lp/compose-multiplatform/)
framework. Read more about [Compose configuration](Documentation.md#configuring-compose-multiplatform).

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
    version: 1.6.10
```

```yaml
# Full form with resources configuration
settings:
  compose:
    enabled: true
    version: 1.6.10
    resources:
      packageName: "com.example.myapp.resources"
      exposedAccessors: true
```

```yaml
# With experimental hot reload enabled
settings:
  compose:
    enabled: true
    experimental:
      hotReload: enabled
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

#### Kover

(Gradle-based projects only) `settings:kover:` configures Kover for code coverage. Read more about [Kover](https://kotlin.github.io/kotlinx-kover/gradle-plugin/)

| Attribute          | Description                    | Default |
|--------------------|--------------------------------|---------|
| `enabled: boolean` | Enable code overage with Kover | `false` |

`settings:kover:html` configures HTML reports

| Attribute       | Description                                                           | Default     |
|-----------------|-----------------------------------------------------------------------|-------------|
| `title: string` | The title for the coverage report                                     | module name |
| `reportDir`     | The directory (relative to project root) to store coverage reports in | `null`      |
| `onCheck`       | Run html report on check task                                         | `false`     |
| `charset`       | Charset to pass to kover                                              | `null`      |

`settings:kover:xml` configures XML reports

| Attribute       | Description                                                      | Default     |
|-----------------|------------------------------------------------------------------|-------------|
| `reportFile`    | The file (relative to project root) to store coverage reports in | `null`      |
| `onCheck`       | Run html report on check task                                    | `false`     |

Examples:

```yaml
settings:
  kover:
    enabled: true
    html:
      title: "A title"
      reportDir: build/report
      onCheck: true
    xml:
      onCheck: true
      reportFile: build/report.xml
```
