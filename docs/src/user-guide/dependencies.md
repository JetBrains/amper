# Dependencies

Dependencies are pieces of code (e.g., libraries or other modules) that your module depends on.

## Declaring dependencies

Dependencies are declared in the `dependencies` list of the `module.yaml` file:

```yaml
dependencies:
  - ./my-other-module #(1)!
  - org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1 #(2)!
  - $libs.apache.commons.lang3 #(3)!
  - $kotlin.reflect #(4)!
```

1. Dependency on another module of the project (see [Module dependencies](#module-dependencies)).
2. Dependency on an external Maven library, with the provided coordinates (see [External Maven dependencies](#external-maven-dependencies)).
3. Dependency on a library from the project's [Library Catalog](#library-catalogs).
4. Dependency on a library from a built-in [Library Catalog](#library-catalogs)
   (in this case, the catalog brought by the Kotlin "toolchain").

### Module dependencies

To depend on another module of your project, use the path to that module, relative to the current module's root
directory. The path must start either with `./` or `../`.

For example, here the `app` module declares a dependency on the `nested-lib` and `ui/utils` modules:

<div class="grid" markdown>
<div>
```yaml hl_lines="4 5" title="app/module.yaml"
product: jvm/app

dependencies:
- ./nested-lib
- ../ui/utils
```

```yaml title="project.yaml"
modules:
  - app
  - app/nested-lib
  - ui/utils
```
</div>
<div>
``` title="Project structure"
root/
├─ app/
│  ├─ nested-lib/
│  │  ├─ src/
│  │  ╰─ module.yaml
│  ├─ src/
│  ╰─ module.yaml
├─ ui/
│  ╰─ utils/
│     ├─ src/
│     ╰─ module.yaml
╰─ project.yaml
‎ 
```
</div>
</div>

!!! note

    Dependencies between modules are only allowed within the project scope.
    That is, they must be listed in the `project.yaml` file and cannot be outside the project root directory.

### External Maven dependencies

Maven dependencies can be added via their coordinates[^1] using the usual `:`-separated notation:

```yaml
dependencies:
  - org.jetbrains.kotlin:kotlin-serialization:1.8.0
  - io.ktor:ktor-client-core:2.2.0
```

Maven dependencies are fetched from a Maven repository[^2] before being cached locally.
Default repositories are provided out of the box, so you don't have to configure anything.
If you need to customize the repositories, see [Managing Maven repositories](#managing-maven-repositories).

[^1]: If you're not familiar with Maven coordinates, check out Maven's 
[POM reference :fontawesome-solid-external-link:](https://maven.apache.org/pom.html#Maven_Coordinates).

[^2]: If you're not familiar with Maven repositories, check out Maven's
[Introduction to repositories :fontawesome-solid-external-link:](https://maven.apache.org/guides/introduction/introduction-to-repositories.html).

### Catalog dependencies

See [Library Catalogs](#library-catalogs).

### Transitivity and scope

#### Scope

The **_scope_** of a dependency defines whether it is available during compilation (_compile classpath_) and/or 
available at runtime (_runtime classpath_):

| Scope           |     Compilation      |      Runtime       |
|-----------------|:--------------------:|:------------------:|
| `all` (default) |  :white_check_mark:  | :white_check_mark: |
| `compile-only`  |  :white_check_mark:  |        :x:         |
| `runtime-only`  |         :x:          | :white_check_mark: |

By default, the scope is `all`. You can restrict a dependency's scope as follows:

=== "Short form"

    ```yaml
    dependencies:
      - io.ktor:ktor-client-core:2.2.0: compile-only  
      - ../ui/utils: runtime-only
    ```

=== "Long form"

    ```yaml
    dependencies:
      - io.ktor:ktor-client-core:2.2.0:
          scope: compile-only 
      - ../ui/utils:
          scope: runtime-only 
    ```

!!! note

    The long form is necessary when you also need to mark the dependency as `exported`:

    ```yaml
    dependencies:
      - io.ktor:ktor-client-core:2.2.0:
          exported: true
          scope: compile-only
    ```

#### Transitivity

By default, dependencies of your module are not added to the compilation of dependent modules.
In the following setup, `app` cannot directly use Ktor classes in its code:

```yaml title="lib/module.yaml"
dependencies:
  - io.ktor:ktor-client-core:2.2.0 #(1)! 
```

1. Regular dependency, not `exported`.

```yaml title="app/module.yaml"
dependencies:
  - ../lib #(1)! 
```

1. Brings the `ktor-client-core` dependency from the `lib` module at runtime, but doesn't expose it at compile time.

To make a dependency accessible to all dependent modules during their compilation, you need to explicitly mark it as 
`exported` (this is equivalent to declaring a dependency using the `api()` configuration in Gradle).

=== "Short form"

    ```yaml
    dependencies:
      - io.ktor:ktor-client-core:2.2.0: exported
      - ../ui/utils: exported
    ```

=== "Long form"

    ```yaml
    dependencies:
      - io.ktor:ktor-client-core:2.2.0:
          exported: true 
      - ../ui/utils:
          exported: true 
    ```

!!! note

    The long form is necessary when you also need to customize the scope

    ```yaml
    dependencies:
      - io.ktor:ktor-client-core:2.2.0:
          exported: true
          scope: compile-only
    ```


??? question "When should I use `exported`?"

    Ideally, as little as possible. The rule of thumb is that, if your module uses some types from the dependency in 
    its public API, you should mark it as `exported`. If not, you should probably avoid it.

    For example, if you depend on `ktor-client-core` in your module, and you have the following class:

    ```kotlin
    class MyApi(private val client: HttpClient) {
        // ...
    }
    ```

    The `HttpClient` type is used in your public constructor, so your consumers will need to see it at compile time.
    You should therefore mark `ktor-client-core` as `exported`.

??? info "`exported` is like a scope for transitive consumers"

    We can see `exported` as a way to modify the scope of a transitive dependency in the context of the consuming 
    module. For example, say `app` depends on `lib`, which depends on `ktor`. The `ktor` dependency is transitively 
    part of the dependencies of `app`.

    * If `lib` doesn't export `ktor`, the `ktor` dependency effectively has a `scope: runtime-only` in `app`
    * If `lib` marks `ktor` as `exported`, the `ktor` dependency effectively has a `scope: all` in `app`

## Library Catalogs

A library catalog associates keys to library coordinates (including the version), and allows adding the same libraries
as dependencies to multiple modules without having to repeat the coordinates or the versions of the libraries.

Amper currently supports the following library catalogs:

- one project catalog (user-defined)
- several toolchain catalogs (a.k.a built-in catalogs, such as Kotlin or Compose Multiplatform)

### Project catalog

The **project catalog** is the user-defined catalog for the project.

It is defined in a file named `libs.versions.toml`, and is written in the TOML format[^3] of
[Gradle version catalogs](https://docs.gradle.org/current/userguide/version_catalogs.html#sec:version-catalog-declaration).
It can be located at the root of the project or at `gradle/libs.versions.toml` (the default from Gradle, to ease 
migration).

[^3]: Only `[versions]` and `[libraries]` sections are supported from the Gradle format, not `[bundles]` and `[plugins]`.

!!! info "You can only have one project catalog"

    You have to choose between `libs.versions.toml` and `gradle/libs.versions.toml`", but cannot use both at the same 
    time.

To use dependencies from the project catalog, use the syntax `$libs.<key>` instead of the coordinates, where `$libs` is
the catalog name of the project catalog, and `<key>` is defined according to the
[Gradle name mapping rules](https://docs.gradle.org/current/userguide/version_catalogs.html#sec:mapping-aliases-to-accessors).

Example:

```toml title="libs.versions.toml"
[versions]
ktor = "3.3.2"

[libraries]
ktor-client-auth = { module = "io.ktor:ktor-client-auth", version.ref = "ktor" }
ktor-client-cio = { module = "io.ktor:ktor-client-cio", version.ref = "ktor" }
ktor-client-contentNegotiation = { module = "io.ktor:ktor-client-content-negotiation", version.ref = "ktor" }
```

```yaml title="app/module.yaml"
dependencies:
  - $libs.ktor.client.auth
  - $libs.ktor.client.cio
  - $libs.ktor.client.contentNegotiation
```

### Toolchain catalogs (built-in)

The **toolchain catalogs** are implicitly defined, and contain predefined libraries that relate to the corresponding 
toolchain. The name of such a catalog corresponds to the name of the toolchain in the 
[settings section](basics.md#settings). All dependencies in such catalogs usually have the same version, which is the
toolchain version.

For example, dependencies for the Compose Multiplatform framework are accessible using the `$compose` catalog name, and
take their versions from the `settings.compose.version` setting.

To use dependencies from toolchain catalogs, use the syntax `$<catalog-name>.<key>` instead of the coordinates, for
example:
```yaml
dependencies:
  - $kotlin.reflect      # dependency from the Kotlin catalog
  - $compose.material3   # dependency from the Compose Multiplatform catalog
```

Catalog dependencies can still have a [scope and visibility](#transitivity-and-scope) even when coming from a catalog:

```yaml
dependencies:
  - $compose.foundation: exported
  - $my-catalog.db-engine: runtime-only 
```

## Managing Maven repositories

By default, Maven Central and Google Android repositories are pre-configured. To add extra repositories, use the following options:

```yaml title="module.yaml"
repositories:
  - https://repo.spring.io/ui/native/release
  - url: https://dl.google.com/dl/android/maven2/
  - id: jitpack
    url: https://jitpack.io
```

For private repositories, you can configure credentials this way:

<div class="grid" markdown>
```yaml title="module.yaml"
repositories:
  - url: https://my.private.repository/
    credentials:
      file: ../local.properties # relative path to the file with credentials
      usernameKey: my.private.repository.username
      passwordKey: my.private.repository.password
```

```properties title="local.properties"
my.private.repository.username=...
my.private.repository.password=...
```
</div>

!!! note

    Currently only `*.properties` files with credentials are supported.

## Using a Maven BOM

To import a BOM, specify its coordinates prefixed by `bom: `

```yaml
dependencies:
  - bom: io.ktor:ktor-bom:2.2.0
  - io.ktor:ktor-client-core 
```

The effects are the following:

* Maven dependencies that are listed in the BOM no longer need a version (e.g., `io.ktor:ktor-client-core`).
  The version from the BOM is used in this case.
* Dependency versions declared in the BOM participate in version conflict resolution.

!!! tip "This also applies to catalog dependencies!"

    If a dependency in the [library catalog](#library-catalogs) is only used in modules that 
    declare a BOM that provides a version for it, then the version can be omitted there:

    <div class="grid" markdown>
    ```yaml title="libs.versions.toml"
    [libraries]
    ktor-client-core = { module = "io.ktor:ktor-client-core" }
    ```
    ```yaml title="module.yaml"
    dependencies:
      - bom: io.ktor:ktor-bom:3.2.0
      - $libs.ktor.client.core
    ```
    </div>