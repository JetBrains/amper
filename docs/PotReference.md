### Product

`product:` section defines what should be produced out of the Pot.
Read more about the [product types](Documentation.md#product-types).

| Attribute             | Description                                |
|-----------------------|--------------------------------------------|
| `type: enum`          | What type of the product to generate       |
| `platform: enum list` | What platforms to generate the product for |

Supported product types and platforms:

| Product Type  | Description                                                                         | Platforms                                                     |
|---------------|-------------------------------------------------------------------------------------|---------------------------------------------------------------|
| `lib`         | A reusable library which could be used as dependency by other Pots in the codebase. | any                                                           |
| `jvm/app`     | A JVM console or desktop application                                                | `jvm`                                                         |
| `linux/app`   | A native linux application                                                          | `linuxX86`, `linuxArm64`                                      |
| `macos/app`   | A native macOS application                                                          | `macosX64`, `macosArm64`                                      |
| `android/app` | An Android VM application                                                           | `android`                                                     |
| `ios/app`     | An iOS application                                                                  | device: `iosArm64`, simulators: `iosX64`, `iosSimulatorArm64` |

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

### Pot

`pot:` section defines the non-code/product related aspects of the Pot, such as file layout.

| Attribute      | Description                  |
|----------------|------------------------------|
| `layout: enum` | What Pot file layout to use. |

Supported file layouts:

| Attribute    | Description                                                                                                                                   |
|--------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| `default`    | [Pot file layout](Documentation.md#project-layout) is used                                                                                    |
| `gradle-jvm` | The file layout corresponds to the standard Gradle [JVM layout](https://docs.gradle.org/current/userguide/organizing_gradle_projects.html)    |
| `gradle-kmp` | The file layout corresponds to the [Kotlin Multiplatform layout](https://kotlinlang.org/docs/multiplatform-discover-project.html#source-sets) |

See more on the layouts in the [documentation](Documentation.md#file-layout-with-gradle-interop).

Examples:

```yaml
# Layout is not specified, default Pot layout is used
product: jvm/app
```

```yaml
# Use Gradle Kotlin Multiplatform compatibility mode for the file layout 
product:
  type: lib
  platforms: [ android, jvm ]

pot:
  layout: gradle-kmp
```

### Dependencies and test dependencies

`dependecies:` section defines the list of modules and libraries necessary to build the Pot.
Certain dependencies can also be exported as part of the Pot API.
Read more about the [dependencies](Documentation.md#dependencies).

`test-dependenceis:` section defines the dependencies necessary to build and run tests of the Pot. Read more about
the [Pot tests](Documentation.md#tests).

Supported dependency types:

| Notation                                         | Description                                                                                                    |
|--------------------------------------------------|----------------------------------------------------------------------------------------------------------------|
| `- ./<relative path>`<br/>`- ../<relative path>` | [Dependency on another Pot](Documentation.md#internal-dependencies) in the codebase.                           |
| `- <group ID>:<artifact ID>:<version>`           | [Dependency on a Kotlin or  Java library](Documentation.md#external-maven-dependencies) in a Maven repository. |

Each dependency has the following attributes:

| Attribute           | Description                                                                                                                                             |
|---------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------|
| `exported: boolean` | Whether a dependency should be [visible as a par of a published API](Documentation.md#scopes-and-visibility). By default dependencies are not exported. |
| `scope: enum`       | [When in the build process](Documentation.md#scopes-and-visibility) should a dependency be used. Default is `all`                                       |

Available scopes:

| Scopes         | Description                                                                                                |
|----------------|------------------------------------------------------------------------------------------------------------|
| `all`          | The dependency is available during compilation and runtime.                                                |  
| `compile-only` | The dependency is only available during compilation. This is a 'provided' dependency in Maven terminology. |
| `runtime-only` | The dependency is not available during compilation, but available during testing and running               |

Examples:

```yaml
# Short form for the dependency attributes
dependencies:
  - io.ktor:ktor-client-core:2.2.0
  - ../common-types: exported
  - org.postgresql:postgresql:42.3.3: runtime-only
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

`repositories:` section defines the list of repositories used to look up and download the Pot dependencies.
Read more about the [dependency repositories](Documentation.md#managing-maven-repositories).

| Attribute             | Description                                         |
|-----------------------|-----------------------------------------------------|
| `id: string`          | The ID of the repository, used for to reference it. |
| `url: string`         | The url to the repository.                          |
| `credentials: object` | Credentials for the authenticated repositories.     |

Read more on the [repository configuration](Documentation.md#managing-maven-repositories)
Credentials support username/password authentication and have the following attributes:

| Attribute             | Description                                    |
|-----------------------|------------------------------------------------|
| `file: path`          | A relative path to a file with the credentials |
| `usernameKey: string` | A key in the file that holds the username.     |
| `passwordKey: string` | A key in the file that holds the password.     |

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

TODO
